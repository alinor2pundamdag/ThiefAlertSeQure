package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2DisconnectPolicyTest {
    @Test fun gracePeriodRequiresEnabledTrustedDeviceWithConfirmedBaselineWhileArmed() {
        assertTrue(A2DisconnectPolicy.shouldStartGracePeriod(true, true, true, ProtectionMode.ARMED))
    }

    @Test fun disabledAlertCannotStartGracePeriod() {
        assertFalse(A2DisconnectPolicy.shouldStartGracePeriod(false, true, true, ProtectionMode.ARMED))
    }

    @Test fun unregisteredDeviceCannotStartGracePeriod() {
        assertFalse(A2DisconnectPolicy.shouldStartGracePeriod(true, false, true, ProtectionMode.ARMED))
    }

    @Test fun transientDisconnectWithoutBaselineCannotStartGracePeriod() {
        assertFalse(A2DisconnectPolicy.shouldStartGracePeriod(true, true, false, ProtectionMode.ARMED))
    }

    @Test fun disarmedProtectionCannotStartGracePeriod() {
        assertFalse(A2DisconnectPolicy.shouldStartGracePeriod(true, true, true, ProtectionMode.DISARMED))
    }

    @Test fun alarmRequiresEveryGuardToRemainValidAfterGracePeriod() {
        assertTrue(A2DisconnectPolicy.shouldTriggerAlarm(true, true, true, true, ProtectionMode.ARMED))
    }

    @Test fun unregisterInvalidatesPendingAlarm() {
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, false, false, true, ProtectionMode.ARMED))
    }

    @Test fun registryChangeInvalidatesPendingAlarm() {
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, true, false, true, ProtectionMode.ARMED))
    }

    @Test fun reconnectCancelsPendingAlarm() {
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, true, true, false, ProtectionMode.ARMED))
    }

    @Test fun disarmedOrAlreadyLatchedStateCannotTriggerA2Alarm() {
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, true, true, true, ProtectionMode.DISARMED))
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, true, true, true, ProtectionMode.ALARM_TRIGGERED))
    }
}
