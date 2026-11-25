package com.thiefalertsequre.app

import android.os.Build
import java.io.File
import java.io.IOException
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

    private data class CommandResult(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean
    )

    private const val ROOT_COMMAND_TIMEOUT_SECONDS = 8L
    private const val MAX_DIAGNOSTIC_OUTPUT_CHARS = 24_000
    private const val PROCESS_TERMINATE_WAIT_MS = 250L
    private const val PROCESS_FORCE_WAIT_MS = 500L
    private const val STREAM_READER_JOIN_MS = 500L

    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
        "/system/bin/.ext/.su", "/system/usr/we-need-root/su",
        "/data/local/bin/su", "/data/local/xbin/su", "/data/adb/magisk",
        "/sbin/.magisk", "/debug_ramdisk/.magisk", "/vendor/bin/su", "/su/bin/su"
    )

    private val suExecutablePaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
        "/system/bin/.ext/.su", "/system/usr/we-need-root/su",
        "/data/local/bin/su", "/data/local/xbin/su", "/vendor/bin/su", "/su/bin/su"
    )

    private fun findSuExecutable(): String? = suExecutablePaths.firstNotNullOfOrNull { path ->
        runCatching {
            val file = File(path)
            if (RootExecutablePathPolicy.isSafeAbsoluteExecutable(path) && file.isFile && file.canExecute()) {
                file.canonicalPath.takeIf(RootExecutablePathPolicy::isSafeAbsoluteExecutable)
            } else null
        }.getOrNull()
    }

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
    fun verifyRootAuthorization(timeoutSeconds: Long = ROOT_COMMAND_TIMEOUT_SECONDS): Report {
        val passive = inspectPassive()
        val suExecutable = findSuExecutable() ?: return passive.copy(
            status = RootStatus.SU_NOT_AVAILABLE,
            detail = if (passive.mayBeRooted) "Root indicators exist, but no executable su binary was found at a verified absolute path." else "No executable su binary was found at a verified absolute path."
        )

        val result = try {
            runReadOnlySuCommand(suExecutable, "id", timeoutSeconds)
        } catch (error: Exception) {
            return passive.copy(
                status = RootStatus.ROOT_CHECK_ERROR,
                detail = "Root authorization could not be verified: ${error.javaClass.simpleName}. No system changes were made."
            )
        }

        if (result.timedOut) {
            return passive.copy(
                status = RootStatus.ROOT_CHECK_TIMED_OUT,
                detail = "The superuser request timed out or was not answered. The child process was terminated; no system changes were made."
            )
        }

        return if (result.exitCode == 0 && result.output.contains("uid=0")) {
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

    /**
     * Read-only root diagnostics. Call only after ROOT_AUTHORIZED.
     * Never changes controller configuration, power, pairing, or system properties.
     */
    fun collectBluetoothDiagnostics(timeoutSeconds: Long = ROOT_COMMAND_TIMEOUT_SECONDS): String {
        val suExecutable = findSuExecutable()
            ?: return "No executable su binary was found at a verified absolute path."
        val command = "dumpsys bluetooth_manager; echo '\\n--- Bluetooth system properties ---'; getprop | grep -i bluetooth; echo '\\n--- Bluetooth sysfs devices ---'; ls -la /sys/class/bluetooth 2>/dev/null"
        val result = try {
            runReadOnlySuCommand(suExecutable, command, timeoutSeconds, MAX_DIAGNOSTIC_OUTPUT_CHARS)
        } catch (error: Exception) {
            return "Diagnostics failed safely: ${error.javaClass.simpleName}. No system changes were made."
        }

        if (result.timedOut) {
            return "Root Bluetooth diagnostics timed out. The child process was terminated; no system changes were made."
        }
        return if (result.exitCode == 0 || result.output.isNotBlank()) {
            result.output.ifBlank { "Diagnostics returned no readable Bluetooth system information." }
        } else {
            "Diagnostics returned no readable Bluetooth system information."
        }
    }

    /**
     * Drain stdout concurrently so a full pipe cannot block process exit.
     * Output is bounded in memory, while excess bytes are still drained.
     * Both normal completion and timeout close streams and reap the child.
     */
    private fun runReadOnlySuCommand(
        suExecutable: String,
        command: String,
        timeoutSeconds: Long,
        maxOutputChars: Int = 4_096
    ): CommandResult {
        val process = ProcessBuilder(suExecutable, "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val outputLock = Any()
        val reader = Thread({
            try {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(2_048)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(outputLock) {
                            val remaining = maxOutputChars - output.length
                            if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                        }
                    }
                }
            } catch (_: IOException) {
                // Stream closure is expected when a timed-out process is terminated.
            }
        }, "thiefalert-su-output-reader").apply {
            isDaemon = true
            start()
        }

        var timedOut = false
        try {
            val boundedTimeout = timeoutSeconds.coerceIn(1L, 30L)
            if (!process.waitFor(boundedTimeout, TimeUnit.SECONDS)) {
                timedOut = true
                terminateProcess(process)
            }
        } catch (interrupted: InterruptedException) {
            terminateProcess(process)
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            if (process.isAlive) terminateProcess(process)
            runCatching { process.outputStream.close() }
            if (reader.isAlive) {
                runCatching { reader.join(STREAM_READER_JOIN_MS) }
            }
            if (reader.isAlive) {
                runCatching { process.inputStream.close() }
                runCatching { reader.join(PROCESS_FORCE_WAIT_MS) }
            }
            // Ensure the process is not left behind if the caller is destroyed/interrupted.
            if (process.isAlive) terminateProcess(process)
            runCatching { process.errorStream.close() }
        }

        val exitCode = if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull()
        val capturedOutput = synchronized(outputLock) { output.toString() }
        return CommandResult(exitCode, capturedOutput, timedOut)
    }

    private fun terminateProcess(process: Process) {
        if (!process.isAlive) return
        runCatching { process.destroy() }
        val exitedGracefully = runCatching {
            process.waitFor(PROCESS_TERMINATE_WAIT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!exitedGracefully && process.isAlive) {
            runCatching { process.destroyForcibly() }
            runCatching { process.waitFor(PROCESS_FORCE_WAIT_MS, TimeUnit.MILLISECONDS) }
        }
    }
}
