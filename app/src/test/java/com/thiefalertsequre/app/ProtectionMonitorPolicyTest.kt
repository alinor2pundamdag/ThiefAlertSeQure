package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionMonitorPolicyTest {
    @Test fun chargerMonitorIsEnabledByDefault() {
        assertEquals(true, ProtectionMonitorPolicy.DEFAULT_ENABLED)
    }

    @Test fun powerMonitorRunsWhileDisarmedArmedAndCountingDown() {
        assertEquals(true, ProtectionMonitorPolicy.shouldRun(true, ProtectionMode.DISARMED))
        assertEquals(true, ProtectionMonitorPolicy.shouldRun(true, ProtectionMode.ARMED))
        assertEquals(true, ProtectionMonitorPolicy.shouldRun(true, ProtectionMode.COUNTDOWN))
    }

    @Test fun powerMonitorStopsForTriggeredAlarmAndDisarmedOwnerOptOut() {
        assertEquals(false, ProtectionMonitorPolicy.shouldRun(true, ProtectionMode.ALARM_TRIGGERED))
        assertEquals(false, ProtectionMonitorPolicy.shouldRun(false, ProtectionMode.DISARMED))
        // Active protection always retains its monitor, even if an imported or
        // stale preference says OFF. Explicit owner stop first disarms the state.
        assertTrue(ProtectionMonitorPolicy.shouldRun(false, ProtectionMode.ARMED))
        assertTrue(ProtectionMonitorPolicy.shouldRun(false, ProtectionMode.COUNTDOWN))
    }

    @Test fun motionAlarmIsOptInAndOnlyRunsWhileArmed() {
        assertEquals(false, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(false, ProtectionMode.ARMED))
        assertEquals(true, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.ARMED))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.COUNTDOWN))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.DISARMED))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.ALARM_TRIGGERED))
    }
    @Test fun notificationStopAppliesEvenWhenMonitorIsEnabled() {
        assertTrue(ProtectionMonitorPolicy.shouldApplyStopRequest(
            preferenceAlreadyCommitted = false,
            monitorEnabled = true
        ))
    }

    @Test fun committedSettingsStopAppliesWhileMonitorRemainsOff() {
        assertTrue(ProtectionMonitorPolicy.shouldApplyStopRequest(
            preferenceAlreadyCommitted = true,
            monitorEnabled = false
        ))
    }

    @Test fun staleSettingsStopCannotOverrideANewerOwnerReenable() {
        assertFalse(ProtectionMonitorPolicy.shouldApplyStopRequest(
            preferenceAlreadyCommitted = true,
            monitorEnabled = true
        ))
    }

}