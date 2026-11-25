package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenSetupStatusPolicyTest {
    @Test fun disabledOptionalDashboardDoesNotRequirePermission() {
        assertEquals(SetupState.NEUTRAL, FullScreenSetupStatusPolicy.evaluate(false, 34, false).state)
    }

    @Test fun enabledDashboardWithAccessIsVerified() {
        assertEquals(SetupState.VERIFIED, FullScreenSetupStatusPolicy.evaluate(true, 34, true).state)
    }

    @Test fun enabledDashboardWithoutAccessRequiresAction() {
        val item = FullScreenSetupStatusPolicy.evaluate(true, 34, false)
        assertEquals(SetupState.ACTION_REQUIRED, item.state)
        assert(item.detail.contains("normal alarm notification and sound remain available"))
    }

    @Test fun olderAndroidAllowsOwnerEnabledDashboardWithoutSeparateAccessGate() {
        assertEquals(SetupState.VERIFIED, FullScreenSetupStatusPolicy.evaluate(true, 33, false).state)
    }
}
