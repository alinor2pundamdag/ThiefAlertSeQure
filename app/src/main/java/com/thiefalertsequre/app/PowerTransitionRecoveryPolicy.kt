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

    fun isNewerDisconnectCandidate(
        candidateEventEpochMs: Long,
        candidateEventElapsedRealtimeMs: Long,
        candidateBootCount: Int,
        countdownChangedAtEpochMs: Long,
        countdownDeadlineElapsedRealtimeMs: Long,
        countdownBootCount: Int
    ): Boolean {
        val sameBoot = candidateBootCount >= 0 && countdownBootCount >= 0 &&
            candidateBootCount == countdownBootCount
        val countdownEventElapsedRealtimeMs = if (
            sameBoot && countdownDeadlineElapsedRealtimeMs > COUNTDOWN_MS
        ) countdownDeadlineElapsedRealtimeMs - COUNTDOWN_MS else 0L

        return if (countdownEventElapsedRealtimeMs > 0L && candidateEventElapsedRealtimeMs > 0L) {
            candidateEventElapsedRealtimeMs > countdownEventElapsedRealtimeMs
        } else {
            candidateEventEpochMs > countdownChangedAtEpochMs
        }
    }

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
}
