package com.thiefalertsequre.app

import android.content.Context

class ProtectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)

    fun read(): ProtectionState = synchronized(LOCK) { readUnlocked() }

    fun write(state: ProtectionState): Boolean = synchronized(LOCK) { writeUnlocked(state) }

    fun update(transform: (ProtectionState) -> ProtectionState): ProtectionState = synchronized(LOCK) {
        val next = transform(readUnlocked())
        writeUnlocked(next)
        next
    }

    fun readAuthenticationThrottle(now: Long): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked()
        val normalized = AuthenticationThrottle.normalize(current, now)
        if (normalized != current) writeThrottleUnlocked(normalized)
        normalized
    }

    fun recordAuthenticationFailure(now: Long): AuthThrottleState = synchronized(LOCK) {
        val next = AuthenticationThrottle.onFailure(readThrottleUnlocked(), now)
        writeThrottleUnlocked(next)
        next
    }

    fun startAuthenticationCooldown(now: Long): AuthThrottleState = synchronized(LOCK) {
        val next = AuthenticationThrottle.startCooldown(readThrottleUnlocked(), now)
        writeThrottleUnlocked(next)
        next
    }

    fun resetAuthenticationFailures() = synchronized(LOCK) {
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
        prefs.getLong(KEY_AUTH_COOLDOWN, 0L)
    )

    private fun writeThrottleUnlocked(state: AuthThrottleState) {
        prefs.edit()
            .putInt(KEY_AUTH_FAILURES, state.failedAttempts)
            .putLong(KEY_AUTH_COOLDOWN, state.cooldownUntilEpochMs)
            .commit()
    }

    companion object {
        private val LOCK = Any()
        private const val KEY_AUTH_FAILURES = "auth_failed_attempts"
        private const val KEY_AUTH_COOLDOWN = "auth_cooldown_until"
    }
}
