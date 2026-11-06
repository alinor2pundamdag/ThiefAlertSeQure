package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPowerStateTest {
    @Test fun noPluggedSourceMeansDisconnected() {
        assertFalse(ExternalPowerState.isConnected(0))
    }

    @Test fun acPowerMeansConnected() {
        assertTrue(ExternalPowerState.isConnected(1))
    }

    @Test fun usbPowerMeansConnected() {
        assertTrue(ExternalPowerState.isConnected(2))
    }

    @Test fun wirelessPowerMeansConnected() {
        assertTrue(ExternalPowerState.isConnected(4))
    }

    @Test fun fullBatteryStillCountsAsConnectedWhenPlugged() {
        // Connection is derived from EXTRA_PLUGGED, not BatteryManager.isCharging.
        assertTrue(ExternalPowerState.isConnected(1))
    }
}
