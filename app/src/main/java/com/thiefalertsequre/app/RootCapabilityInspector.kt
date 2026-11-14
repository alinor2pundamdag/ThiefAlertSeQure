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
        SU_NOT_AVAILABLE,
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
            indicators.isNotEmpty() || status == RootStatus.ROOT_INDICATORS_FOUND || status == RootStatus.ROOT_AUTHORIZED
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
                status = RootStatus.SU_NOT_AVAILABLE,
                detail = if (passive.mayBeRooted) "The su command could not be started, but passive root indicators exist. Root may be hidden or inaccessible." else "No usable su command was found. No root access is available to this app through the standard su interface; concealed root cannot be ruled out."
            )
        }

        return try {
            if (!waitForProcess(process, timeoutSeconds)) {
                terminateProcess(process)
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
                        detail = "ROOT ACCESS GRANTED: this app received uid=0 for this explicit diagnostic check. Only the separately approved read-only diagnostics may use this authorization."
                    )
                } else {
                    passive.copy(
                        status = RootStatus.ROOT_NOT_AUTHORIZED,
                        detail = "The su command ran but did not return uid=0. Superuser authorization was denied, unavailable, or not granted. This does not prove the operating system has no root modifications."
                    )
                }
            }
        } catch (e: Exception) {
            terminateProcess(process)
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
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < 24000) output.append(line).append('\n')
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        return try {
            if (!waitForProcess(process, timeoutSeconds)) {
                terminateProcess(process)
                reader.join(500L)
                "Root Bluetooth diagnostics timed out. No system changes were made."
            } else {
                reader.join(1000L)
                val text = synchronized(output) { output.toString().take(24000) }
                if (process.exitValue() == 0 || text.isNotBlank()) text
                else "Diagnostics returned no readable Bluetooth system information."
            }
        } catch (e: Exception) {
            terminateProcess(process)
            "Diagnostics failed: ${e.javaClass.simpleName}"
        }
    }
    private fun waitForProcess(process: Process, timeoutSeconds: Long): Boolean {
        if (Build.VERSION.SDK_INT >= 26) return process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val waiter = Thread { runCatching { process.waitFor() } }.apply { isDaemon = true; start() }
        waiter.join(TimeUnit.SECONDS.toMillis(timeoutSeconds))
        if (waiter.isAlive) {
            terminateProcess(process)
            waiter.join(500L)
            return false
        }
        return true
    }

    private fun terminateProcess(process: Process) {
        if (Build.VERSION.SDK_INT >= 26) runCatching { process.destroyForcibly() }
        else runCatching { process.destroy() }
    }

}
