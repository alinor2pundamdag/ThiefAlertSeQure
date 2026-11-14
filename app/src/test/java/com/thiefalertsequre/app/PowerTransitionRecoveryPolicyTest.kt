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
}
