package com.thiefalertsequre.app

import org.junit.Assert.*
import org.junit.Test

class ProtectionEngineTest {
    @Test fun plugInArms() {
        val s = ProtectionEngine.chargerConnected(ProtectionState(), 100L)
        assertEquals(ProtectionMode.ARMED, s.mode)
    }

    @Test fun unplugStartsTenSecondCountdown() {
        val armed = ProtectionState(ProtectionMode.ARMED, 0, 100)
        val s = ProtectionEngine.chargerDisconnected(armed, 1_000L)
        assertEquals(ProtectionMode.COUNTDOWN, s.mode)
        assertEquals(11_000L, s.deadlineEpochMs)
    }

    @Test fun reconnectBeforeDeadlineRearms() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000)
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.chargerConnected(countdown, 5_000).mode)
    }

    @Test fun deadlineTriggers() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, ProtectionEngine.evaluateDeadline(countdown, 11_000).mode)
    }

    @Test fun reconnectCannotCancelTriggeredAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(alarm, ProtectionEngine.chargerConnected(alarm, 12_000))
    }

    @Test fun rebootWhileStillChargingCancelsOnlyPreTriggerCountdown() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000)
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.restoreAfterBoot(countdown, true, 5_000).mode)
    }

    @Test fun rebootPreservesTriggeredAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(alarm, ProtectionEngine.restoreAfterBoot(alarm, true, 20_000))
    }

    @Test fun validAuthenticationClearsAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(ProtectionMode.DISARMED, ProtectionEngine.authenticated(alarm, 12_000).mode)
    }
}
