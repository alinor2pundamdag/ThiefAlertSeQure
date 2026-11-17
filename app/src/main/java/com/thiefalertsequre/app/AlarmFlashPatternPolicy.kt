package com.thiefalertsequre.app

import android.content.Context

/** User-selectable torch cadence. Torch is optional and never gates alarm audio. */
enum class AlarmFlashPattern(val label: String) {
    STEADY("Steady"),
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast"),
    BURST("Burst"),
    SOS("SOS"),
    ALTERNATING("Alternating")
}

object AlarmFlashPatternPolicy {
    private const val PREFS = "alarm_flash_settings_v1"
    private const val KEY_PATTERN = "pattern"
    private val patterns = AlarmFlashPattern.entries

    fun read(context: Context): AlarmFlashPattern {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PATTERN, AlarmFlashPattern.SLOW.name)
        return runCatching { AlarmFlashPattern.valueOf(raw ?: AlarmFlashPattern.SLOW.name) }
            .getOrDefault(AlarmFlashPattern.SLOW)
    }

    fun save(context: Context, pattern: AlarmFlashPattern): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PATTERN, pattern.name).commit()

    fun next(context: Context): AlarmFlashPattern {
        val current = read(context)
        val next = patterns[(patterns.indexOf(current) + 1) % patterns.size]
        return if (save(context, next)) next else current
    }

    fun delayMs(pattern: AlarmFlashPattern, step: Int): Long {
        val sequence = when (pattern) {
            AlarmFlashPattern.STEADY -> longArrayOf(60_000L)
            AlarmFlashPattern.SLOW -> longArrayOf(1_200L, 1_200L)
            AlarmFlashPattern.NORMAL -> longArrayOf(700L, 700L)
            AlarmFlashPattern.FAST -> longArrayOf(250L, 250L)
            AlarmFlashPattern.BURST -> longArrayOf(100L, 100L, 100L, 550L, 100L, 100L, 100L, 1_000L)
            AlarmFlashPattern.SOS -> longArrayOf(
                100L, 100L, 100L, 100L, 300L, 300L, 300L, 300L, 100L, 100L, 100L, 800L
            )
            AlarmFlashPattern.ALTERNATING -> longArrayOf(220L, 120L, 420L, 120L)
        }
        return sequence[Math.floorMod(step, sequence.size)]
    }

    fun nextTorchState(pattern: AlarmFlashPattern, currentOn: Boolean): Boolean =
        if (pattern == AlarmFlashPattern.STEADY) true else !currentOn
}
