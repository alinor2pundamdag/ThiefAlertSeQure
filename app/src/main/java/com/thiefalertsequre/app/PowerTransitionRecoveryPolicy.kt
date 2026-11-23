package com.thiefalertsequre.app

/**
 * Persisted unplug candidates may be replayed only when Android exposes matching
 * BOOT_COUNT values. elapsedRealtime alone is not sufficient to prove the same boot.
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

    fun shouldRecoverMissedDisconnect(
        monitorEnabled: Boolean,
        baselineKnown: Boolean,
        baselineConnected: Boolean,
        baselineBootCount: Int,
        currentBootCount: Int,
        baselineGeneration: Long,
        pendingDisconnect: Boolean,
        protectionMode: ProtectionMode,
        liveConnected: Boolean?
    ): Boolean =
        monitorEnabled && baselineKnown && baselineConnected &&
            baselineBootCount >= 0 && currentBootCount >= 0 &&
            baselineBootCount == currentBootCount && baselineGeneration >= 0L &&
            !pendingDisconnect && protectionMode == ProtectionMode.ARMED &&
            liveConnected == false

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
