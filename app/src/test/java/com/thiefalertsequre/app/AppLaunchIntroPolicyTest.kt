package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchIntroPolicyTest {
    @Test
    fun showsOnceForEachNewVersion() {
        assertTrue(AppLaunchIntroPolicy.shouldShow(6L, 7L, true))
        assertFalse(AppLaunchIntroPolicy.shouldShow(7L, 7L, true))
        assertTrue(AppLaunchIntroPolicy.shouldShow(7L, 8L, true))
    }

    @Test
    fun neverDelaysAnActiveProtectionSession() {
        assertFalse(AppLaunchIntroPolicy.shouldShow(6L, 7L, false))
        assertFalse(AppLaunchIntroPolicy.shouldShow(7L, 8L, false))
    }
}
