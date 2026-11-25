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
        val savedOriginal = if (!prefs.contains(KEY_ORIGINAL)) {
            null
        } else {
            when (val stored = SafePreferences.readInt(prefs, KEY_ORIGINAL, original)) {
                is PreferenceRead.Value -> stored.value.takeIf { it >= 0 }
                is PreferenceRead.Corrupt -> null
            }
        }

        if (savedOriginal == null) {
            if (prefs.contains(KEY_ORIGINAL)) {
                record(context, "alarm_volume_snapshot_corrupt", "Previous alarm-volume snapshot was malformed; the original user volume is no longer recoverable")
            }
            if (!prefs.edit().putInt(KEY_ORIGINAL, original).commit()) {
                record(context, "alarm_volume_snapshot_persist_failed", "Could not persist the pre-alarm volume; maximum-volume enforcement was not attempted")
                return false
            }
        }

        val maximum = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) }.getOrNull()
        if (maximum == null) {
            record(context, "alarm_volume_maximum_unavailable", "Android did not return the maximum alarm-stream volume")
            return false
        }
        val raised = runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maximum, 0)
            audio.getStreamVolume(AudioManager.STREAM_ALARM) == maximum
        }.getOrDefault(false)
        if (!raised) {
            record(context, "alarm_volume_raise_failed", "Android did not confirm that the alarm stream reached its maximum volume")
        }
        return raised
    }

    /** Reassert the alarm stream ceiling while the alarm remains latched. */
    fun enforceMaximum(audio: AudioManager): Boolean = runCatching {
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != maximum) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maximum, 0)
        }
        audio.getStreamVolume(AudioManager.STREAM_ALARM) == maximum
    }.getOrDefault(false)

    fun restore(context: Context, audio: AudioManager): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ORIGINAL)) return true

        val storedOriginal = when (val stored = SafePreferences.readInt(prefs, KEY_ORIGINAL, -1)) {
            is PreferenceRead.Value -> stored.value.takeIf { it >= 0 }
            is PreferenceRead.Corrupt -> null
        }
        if (storedOriginal == null) {
            val cleared = prefs.edit().remove(KEY_ORIGINAL).commit()
            record(context, "alarm_volume_snapshot_invalid", "The original alarm-volume snapshot was corrupt or invalid; it cannot be restored")
            if (!cleared) {
                record(context, "alarm_volume_snapshot_cleanup_failed", "The corrupt alarm-volume snapshot could not be removed from preferences")
            }
            return false
        }

        val maximum = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) }.getOrNull()
        if (maximum == null) {
            record(context, "alarm_volume_restore_unavailable", "Android did not return the maximum alarm-stream volume; the saved snapshot was retained")
            return false
        }
        val original = storedOriginal.coerceIn(0, maximum)
        val restored = runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
            audio.getStreamVolume(AudioManager.STREAM_ALARM) == original
        }.getOrDefault(false)
        if (!restored) {
            record(context, "alarm_volume_restore_failed", "Android did not confirm restoration of the saved alarm-stream volume; the snapshot was retained")
            return false
        }

        if (!prefs.edit().remove(KEY_ORIGINAL).commit()) {
            record(context, "alarm_volume_snapshot_clear_failed", "Alarm volume was restored, but the saved snapshot could not be durably cleared")
            return false
        }
        record(context, "alarm_volume_restored", "Original alarm-stream volume was restored and its saved snapshot was cleared")
        return true
    }

    private fun record(context: Context, event: String, detail: String) {
        runCatching { LocalTamperHistory.record(context, event, detail) }
    }
}
