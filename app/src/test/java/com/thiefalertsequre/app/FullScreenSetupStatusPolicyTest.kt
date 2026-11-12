package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenSetupStatusPolicyTest {
    @Test fun disabledOptionalDashboardDoesNotRequirePermission() {
        val item = FullScreenSetupStatusPolicy.evaluate(false, 34, false)
        assertEquals(SetupState.NEUTRAL, item.state)
    }

    @Test fun enabledDashboardWithAccessIsVerified() {
        val item = FullScreenSetupStatusPolicy.evaluate(true, 34, true)
        assertEquals(SetupState.VERIFIED, item.state)
    }

    @Test fun enabledDashboardWithoutAccessRequiresAction() {
        val item = FullScreenSetupStatusPolicy.evaluate(true, 34, false)
        assertEquals(SetupState.ACTION_REQUIRED, item.state)
        assert(item.detail.contains("normal alarm notification and sound remain available"))
    }

    @Test fun olderAndroidDoesNotClaimASeparateAccessRequirement() {
        val item = FullScreenSetupStatusPolicy.evaluate(true, 33, false)
        assertEquals(SetupState.UNSUPPORTED, item.state)
    }
}
