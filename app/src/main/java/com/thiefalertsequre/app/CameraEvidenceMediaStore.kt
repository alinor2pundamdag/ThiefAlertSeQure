package com.thiefalertsequre.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Publishes owner-consented photos into the app's dedicated Gallery folder. */
internal object CameraEvidenceMediaStore {
    private const val DIRECTORY_NAME = "ThiefAlertSeQure"
    private val OWNED_IMAGE_NAME = Regex("^ThiefAlertSeQure-[0-9]{10,13}-[0-9a-f]{8}\\.jpg$")

    data class MigrationResult(
        val enumerationSucceeded: Boolean,
        val copiedToVault: Int = 0,
        val originalsRemoved: Int = 0,
        val copyFailures: Int = 0,
        val removalFailures: Int = 0,
        val capacityLimited: Int = 0,
        val vaultIntegrityVerified: Boolean = false,
        val blockedByProtection: Boolean = false,
        val blockedByEncryptionSetting: Boolean = false
    )

    @Synchronized
    fun save(context: Context, jpeg: ByteArray): Uri? {
        if (!CameraEvidencePolicy.isValidImageSize(jpeg.size)) return null
        val resolver = context.applicationContext.contentResolver
        val collection = imageCollectionUri()
        val now = System.currentTimeMillis()
        val fileName = "ThiefAlertSeQure-$now-${UUID.randomUUID().toString().take(8)}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIRECTORY_NAME)
                if (!directory.exists() && !directory.mkdirs()) return null
                put(MediaStore.Images.Media.DATA, File(directory, fileName).absolutePath)
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri, "w")?.use { it.write(jpeg); it.flush() }
                ?: throw IllegalStateException("Gallery output stream unavailable")
            if (Build.VERSION.SDK_INT >= 29) {
                val published = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                if (published <= 0) throw IllegalStateException("Gallery image could not be published")
            }
            true
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        runCatching { prune(context.applicationContext, now) }
        return uri
    }

    @Synchronized
    fun enforceRetention(context: Context) {
        prune(context.applicationContext, System.currentTimeMillis())
    }

    /**
     * Copies app-owned Gallery photos into the encrypted vault, verifies every
     * ciphertext by reading it back, then removes the original only if the
     * source bytes are still identical and protection remains DISARMED.
     */
    @Synchronized
    fun migrateAllToEncryptedVault(context: Context): MigrationResult {
        val app = context.applicationContext
        if (ProtectionStore(app).read().mode != ProtectionMode.DISARMED) {
            return MigrationResult(true, blockedByProtection = true)
        }
        if (!CameraEvidenceCapture.isEncryptionEnabled(app)) {
            return MigrationResult(true, blockedByEncryptionSetting = true)
        }
        enforceRetention(app)
        val query = queryOwnedImages(app)
        if (!query.success) return MigrationResult(false)
        if (!CameraEvidenceVault.verifyIntegrity(app)) {
            return MigrationResult(true, copyFailures = query.images.size, vaultIntegrityVerified = false)
        }
        if (query.images.isEmpty()) {
            return MigrationResult(true, vaultIntegrityVerified = true)
        }

        val existingCount = CameraEvidenceVault.entries(app).size
        val capacity = (CameraEvidencePolicy.MAX_IMAGES - existingCount).coerceAtLeast(0)
        val selected = query.images
            .sortedByDescending { it.second }
            .take(capacity)
            .sortedBy { it.second }
        var copied = 0
        var removed = 0
        var copyFailures = 0
        var removalFailures = 0
        var blocked = false

        for ((uri, timestamp) in selected) {
            val original = readBoundedImage(app, uri)
            if (original == null || !CameraEvidencePolicy.isValidImageSize(original.size)) {
                copyFailures += 1
                continue
            }
            val entry = CameraEvidenceVault.save(app, original, timestamp)
            if (entry == null) {
                copyFailures += 1
                continue
            }
            val verified = CameraEvidenceVault.read(app, entry.id)
            if (verified == null || !MessageDigest.isEqual(original, verified)) {
                runCatching { CameraEvidenceVault.delete(app, entry.id) }
                copyFailures += 1
                continue
            }
            copied += 1

            // Re-read the Gallery row immediately before deletion. If its contents
            // changed after the encrypted copy was made, preserve the source row.
            val currentSource = readBoundedImage(app, uri)
            if (currentSource == null || !MessageDigest.isEqual(original, currentSource)) {
                removalFailures += 1
                continue
            }
            val deleteResult = ProtectionStore(app).runIfDisarmed {
                runCatching { app.contentResolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            }
            when (deleteResult) {
                true -> removed += 1
                false -> removalFailures += 1
                null -> {
                    blocked = true
                    break
                }
            }
        }

        val vaultHealthy = CameraEvidenceVault.verifyIntegrity(app)
        return MigrationResult(
            enumerationSucceeded = true,
            copiedToVault = copied,
            originalsRemoved = removed,
            copyFailures = copyFailures,
            removalFailures = removalFailures,
            capacityLimited = (query.images.size - selected.size).coerceAtLeast(0),
            vaultIntegrityVerified = vaultHealthy,
            blockedByProtection = blocked
        )
    }

    private fun readBoundedImage(context: Context, uri: Uri): ByteArray? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= CameraEvidencePolicy.MAX_IMAGE_BYTES) {
                    "Gallery image exceeds the encrypted evidence size limit"
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }.getOrNull()


    @Synchronized
    fun count(context: Context): Int {
        enforceRetention(context)
        val query = queryOwnedImages(context.applicationContext)
        return if (query.success) query.images.size else -1
    }

    @Synchronized
    fun deleteAll(context: Context): Boolean {
        val resolver = context.applicationContext.contentResolver
        val query = queryOwnedImages(context.applicationContext)
        if (!query.success) return false
        var allDeleted = true
        query.images.forEach { (uri, _) ->
            val deleted = runCatching { resolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            if (!deleted) allDeleted = false
        }
        return allDeleted
    }

    @Synchronized
    private fun prune(context: Context, now: Long) {
        val resolver = context.contentResolver
        val query = queryOwnedImages(context)
        if (!query.success) return
        query.images.sortedByDescending { it.second }.forEachIndexed { index, (uri, timestamp) ->
            if (!CameraEvidencePolicy.isRetained(timestamp, now) || index >= CameraEvidencePolicy.MAX_IMAGES) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun imageCollectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private data class QueryResult(
        val success: Boolean,
        val images: List<Pair<Uri, Long>>
    )

    private fun queryOwnedImages(context: Context): QueryResult {
        val collection = imageCollectionUri()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DISPLAY_NAME)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "(${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?) AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(
                "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME",
                "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME/",
                "ThiefAlertSeQure-%"
            )
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIRECTORY_NAME)
            selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(directory.absolutePath + "/%", "ThiefAlertSeQure-%")
        }
        return try {
            val cursor = context.contentResolver.query(
                collection, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            ) ?: return QueryResult(false, emptyList())
            val images = mutableListOf<Pair<Uri, Long>>()
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val takenCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val displayName = it.getString(nameCol).orEmpty()
                    // Never treat arbitrary files placed in the app folder as app-owned evidence.
                    if (OWNED_IMAGE_NAME.matches(displayName)) {
                        val id = it.getLong(idCol)
                        val taken = it.getLong(takenCol)
                        val added = it.getLong(addedCol)
                        val timestamp = if (taken > 0L) taken else added * 1_000L
                        val uri = collection.buildUpon().appendPath(id.toString()).build()
                        images += uri to timestamp
                    }
                }
            }
            QueryResult(true, images)
        } catch (error: Exception) {
            android.util.Log.e("CameraEvidenceMediaStore", "Could not enumerate app-owned Gallery evidence", error)
            QueryResult(false, emptyList())
        }
    }
}
