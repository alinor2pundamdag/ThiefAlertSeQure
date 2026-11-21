package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2DisconnectPolicyTest {
    @Test fun ownerAuthenticationInvalidatesOlderA2GraceDeadlines() {
        assertFalse(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(500L, 4, 600L, 4))
        assertTrue(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(700L, 4, 600L, 4))
    }

    @Test fun newerBootA2EventsFollowPriorBootOwnerAuthentication() {
        assertTrue(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(100L, 5, 900L, 4))
        assertFalse(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(100L, 3, 900L, 4))
    }

    @Test fun unknownBootIdentityFailsClosedAfterAnAuthenticatedDisarm() {
        assertFalse(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(700L, -1, 600L, 4))
        assertFalse(A2DisconnectPolicy.isAfterLastAuthenticatedDisarm(700L, 4, 600L, -1))
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

    @Test fun unknownBootCountsCannotProveSameBootFromElapsedTimeAlone() {
        // A reboot may already have accumulated more uptime than the old event.
        assertFalse(A2DisconnectPolicy.isSameBoot(-1, -1, 12_000L, 13_000L))
        assertFalse(A2DisconnectPolicy.isSameBoot(-1, -1, 12_000L, 500L))
        assertFalse(A2DisconnectPolicy.isSameBoot(4, -1, 12_000L, 13_000L))
        assertFalse(A2DisconnectPolicy.isSameBoot(-1, 4, 12_000L, 13_000L))
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
