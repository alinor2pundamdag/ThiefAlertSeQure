package com.thiefalertsequre.app

import android.content.Context
import android.media.AudioManager

/**
 * Temporarily raises the alarm stream while the alarm is active and restores
 * the user's prior alarm volume after a verified disarm.
 */
object AlarmVolumeController {
    private const val PREFS = "alarm_volume_restore_v1"
    private const val KEY_ORIGINAL = "original_alarm_volume"

    fun raiseToMaximum(context: Context, audio: AudioManager): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val original = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (!prefs.contains(KEY_ORIGINAL)) {
            if (!prefs.edit().putInt(KEY_ORIGINAL, original).commit()) return false
        }
        return runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
            true
        }.getOrDefault(false)
    }

    /** Reassert the alarm stream ceiling while the theft alarm remains active. */
    fun enforceMaximum(audio: AudioManager): Boolean = runCatching {
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != maximum) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maximum, 0)
        }
        true
    }.getOrDefault(false)

    fun restore(context: Context, audio: AudioManager): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ORIGINAL)) return true
        val original = prefs.getInt(KEY_ORIGINAL, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
            .coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
        val restored = runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
            true
        }.getOrDefault(false)
        if (restored) prefs.edit().remove(KEY_ORIGINAL).commit()
        return restored
    }
}
