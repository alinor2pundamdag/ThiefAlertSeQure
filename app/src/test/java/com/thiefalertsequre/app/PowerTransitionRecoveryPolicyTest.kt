package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionRecoveryPolicyTest {
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

    @Test fun unknownBootCountCanUseMonotonicClockWhenItHasNotReset() {
        assertTrue(PowerTransitionRecoveryPolicy.isSameBoot(-1, -1, 50_000L, 51_000L))
    }
}
