package com.thiefalertsequre.app

data class AuthThrottleState(
    val failedAttempts: Int = 0,
    val cooldownUntilEpochMs: Long = 0L
)

object AuthenticationThrottle {
    const val MAX_FAILURES = 5
    const val COOLDOWN_MS = 30_000L

    fun normalize(state: AuthThrottleState, now: Long): AuthThrottleState =
        if (state.cooldownUntilEpochMs > 0L && now >= state.cooldownUntilEpochMs) {
            AuthThrottleState()
        } else state.copy(failedAttempts = state.failedAttempts.coerceIn(0, MAX_FAILURES))

    fun onFailure(state: AuthThrottleState, now: Long): AuthThrottleState {
        val current = normalize(state, now)
        if (current.cooldownUntilEpochMs > now) return current
        val failures = current.failedAttempts + 1
        return if (failures >= MAX_FAILURES) {
            AuthThrottleState(0, now + COOLDOWN_MS)
        } else {
            AuthThrottleState(failures, 0L)
        }
    }

    fun startCooldown(state: AuthThrottleState, now: Long): AuthThrottleState =
        AuthThrottleState(0, maxOf(state.cooldownUntilEpochMs, now + COOLDOWN_MS))

    fun remainingMs(state: AuthThrottleState, now: Long): Long =
        (normalize(state, now).cooldownUntilEpochMs - now).coerceAtLeast(0L)
}
