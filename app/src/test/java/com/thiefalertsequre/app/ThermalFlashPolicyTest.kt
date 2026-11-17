package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalFlashPolicyTest {
    private val severe = 4

    @Test fun suppressesFlashWhenThermalListenerCannotBeRegistered() {
        assertTrue(ThermalFlashPolicy.shouldSuppress(0, false, severe))
    }

    @Test fun suppressesFlashWhenInitialThermalStatusIsUnknown() {
        assertTrue(ThermalFlashPolicy.shouldSuppress(null, true, severe))
    }

    @Test fun suppressesFlashAtOrAboveSevereThermalStatus() {
        assertTrue(ThermalFlashPolicy.shouldSuppress(severe, true, severe))
        assertTrue(ThermalFlashPolicy.shouldSuppress(severe + 1, true, severe))
    }

    @Test fun permitsFlashOnlyWhenMonitoringIsAvailableAndStatusIsBelowSevere() {
        assertFalse(ThermalFlashPolicy.shouldSuppress(0, true, severe))
        assertFalse(ThermalFlashPolicy.shouldSuppress(severe - 1, true, severe))
    }
}
