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
    ALTERNATING("Alternating"),
    CUSTOM("Custom")
}

object AlarmFlashPatternPolicy {
    private const val PREFS = "alarm_flash_settings_v1"
    private const val KEY_PATTERN = "pattern"
    private const val KEY_SPEED_PERCENT = "speed_percent"
    private const val KEY_CUSTOM_ON_MS = "custom_on_ms"
    private const val KEY_CUSTOM_OFF_MS = "custom_off_ms"
    private val patterns = AlarmFlashPattern.entries

    fun read(context: Context): AlarmFlashPattern {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = SafePreferences.string(prefs, KEY_PATTERN, AlarmFlashPattern.SLOW.name)
        return runCatching { AlarmFlashPattern.valueOf(raw ?: AlarmFlashPattern.SLOW.name) }
            .getOrDefault(AlarmFlashPattern.SLOW)
    }

    fun speedPercent(context: Context): Int =
        SafePreferences.int(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), KEY_SPEED_PERCENT, 100)
            .coerceIn(50, 200)

    fun saveSpeedPercent(context: Context, value: Int): Boolean {
        require(value in 50..200)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SPEED_PERCENT, value).commit()
    }

    fun customTimings(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val onMs = SafePreferences.long(prefs, KEY_CUSTOM_ON_MS, 700L).coerceIn(100L, 2_000L)
        val offMs = SafePreferences.long(prefs, KEY_CUSTOM_OFF_MS, 700L).coerceIn(100L, 2_000L)
        return onMs to offMs
    }

    fun saveCustomTimings(context: Context, onMs: Long, offMs: Long): Boolean {
        require(onMs in 100L..2_000L && offMs in 100L..2_000L)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CUSTOM_ON_MS, onMs).putLong(KEY_CUSTOM_OFF_MS, offMs).commit()
    }

    fun save(context: Context, pattern: AlarmFlashPattern): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PATTERN, pattern.name).commit()

    fun next(context: Context): AlarmFlashPattern {
        val current = read(context)
        val next = patterns[(patterns.indexOf(current) + 1) % patterns.size]
        return if (save(context, next)) next else current
    }

    fun delayMs(
        pattern: AlarmFlashPattern,
        step: Int,
        speedPercent: Int = 100,
        customOnMs: Long = 700L,
        customOffMs: Long = 700L
    ): Long {
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
            AlarmFlashPattern.CUSTOM -> longArrayOf(customOnMs.coerceIn(100L, 2_000L), customOffMs.coerceIn(100L, 2_000L))
        }
        val base = sequence[Math.floorMod(step, sequence.size)]
        if (pattern == AlarmFlashPattern.STEADY) return base
        return (base * 100L / speedPercent.coerceIn(50, 200)).coerceIn(80L, 5_000L)
    }

    fun nextTorchState(pattern: AlarmFlashPattern, currentOn: Boolean): Boolean =
        if (pattern == AlarmFlashPattern.STEADY) true else !currentOn
}
