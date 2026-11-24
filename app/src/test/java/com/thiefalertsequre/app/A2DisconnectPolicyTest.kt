package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2DisconnectPolicyTest {
    @Test fun a2DeadlineRetryBackoffIsBounded() {
        assertEquals(1_000L, A2DisconnectPolicy.nextRetryDelayMs(0))
        assertEquals(3_000L, A2DisconnectPolicy.nextRetryDelayMs(1))
        assertEquals(10_000L, A2DisconnectPolicy.nextRetryDelayMs(2))
        assertEquals(null, A2DisconnectPolicy.nextRetryDelayMs(3))
        assertEquals(null, A2DisconnectPolicy.nextRetryDelayMs(-1))
    }

    @Test fun disconnectGraceDeadlineStartsAtBroadcastReceipt() {
        assertEquals(9_000L, A2DisconnectPolicy.graceDeadlineUptimeMs(1_000L, 8_000L))
    }

    @Test fun graceDeadlineDoesNotOverflowMonotonicClock() {
        assertEquals(Long.MAX_VALUE, A2DisconnectPolicy.graceDeadlineUptimeMs(Long.MAX_VALUE - 5L, 8L))
    }

    @Test fun sameBootRequiresMatchingKnownBootCounts() {
        assertTrue(A2DisconnectPolicy.isSameBoot(4, 4, 12_000L, 13_000L))
        assertFalse(A2DisconnectPolicy.isSameBoot(4, 5, 12_000L, 13_000L))
        assertFalse("A future-dated event must not pass same-boot validation",
            A2DisconnectPolicy.isSameBoot(4, 4, 13_000L, 12_000L))
    }

    @Test fun elapsedRealtimeDetectsRebootWhenBootCountUnavailable() {
        assertTrue(A2DisconnectPolicy.isSameBoot(-1, -1, 12_000L, 13_000L))
        assertFalse(A2DisconnectPolicy.isSameBoot(-1, -1, 12_000L, 500L))
        assertFalse(A2DisconnectPolicy.isSameBoot(4, -1, 12_000L, 13_000L))
    }

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
    @Test fun unchangedDeviceTimerSurvivesUnrelatedRegistryChanges() {
        assertTrue(A2DisconnectPolicy.isPendingDisconnectCurrent(7L, 7L, 3L, 3L))
    }
    @Test fun corruptRevisionSentinelsCannotValidatePendingDisconnect() {
        assertFalse(A2DisconnectPolicy.isPendingDisconnectCurrent(Long.MIN_VALUE, Long.MIN_VALUE, 3L, 3L))
        assertFalse(A2DisconnectPolicy.isPendingDisconnectCurrent(7L, 7L, Long.MIN_VALUE, Long.MIN_VALUE))
        assertFalse(A2DisconnectPolicy.isPendingDisconnectCurrent(-1L, -1L, 3L, 3L))
    }
    @Test fun targetDeviceReregistrationInvalidatesItsOldTimer() {
        assertFalse(A2DisconnectPolicy.isPendingDisconnectCurrent(7L, 7L, 3L, 4L))
    }
    @Test fun monitorResetInvalidatesEveryPendingDeviceTimer() {
        assertFalse(A2DisconnectPolicy.isPendingDisconnectCurrent(7L, 8L, 3L, 3L))
    }
    @Test fun alarmRequiresEveryGuardToRemainValidAfterGracePeriod() {
        assertTrue(A2DisconnectPolicy.shouldTriggerAlarm(true, true, true, true, ProtectionMode.ARMED))
    }
    @Test fun unregisterInvalidatesPendingAlarm() {
        assertFalse(A2DisconnectPolicy.shouldTriggerAlarm(true, false, false, true, ProtectionMode.ARMED))
    }
    @Test fun staleTimerInvalidatesPendingAlarm() {
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
