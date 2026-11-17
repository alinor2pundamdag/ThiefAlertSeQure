package com.thiefalertsequre.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler

/** Five Android-generated alarm tones; no extra audio files or storage permissions are required. */
enum class AlarmTonePreset(
    val label: String,
    internal val toneType: Int,
    internal val durationMs: Int,
    internal val gapMs: Long
) {
    DEFAULT_AUDIO("Packaged alarm", ToneGenerator.TONE_PROP_BEEP, 0, 0),
    SIREN("Siren", ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 850, 180),
    PULSE("Pulse", ToneGenerator.TONE_PROP_BEEP, 300, 220),
    DOUBLE("Double alert", ToneGenerator.TONE_PROP_ACK, 180, 120),
    URGENT("Urgent", ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 240, 90),
    CONTINUOUS("Continuous alert", ToneGenerator.TONE_SUP_RINGTONE, 1_000, 80);

    companion object {
        val builtIns = listOf(SIREN, PULSE, DOUBLE, URGENT, CONTINUOUS)
        fun fromStored(value: String?): AlarmTonePreset =
            entries.firstOrNull { it.name == value } ?: DEFAULT_AUDIO
    }
}

/** Repeats a system-generated tone on the alarm stream. Failure falls back to packaged audio. */
internal class AlarmToneController(
    private val handler: Handler,
    private val preset: AlarmTonePreset,
    private val onFailure: (Throwable) -> Unit
) {
    private var generator: ToneGenerator? = null
    private var running = false

    private val repeat = object : Runnable {
        override fun run() {
            if (!running) return
            val active = generator ?: return
            try {
                if (!active.startTone(preset.toneType, preset.durationMs)) {
                    throw IllegalStateException("Android ToneGenerator could not start the selected alarm tone.")
                }
                handler.postDelayed(this, preset.durationMs + preset.gapMs)
            } catch (error: Throwable) {
                stop()
                onFailure(error)
            }
        }
    }

    fun start(): Boolean {
        if (running) return true
        return try {
            generator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            running = true
            repeat.run()
            running
        } catch (error: Throwable) {
            stop()
            onFailure(error)
            false
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(repeat)
        runCatching { generator?.stopTone() }
        runCatching { generator?.release() }
        generator = null
    }
}
