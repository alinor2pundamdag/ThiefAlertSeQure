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
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 0L,
                countdownBootCount = -1
            )
        )
    }

    @Test fun pendingCandidateRequiresItsOwnCurrentGenerationAndDisconnectedBaseline() {
        assertTrue(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 8L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(false, 8L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 7L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 8L, 8L, true))
    }

    @Test fun pendingUnplugCanResumeOnSameKnownBoot() {
        assertTrue(PowerTransitionRecoveryPolicy.isSameBoot(12, 12, 50_000L, 51_000L))
    }

    @Test fun pendingUnplugCannotCrossKnownBootChange() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, 13, 50_000L, 51_000L))
    }

    @Test fun elapsedRealtimeResetRejectsPendingCandidateEvenWithoutBootCount() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, -1, 50_000L, 500L))
    }

    @Test fun missingOneBootIdentityIsNotTreatedAsSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, -1, 50_000L, 51_000L))
    }

    @Test fun unknownBootCountsCannotProveSameBootFromElapsedTimeAlone() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, -1, 50_000L, 51_000L))
    }

    @Test fun missingCandidateBootIdentityCannotProveSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, 12, 50_000L, 51_000L))
    }

    @Test fun missingCurrentBootIdentityCannotProveSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, -1, 50_000L, 51_000L))
    }
}
