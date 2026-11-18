package com.thiefalertsequre.app

import android.content.Context

enum class AlarmHapticPattern(val label: String) {
    PULSE("Pulse"),
    RAPID("Rapid"),
    SOS("SOS")
}

/** Independent repeating vibration output for a latched alarm. */
object AlarmHapticsPolicy {
    private const val PREFS = "alarm_haptics_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PATTERN = "pattern"

    fun isEnabled(context: Context): Boolean =
        SafePreferences.boolean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean): Boolean =
        ProtectionStore(context).runIfDisarmed {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).commit()
        } == true

    fun readPattern(context: Context): AlarmHapticPattern {
        val raw = SafePreferences.string(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            KEY_PATTERN,
            AlarmHapticPattern.PULSE.name
        )
        return runCatching { AlarmHapticPattern.valueOf(raw ?: AlarmHapticPattern.PULSE.name) }
            .getOrDefault(AlarmHapticPattern.PULSE)
    }

    fun nextPattern(context: Context): AlarmHapticPattern {
        val current = readPattern(context)
        return ProtectionStore(context).runIfDisarmed {
            val patterns = AlarmHapticPattern.entries
            val latest = readPattern(context)
            val next = patterns[(patterns.indexOf(latest) + 1) % patterns.size]
            if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_PATTERN, next.name).commit()
            ) next else latest
        } ?: current
    }

    fun waveform(pattern: AlarmHapticPattern): LongArray = when (pattern) {
        AlarmHapticPattern.PULSE -> longArrayOf(0L, 650L, 350L, 650L, 900L)
        AlarmHapticPattern.RAPID -> longArrayOf(0L, 180L, 120L, 180L, 120L, 180L, 600L)
        AlarmHapticPattern.SOS -> longArrayOf(
            0L, 120L, 120L, 120L, 120L, 120L, 360L,
            360L, 360L, 360L, 120L, 120L, 120L, 900L
        )
    }
}
