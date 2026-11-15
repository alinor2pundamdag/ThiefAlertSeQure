package com.thiefalertsequre.app

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPowerStateTest {
    @Test fun explicitNoExternalPowerIsDisconnected() {
        assertFalse(ExternalPowerState.isPluggedIn(0))
    }

    @Test fun missingPluggedExtraIsUnknown() {
        assertTrue(ExternalPowerState.readPluggedValue(false, 0) == null)
    }

    @Test fun explicitDisconnectedValueIsFalse() {
        assertFalse(ExternalPowerState.readPluggedValue(true, 0)!!)
    }

    @Test fun sentinelValueIsUnknownEvenWhenExtraFlagIsPresent() {
        assertTrue(ExternalPowerState.readPluggedValue(true, Int.MIN_VALUE) == null)
    }

    @Test fun usbPowerIsConnected() {
        assertTrue(ExternalPowerState.isPluggedIn(BatteryManager.BATTERY_PLUGGED_USB))
    }

    @Test fun acPowerIsConnected() {
        assertTrue(ExternalPowerState.isPluggedIn(BatteryManager.BATTERY_PLUGGED_AC))
    }

    @Test fun wirelessPowerIsConnected() {
        assertTrue(ExternalPowerState.isPluggedIn(BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    @Test fun dockPowerIsConnected() {
        assertTrue(ExternalPowerState.isPluggedIn(BatteryManager.BATTERY_PLUGGED_DOCK))
    }
}
