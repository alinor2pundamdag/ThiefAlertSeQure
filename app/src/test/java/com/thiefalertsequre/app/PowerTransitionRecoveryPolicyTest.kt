package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionRecoveryPolicyTest {
    @Test fun newerSameBootCandidateUsesMonotonicTimeEvenIfWallClockMovedBack() {
        assertTrue(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 900L,
                candidateEventElapsedRealtimeMs = 12_000L,
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 20_000L,
                countdownBootCount = 4
            )
        )
    }

    @Test fun sameOrOlderSameBootCandidateDoesNotReplaceCountdown() {
        assertFalse(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 2_000L,
                candidateEventElapsedRealtimeMs = 10_000L,
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 20_000L,
                countdownBootCount = 4
            )
        )
    }

    @Test fun unavailableBootIdentityFallsBackToWallClockEventTime() {
        assertTrue(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 2_000L,
                candidateEventElapsedRealtimeMs = 1_000L,
                candidateBootCount = -1,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 0L,
                countdownBootCount = -1
            )
        )
    }
}
