package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionRecoveryPolicyTest {
    @Test fun retriesOnlyPendingOsDisconnectWithConfirmedDisconnectedState() {
        assertTrue(PowerTransitionRecoveryPolicy.shouldRetry(true, false))
    }

    @Test fun neverInventsDisconnectFromBatterySnapshotAlone() {
        assertFalse(PowerTransitionRecoveryPolicy.shouldRetry(false, false))
    }

    @Test fun unknownPowerStateNeverRetries() {
        assertFalse(PowerTransitionRecoveryPolicy.shouldRetry(true, null))
    }

    @Test fun connectedPowerNeverRetriesPendingDisconnect() {
        assertFalse(PowerTransitionRecoveryPolicy.shouldRetry(true, true))
    }

    @Test fun pendingDisconnectRetryCanReuseItsPersistedEventAfterStateCommitFailure() {
        assertTrue(PowerTransitionRecoveryPolicy.canAcceptDisconnectCandidate(false, true))
        assertTrue(PowerTransitionRecoveryPolicy.canAcceptDisconnectCandidate(true, false))
        assertFalse(PowerTransitionRecoveryPolicy.canAcceptDisconnectCandidate(false, false))
    }

    @Test fun connectEventOnlyWinsAfterLiveConfirmationAndWithoutNewerPowerEvent() {
        assertTrue(PowerTransitionRecoveryPolicy.acceptsConnectedEvent(true, 4L, 4L))
        assertFalse(PowerTransitionRecoveryPolicy.acceptsConnectedEvent(false, 4L, 4L))
        assertFalse(PowerTransitionRecoveryPolicy.acceptsConnectedEvent(null, 4L, 4L))
        assertFalse(PowerTransitionRecoveryPolicy.acceptsConnectedEvent(true, 4L, 5L))
    }
}
