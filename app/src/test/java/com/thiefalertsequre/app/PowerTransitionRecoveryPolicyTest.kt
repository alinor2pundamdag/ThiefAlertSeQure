package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionRecoveryPolicyTest {
    @Test fun inconclusivePowerRetryBackoffIsExponentialAndCapped() {
        assertEquals(5_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(0))
        assertEquals(10_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(1))
        assertEquals(20_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(2))
        assertEquals(160_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(5))
        assertEquals(300_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(6))
        assertEquals(300_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(100))
        assertEquals(5_000L, PowerTransitionRecoveryPolicy.nextUnknownRetryDelayMs(-1))
    }

    @Test fun persistedRetryCounterFailsSafeForNegativeAndOversizedValues() {
        assertEquals(0, PowerTransitionRecoveryPolicy.normalizeRetryAttempt(0))
        assertEquals(4, PowerTransitionRecoveryPolicy.normalizeRetryAttempt(4))
        assertEquals(100, PowerTransitionRecoveryPolicy.normalizeRetryAttempt(-1))
        assertEquals(100, PowerTransitionRecoveryPolicy.normalizeRetryAttempt(101))
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
