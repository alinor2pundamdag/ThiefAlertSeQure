package com.thiefalertsequre.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import org.json.JSONObject

sealed class AppUpdateResult {
    data class Ready(
        val apkFile: File,
        val versionName: String,
        val versionCode: Long,
        val releaseNotes: String
    ) : AppUpdateResult()

    object UpToDate : AppUpdateResult()
    data class Failed(val reason: String) : AppUpdateResult()
}

/**
 * Explicit, owner-initiated updater for official GitHub Releases.
 *
 * It never runs automatically, uploads no device data, and never bypasses
 * Android's Package Installer confirmation. Both the installed app and the
 * downloaded APK must use the permanent official release certificate.
 */
object AppUpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/alinor2pundamdag/ThiefAlertSeQure/releases/latest"
    private const val EXPECTED_PACKAGE = "com.thiefalertsequre.app"
    private const val EXPECTED_CERT_SHA256 =
        "9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private val allowedHosts = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    fun checkAndDownload(
        context: Context,
        executor: ExecutorService,
        callback: (AppUpdateResult) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { resolveLatestUpdate(appContext) }
                .getOrElse { AppUpdateResult.Failed(it.message ?: "Update check failed.") }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }

    private fun resolveLatestUpdate(context: Context): AppUpdateResult {
        require(installedSignerMatchesOfficialKey(context)) {
            "This installation does not use the official release signing key. A one-time migration is required before in-app updates can work."
        }

        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedVersionName = installed.versionName ?: "0"
        val installedVersionCode = versionCode(installed)
        val release = JSONObject(readHttpsText(RELEASE_API))
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
            "The latest GitHub release is not a published stable release."
        }

        val tag = release.optString("tag_name")
        require(tag.matches(Regex("^v[0-9]+(\\.[0-9]+){1,3}$"))) {
            "The latest release tag has an invalid version format."
        }
        val remoteVersionName = tag.removePrefix("v")
        if (AppVersionComparator.compare(remoteVersionName, installedVersionName) <= 0) {
            return AppUpdateResult.UpToDate
        }

        val assets = release.optJSONArray("assets")
            ?: error("The GitHub release has no downloadable assets.")
        var apkName: String? = null
        var apkUrl: String? = null
        var apkSize = 0L
        var checksumUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            when {
                name == "ThiefAlertSeQure-v$remoteVersionName-release.apk" -> {
                    apkName = name
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                }
                name == "ThiefAlertSeQure-v$remoteVersionName-release.apk.sha256" -> {
                    checksumUrl = asset.optString("browser_download_url")
                }
            }
        }
        require(!apkName.isNullOrBlank() && !apkUrl.isNullOrBlank()) {
            "The official release APK is missing. A debug APK is never accepted for updates."
        }
        require(!checksumUrl.isNullOrBlank()) {
            "The official release SHA-256 checksum is missing."
        }
        require(apkSize in 1..MAX_APK_BYTES) { "The release APK size is invalid or exceeds 100 MB." }

        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDirectory, apkName!!)
        val temporaryFile = File(updateDirectory, "$apkName.download")
        temporaryFile.delete()
        try {
            downloadHttps(apkUrl!!, temporaryFile, apkSize)
            val checksumText = readHttpsText(checksumUrl!!)
            val expectedHash = checksumText.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                .lowercase()
            require(expectedHash.matches(Regex("^[a-f0-9]{64}$"))) {
                "The release checksum file is malformed."
            }
            val actualHash = sha256(temporaryFile)
            require(actualHash == expectedHash) { "The downloaded APK failed SHA-256 verification." }
            verifyApk(context.packageManager, temporaryFile, remoteVersionName, installedVersionCode)
            if (apkFile.exists()) apkFile.delete()
            check(temporaryFile.renameTo(apkFile)) { "Could not prepare the verified APK for installation." }
            return AppUpdateResult.Ready(
                apkFile = apkFile,
                versionName = remoteVersionName,
                versionCode = readApkVersionCode(context, apkFile),
                releaseNotes = release.optString("body").trim().take(1200)
            )
        } finally {
            temporaryFile.delete()
        }
    }

    private fun readHttpsText(address: String): String {
        val connection = openHttps(address)
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadHttps(address: String, destination: File, expectedSize: Long) {
        val connection = openHttps(address)
        try {
            val contentLength = connection.contentLengthLong
            require(contentLength <= 0L || contentLength == expectedSize) {
                "The release APK size does not match GitHub release metadata."
            }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "The downloaded APK exceeds the 100 MB safety limit." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    require(total == expectedSize) { "The downloaded APK is incomplete." }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(address: String): HttpURLConnection {
        var currentUrl = URL(address)
        repeat(6) {
            require(
                currentUrl.protocol.equals("https", ignoreCase = true) &&
                    currentUrl.host in allowedHosts
            ) { "The update server or redirect is not an approved HTTPS GitHub host." }

            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ThiefAlertSeQure-Android-Updater")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) return connection
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank()) { "GitHub returned a redirect without a destination." }
                currentUrl = URL(currentUrl, location)
            } else {
                connection.disconnect()
                error("GitHub update request failed (HTTP $status).")
            }
        }
        error("GitHub returned too many update redirects.")
    }

    private fun verifyApk(packageManager: PackageManager, file: File, expectedVersionName: String, installedVersionCode: Long) {
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
            ?: error("Android could not inspect the downloaded APK.")
        require(archive.packageName == EXPECTED_PACKAGE) {
            "The downloaded APK has the wrong application ID."
        }
        require(archive.versionName == expectedVersionName) {
            "The APK version does not match its GitHub release tag."
        }
        require(versionCode(archive) > installedVersionCode) {
            "The APK version code is not newer than the installed version."
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            archive.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            archive.signatures
        }
        require(!signatures.isNullOrEmpty()) { "The APK signing certificate could not be read." }
        val signerDigests = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        require(signerDigests.size == 1 && signerDigests.single() == EXPECTED_CERT_SHA256) {
            "The APK is not signed with the pinned official ThiefAlertSeQure release key."
        }
    }

    private fun installedSignerMatchesOfficialKey(context: Context): Boolean {
        val packageManager = context.packageManager
        val installed = packageManager.getPackageInfo(context.packageName, signingFlags())
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            installed.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            installed.signatures
        }
        if (signatures.isNullOrEmpty()) return false
        val digests = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        return digests.size == 1 && digests.single() == EXPECTED_CERT_SHA256
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun readApkVersionCode(context: Context, file: File): Long {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: error("Android could not read the downloaded APK version.")
        return versionCode(info)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

}
