package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
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

    @Test fun powerMonitorStopsForTriggeredAlarmOrExplicitOwnerOptOut() {
        assertEquals(false, ProtectionMonitorPolicy.shouldRun(true, ProtectionMode.ALARM_TRIGGERED))
        assertEquals(false, ProtectionMonitorPolicy.shouldRun(false, ProtectionMode.DISARMED))
    }

    @Test fun motionAlarmIsOptInAndOnlyRunsWhileArmed() {
        assertEquals(false, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(false, ProtectionMode.ARMED))
        assertEquals(true, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.ARMED))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.COUNTDOWN))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.DISARMED))
        assertEquals(false, ProtectionMonitorPolicy.shouldMonitorMotion(true, ProtectionMode.ALARM_TRIGGERED))
    }
}