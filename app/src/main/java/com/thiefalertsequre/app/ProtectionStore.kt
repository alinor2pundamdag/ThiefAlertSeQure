package com.thiefalertsequre.app

import android.content.Context

class ProtectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)

    fun read(): ProtectionState = synchronized(LOCK) { readUnlocked() }

    fun write(state: ProtectionState): Boolean = synchronized(LOCK) { writeUnlocked(state) }

    fun update(transform: (ProtectionState) -> ProtectionState): ProtectionState = synchronized(LOCK) {
        val current = readUnlocked()
        val next = transform(current)
        if (writeUnlocked(next)) next else current
    }

    fun readAuthenticationThrottle(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked()
        val normalized = AuthenticationThrottle.normalize(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (normalized == current || writeThrottleUnlocked(normalized)) normalized else current
    }

    fun recordAuthenticationFailure(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked()
        val next = AuthenticationThrottle.onFailure(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (writeThrottleUnlocked(next)) next else current
    }

    fun startAuthenticationCooldown(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked()
        val next = AuthenticationThrottle.startCooldown(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (writeThrottleUnlocked(next)) next else current
    }

    fun resetAuthenticationFailures(): Boolean = synchronized(LOCK) {
        writeThrottleUnlocked(AuthThrottleState())
    }

    private fun readUnlocked(): ProtectionState = ProtectionState(
        mode = runCatching { ProtectionMode.valueOf(prefs.getString("mode", "DISARMED")!!) }
            .getOrDefault(ProtectionMode.DISARMED),
        deadlineEpochMs = prefs.getLong("deadline", 0L),
        changedAtEpochMs = prefs.getLong("changed", 0L),
        deadlineElapsedRealtimeMs = prefs.getLong("deadline_elapsed", 0L),
        bootCount = prefs.getInt("boot_count", -1)
    )

    private fun writeUnlocked(state: ProtectionState): Boolean = prefs.edit()
        .putString("mode", state.mode.name)
        .putLong("deadline", state.deadlineEpochMs)
        .putLong("changed", state.changedAtEpochMs)
        .putLong("deadline_elapsed", state.deadlineElapsedRealtimeMs)
        .putInt("boot_count", state.bootCount)
        .commit()

    private fun readThrottleUnlocked() = AuthThrottleState(
        prefs.getInt(KEY_AUTH_FAILURES, 0).coerceIn(0, AuthenticationThrottle.MAX_FAILURES),
        prefs.getLong(KEY_AUTH_COOLDOWN, 0L),
        prefs.getLong(KEY_AUTH_COOLDOWN_ELAPSED, 0L),
        prefs.getInt(KEY_AUTH_BOOT_COUNT, -1)
    )

    private fun writeThrottleUnlocked(state: AuthThrottleState): Boolean = prefs.edit()
        .putInt(KEY_AUTH_FAILURES, state.failedAttempts)
        .putLong(KEY_AUTH_COOLDOWN, state.cooldownUntilEpochMs)
        .putLong(KEY_AUTH_COOLDOWN_ELAPSED, state.cooldownUntilElapsedRealtimeMs)
        .putInt(KEY_AUTH_BOOT_COUNT, state.bootCount)
        .commit()

    companion object {
        private val LOCK = Any()
        private const val KEY_AUTH_FAILURES = "auth_failed_attempts"
        private const val KEY_AUTH_COOLDOWN = "auth_cooldown_until"
        private const val KEY_AUTH_COOLDOWN_ELAPSED = "auth_cooldown_until_elapsed"
        private const val KEY_AUTH_BOOT_COUNT = "auth_cooldown_boot_count"
    }
}
