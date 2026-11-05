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
}