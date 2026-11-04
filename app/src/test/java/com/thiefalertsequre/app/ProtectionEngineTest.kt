package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionEngineTest {
    @Test fun plugInArms() {
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.chargerConnected(ProtectionState(), 100L).mode)
    }
    @Test fun unplugStartsTenSecondCountdown() {
        val s = ProtectionEngine.chargerDisconnected(ProtectionState(ProtectionMode.ARMED, 0, 100), 1_000L, 5_000L, 7)
        assertEquals(ProtectionMode.COUNTDOWN, s.mode)
        assertEquals(11_000L, s.deadlineEpochMs)
        assertEquals(15_000L, s.deadlineElapsedRealtimeMs)
        assertEquals(7, s.bootCount)
    }
    @Test fun reconnectBeforeDeadlineRearms() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.chargerConnected(s, 5_000).mode)
    }
    @Test fun deadlineTriggersAtMonotonicDeadline() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, ProtectionEngine.evaluateDeadline(s, 1_010L, 15_000L, 7).mode)
    }
    @Test fun wallClockChangeCannotPrematurelyTriggerDuringSameBoot() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        val early = ProtectionEngine.evaluateDeadline(s, 99_000_000L, 14_999L, 7)
        assertEquals(ProtectionMode.COUNTDOWN, early.mode)
        assertEquals(1L, ProtectionEngine.remainingMs(early, 99_000_000L, 14_999L, 7))
    }
    @Test fun rebootDuringCountdownTriggersImmediatelyWhenChargerIsAbsent() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, ProtectionEngine.restoreAfterBoot(s, false, 2_000L, 500L, 8).mode)
    }
    @Test fun rebootDuringCountdownRearmsIfChargerIsConnected() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.restoreAfterBoot(s, true, 2_000L, 500L, 8).mode)
    }
    @Test fun reconnectCannotCancelTriggeredAlarm() {
        val s = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(s, ProtectionEngine.chargerConnected(s, 12_000))
    }
    @Test fun rebootPreservesTriggeredAlarm() {
        val s = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(s, ProtectionEngine.restoreAfterBoot(s, true, 20_000))
    }
    @Test fun onlyTriggeredStateCanBeClearedByAuthentication() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        val armed = ProtectionState(ProtectionMode.ARMED, 0, 1_000)
        assertEquals(ProtectionMode.DISARMED, ProtectionEngine.authenticated(alarm, 12_000).mode)
        assertEquals(armed, ProtectionEngine.authenticated(armed, 12_000))
    }
}
