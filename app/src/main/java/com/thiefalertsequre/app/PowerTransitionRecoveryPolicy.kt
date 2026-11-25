package com.thiefalertsequre.app

/**
 * A persisted unplug candidate may be replayed only while elapsedRealtime proves
 * the same boot. BOOT_COUNT strengthens this check when Android exposes it.
 */
internal object PowerTransitionRecoveryPolicy {
    fun isCurrentCandidate(
        pending: Boolean,
        pendingGeneration: Long,
        currentGeneration: Long,
        baselineConnected: Boolean
    ): Boolean =
        pending && pendingGeneration == currentGeneration && !baselineConnected

    fun isSameBoot(
        candidateBootCount: Int,
        currentBootCount: Int,
        eventElapsedRealtimeMs: Long,
        currentElapsedRealtimeMs: Long
    ): Boolean {
        // elapsedRealtime is monotonic only within a boot. When either boot
        // identity is unavailable, elapsed time alone cannot prove provenance.
        if (candidateBootCount < 0 || currentBootCount < 0) return false
        if (candidateBootCount != currentBootCount) return false
        if (eventElapsedRealtimeMs <= 0L || currentElapsedRealtimeMs <= 0L ||
            currentElapsedRealtimeMs < eventElapsedRealtimeMs
        ) return false
        return true
    }

    /** Retry inconclusive live power reads with bounded exponential backoff. */
    fun nextUnknownRetryDelayMs(attempt: Int): Long {
        val exponent = attempt.coerceAtLeast(0).coerceAtMost(6)
        return (5_000L * (1L shl exponent)).coerceAtMost(300_000L)
    }
}
