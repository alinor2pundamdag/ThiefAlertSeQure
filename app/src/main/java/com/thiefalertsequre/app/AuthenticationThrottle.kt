package com.thiefalertsequre.app

data class AuthThrottleState(
    val failedAttempts: Int = 0,
    val cooldownUntilEpochMs: Long = 0L,
    val cooldownUntilElapsedRealtimeMs: Long = 0L,
    val bootCount: Int = -1
)

object AuthenticationThrottle {
    const val MAX_FAILURES = 5
    const val COOLDOWN_MS = 30_000L

    /** Recover malformed local throttle data without blocking Android credential fallback. */
    fun recoverCorruptState(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = AuthThrottleState(
        failedAttempts = 0,
        cooldownUntilEpochMs = nowEpochMs + COOLDOWN_MS,
        cooldownUntilElapsedRealtimeMs = if (nowElapsedRealtimeMs > 0L) nowElapsedRealtimeMs + COOLDOWN_MS else 0L,
        bootCount = currentBootCount
    )

    fun normalize(
        state: AuthThrottleState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState {
        val sameBoot = state.bootCount >= 0 && currentBootCount == state.bootCount &&
            state.cooldownUntilElapsedRealtimeMs > 0L && nowElapsedRealtimeMs > 0L
        // Wall-clock time is user-adjustable. Never let a reboot or missing boot
        // identity turn a persisted cooldown into an early biometric retry.
        // Rebase one full cooldown onto the current monotonic clock on first use.
        val hasCooldown = state.cooldownUntilEpochMs > 0L ||
            state.cooldownUntilElapsedRealtimeMs > 0L
        if (hasCooldown && !sameBoot && nowElapsedRealtimeMs > 0L && currentBootCount >= 0) {
            return state.copy(
                failedAttempts = 0,
                cooldownUntilEpochMs = nowEpochMs + COOLDOWN_MS,
                cooldownUntilElapsedRealtimeMs = nowElapsedRealtimeMs + COOLDOWN_MS,
                bootCount = currentBootCount
            )
        }
        val remaining = if (sameBoot) {
            state.cooldownUntilElapsedRealtimeMs - nowElapsedRealtimeMs
        } else {
            state.cooldownUntilEpochMs - nowEpochMs
        }
        return if ((state.cooldownUntilEpochMs > 0L || state.cooldownUntilElapsedRealtimeMs > 0L) && remaining <= 0L) {
            AuthThrottleState()
        } else {
            state.copy(failedAttempts = state.failedAttempts.coerceIn(0, MAX_FAILURES))
        }
    }

    fun onFailure(
        state: AuthThrottleState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState {
        val current = normalize(state, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        val remaining = if (current.bootCount >= 0 && current.bootCount == currentBootCount &&
            current.cooldownUntilElapsedRealtimeMs > 0L && nowElapsedRealtimeMs > 0L
        ) current.cooldownUntilElapsedRealtimeMs - nowElapsedRealtimeMs
        else current.cooldownUntilEpochMs - nowEpochMs
        if (remaining > 0L) return current
        val failures = current.failedAttempts + 1
        return if (failures >= MAX_FAILURES) {
            AuthThrottleState(
                failedAttempts = 0,
                cooldownUntilEpochMs = nowEpochMs + COOLDOWN_MS,
                cooldownUntilElapsedRealtimeMs = if (nowElapsedRealtimeMs > 0L) nowElapsedRealtimeMs + COOLDOWN_MS else 0L,
                bootCount = currentBootCount
            )
        } else {
            AuthThrottleState(failedAttempts = failures)
        }
    }

    fun startCooldown(
        state: AuthThrottleState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = AuthThrottleState(
        failedAttempts = 0,
        cooldownUntilEpochMs = maxOf(state.cooldownUntilEpochMs, nowEpochMs + COOLDOWN_MS),
        cooldownUntilElapsedRealtimeMs = if (nowElapsedRealtimeMs > 0L) nowElapsedRealtimeMs + COOLDOWN_MS else 0L,
        bootCount = currentBootCount
    )

    fun remainingMs(
        state: AuthThrottleState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): Long {
        val normalized = normalize(state, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        val sameBoot = normalized.bootCount >= 0 && normalized.bootCount == currentBootCount &&
            normalized.cooldownUntilElapsedRealtimeMs > 0L && nowElapsedRealtimeMs > 0L
        return if (sameBoot) {
            (normalized.cooldownUntilElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            (normalized.cooldownUntilEpochMs - nowEpochMs).coerceAtLeast(0L)
        }
    }
}
