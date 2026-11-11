package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPresentationPolicyTest {
    @Test fun dashboardMustBeExplicitlyEnabled() {
        assertFalse(AlarmPresentationPolicy.shouldUseFullScreenIntent(false, 36, true))
        assertFalse(AlarmPresentationPolicy.shouldUseFullScreenIntent(false, 26, false))
    }

    @Test fun preAndroid14UsesOwnerOptInWithoutSeparateSystemAccess() {
        assertTrue(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 26, false))
        assertTrue(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 33, false))
    }

    @Test fun Android14AndLaterRequireSystemFullScreenAccess() {
        assertFalse(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 34, false))
        assertFalse(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 36, false))
        assertTrue(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 34, true))
        assertTrue(AlarmPresentationPolicy.shouldUseFullScreenIntent(true, 37, true))
    }
}
