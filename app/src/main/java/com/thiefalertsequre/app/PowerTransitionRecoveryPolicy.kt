package com.thiefalertsequre.app

/**
 * A persisted unplug candidate may be replayed only while elapsedRealtime proves
 * the same boot. BOOT_COUNT strengthens this check when Android exposes it.
 */
internal object PowerTransitionRecoveryPolicy {
    fun isSameBoot(
        candidateBootCount: Int,
        currentBootCount: Int,
        eventElapsedRealtimeMs: Long,
        currentElapsedRealtimeMs: Long
    ): Boolean {
        if (eventElapsedRealtimeMs <= 0L || currentElapsedRealtimeMs <= 0L ||
            currentElapsedRealtimeMs < eventElapsedRealtimeMs
        ) return false

        return when {
            candidateBootCount >= 0 || currentBootCount >= 0 ->
                candidateBootCount >= 0 && currentBootCount >= 0 &&
                    candidateBootCount == currentBootCount
            else -> true
        }
    }
}
