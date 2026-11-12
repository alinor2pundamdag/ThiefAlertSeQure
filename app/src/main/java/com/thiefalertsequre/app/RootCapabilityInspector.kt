package com.thiefalertsequre.app

import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root detection is evidence-based and deliberately non-invasive.
 * Passive inspection never launches a superuser prompt.
 * Root authorization is tested only after an explicit owner action.
 */
object RootCapabilityInspector {
    enum class RootStatus {
        NOT_INDICATED,
        ROOT_INDICATORS_FOUND,
        ROOT_AUTHORIZED,
        ROOT_NOT_AUTHORIZED,
        ROOT_CHECK_TIMED_OUT,
        ROOT_CHECK_ERROR
    }

    data class Report(
        val status: RootStatus,
        val indicators: List<String>,
        val buildTags: String,
        val detail: String
    ) {
        val isRootAuthorized: Boolean get() = status == RootStatus.ROOT_AUTHORIZED
        val mayBeRooted: Boolean get() =
            status == RootStatus.ROOT_INDICATORS_FOUND || status == RootStatus.ROOT_AUTHORIZED
    }

    private val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/data/adb/magisk",
        "/sbin/.magisk",
        "/debug_ramdisk/.magisk"
    )

    fun inspectPassive(): Report {
        val indicators = buildList {
            suPaths.forEach { path ->
                if (runCatching { File(path).exists() }.getOrDefault(false)) add("Root-related path exists: $path")
            }
            if (Build.TAGS?.contains("test-keys", ignoreCase = true) == true) {
                add("Build tags contain test-keys (not proof of root by itself)")
            }
            if (Build.TYPE.equals("userdebug", true) || Build.TYPE.equals("eng", true)) {
                add("Build type is ${Build.TYPE} (development build; not proof of root by itself)")
            }
        }.distinct()

        return Report(
            status = if (indicators.isEmpty()) RootStatus.NOT_INDICATED else RootStatus.ROOT_INDICATORS_FOUND,
            indicators = indicators,
            buildTags = Build.TAGS ?: "unknown",
            detail = if (indicators.isEmpty()) {
                "No common root indicators found. Root may still be concealed; this is not a guarantee that the device is unrooted."
            } else {
                "Root-related indicators found. They are evidence, not proof that this app currently has superuser authorization."
            }
        )
    }

    /**
     * Run only from a direct owner tap after explaining that the installed
     * superuser manager may display an authorization prompt.
     * The only command is read-only: id.
     */
    fun verifyRootAuthorization(timeoutSeconds: Long = 8L): Report {
        val passive = inspectPassive()
        val process = try {
            ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        } catch (e: Exception) {
            return passive.copy(
                status = if (passive.mayBeRooted) RootStatus.ROOT_NOT_AUTHORIZED else RootStatus.NOT_INDICATED,
                detail = "The su command could not be started. Root authorization is not available to this app."
            )
        }

        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                passive.copy(
                    status = RootStatus.ROOT_CHECK_TIMED_OUT,
                    detail = "The superuser request timed out or was not answered. No system changes were made."
                )
            } else {
                val output = runCatching { process.inputStream.bufferedReader().use { it.readText().trim() } }
                    .getOrDefault("")
                if (process.exitValue() == 0 && output.contains("uid=0")) {
                    passive.copy(
                        status = RootStatus.ROOT_AUTHORIZED,
                        detail = "Superuser authorization verified for this explicit diagnostic check. Only read-only Bluetooth diagnostics are enabled."
                    )
                } else {
                    passive.copy(
                        status = RootStatus.ROOT_NOT_AUTHORIZED,
                        detail = "Root indicators may exist, but superuser authorization was denied or unavailable."
                    )
                }
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            passive.copy(
                status = RootStatus.ROOT_CHECK_ERROR,
                detail = "Root authorization could not be verified: ${e.javaClass.simpleName}. No system changes were made."
            )
        }
    }

    /**
     * Read-only root diagnostics. Call only after ROOT_AUTHORIZED.
     * Never changes controller configuration, power, pairing, or system properties.
     */
    fun collectBluetoothDiagnostics(timeoutSeconds: Long = 8L): String {
        val process = ProcessBuilder(
            "su", "-c",
            "dumpsys bluetooth_manager; echo '\\n--- Bluetooth system properties ---'; getprop | grep -i bluetooth; echo '\\n--- Bluetooth sysfs devices ---'; ls -la /sys/class/bluetooth 2>/dev/null"
        ).redirectErrorStream(true).start()
        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "Root Bluetooth diagnostics timed out. No system changes were made."
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }.take(24000)
                if (process.exitValue() == 0 || output.isNotBlank()) output
                else "Diagnostics returned no readable Bluetooth system information."
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            "Diagnostics failed: ${e.javaClass.simpleName}"
        }
    }
}
