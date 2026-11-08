package com.thiefalertsequre.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler

/**
 * Pulses the device LED through CameraManager's torch API. This does not open
 * the camera and does not capture images or video.
 *
 * Torch access is best-effort: camera contention, OEM policy, thermal limits,
 * or missing hardware may prevent light output. Audio alarm operation is
 * deliberately independent from this controller.
 */
internal class AlarmFlashController(
    context: Context,
    private val handler: Handler,
    private val onEvent: (String, String) -> Unit
) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var cameraId: String? = null
    private var running = false
    private var torchOn = false

    private val pulse = object : Runnable {
        override fun run() {
            if (!running) return
            val id = cameraId ?: run {
                stop()
                return
            }
            val nextState = !torchOn
            try {
                cameraManager.setTorchMode(id, nextState)
                torchOn = nextState
                handler.postDelayed(
                    this,
                    if (nextState) AlarmFlashSettings.ON_DURATION_MS else AlarmFlashSettings.OFF_DURATION_MS
                )
            } catch (error: Exception) {
                report("alarm_flash_interrupted", error.javaClass.simpleName + ": " + error.message)
                stop()
            }
        }
    }

    fun start(): Boolean {
        if (running) return true
        val selectedCamera = findFlashCameraId()
        if (selectedCamera == null) {
            report("alarm_flash_unavailable", "No available camera flash unit was found; audio alarm remains active")
            return false
        }
        cameraId = selectedCamera
        return try {
            cameraManager.setTorchMode(selectedCamera, true)
            torchOn = true
            running = true
            handler.postDelayed(pulse, AlarmFlashSettings.ON_DURATION_MS)
            report("alarm_flash_started", "Camera LED pulsing while the theft alarm is latched")
            true
        } catch (error: Exception) {
            report("alarm_flash_start_failed", error.javaClass.simpleName + ": " + error.message)
            stop()
            false
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(pulse)
        val id = cameraId
        if (id != null && torchOn) {
            runCatching { cameraManager.setTorchMode(id, false) }
                .onFailure { report("alarm_flash_stop_failed", it.javaClass.simpleName + ": " + it.message) }
        }
        val hadState = cameraId != null || torchOn
        torchOn = false
        cameraId = null
        if (hadState) report("alarm_flash_stopped", "Camera LED switched off because the alarm output stopped")
    }

    private fun findFlashCameraId(): String? = runCatching {
        val candidates = cameraManager.cameraIdList.mapNotNull { id ->
            val characteristics = runCatching {
                cameraManager.getCameraCharacteristics(id)
            }.getOrNull() ?: return@mapNotNull null
            if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                id to characteristics.get(CameraCharacteristics.LENS_FACING)
            } else null
        }
        candidates.firstOrNull { it.second == CameraCharacteristics.LENS_FACING_BACK }?.first
            ?: candidates.firstOrNull()?.first
    }.getOrNull()

    private fun report(event: String, detail: String) {
        runCatching { onEvent(event, detail) }
    }
}

/** User preference and conservative pulse timing shared by UI and alarm service. */
internal object AlarmFlashSettings {
    const val PREFS_NAME = "alarm_flash_settings_v1"
    const val KEY_ENABLED = "enabled"
    const val ON_DURATION_MS = 700L
    const val OFF_DURATION_MS = 700L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun shouldRun(mode: ProtectionMode): Boolean = mode == ProtectionMode.ALARM_TRIGGERED
}
