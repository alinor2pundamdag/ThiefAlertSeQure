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
    @Test fun reconnectAtDeadlineCancelsAlarmEvenIfDeadlineHasElapsed() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        val next = ProtectionEngine.reconcileCountdown(s, true, 50_000L, 50_000L, 7)
        assertEquals(ProtectionMode.ARMED, next.mode)
    }
    @Test fun unpluggedAtDeadlineTriggersAlarm() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        val next = ProtectionEngine.reconcileCountdown(s, false, 11_000L, 15_000L, 7)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, next.mode)
    }
    @Test fun unknownChargerStateNeverTriggersCountdown() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        val next = ProtectionEngine.reconcileCountdown(s, null, 50_000L, 50_000L, 7)
        assertEquals(ProtectionMode.COUNTDOWN, next.mode)
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
    @Test fun rebootDuringCountdownRestartsFullGracePeriodWhenChargerIsAbsent() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        val restored = ProtectionEngine.restoreAfterBoot(s, false, 2_000L, 500L, 8)
        assertEquals(ProtectionMode.COUNTDOWN, restored.mode)
        assertEquals(12_000L, restored.deadlineEpochMs)
        assertEquals(10_500L, restored.deadlineElapsedRealtimeMs)
        assertEquals(8, restored.bootCount)
    }

    @Test fun rebootWhileArmedAndUnpluggedDoesNotInventTransition() {
        val armed = ProtectionState(ProtectionMode.ARMED, 0L, 1_000L)
        assertEquals(armed, ProtectionEngine.restoreAfterBoot(armed, false, 2_000L, 500L, 8))
    }
    @Test fun rebootDuringCountdownRearmsIfChargerIsConnected() {
        val s = ProtectionState(ProtectionMode.COUNTDOWN, 11_000, 1_000, 15_000, 7)
        assertEquals(ProtectionMode.ARMED, ProtectionEngine.restoreAfterBoot(s, true, 2_000L, 500L, 8).mode)
    }

    @Test fun countdownDeadlineFromPreviousBootIsNeverEvaluatedAgainstNewBootClock() {
        val previousBoot = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, 7)
        val unknown = ProtectionEngine.reconcileCountdown(previousBoot, null, 50_000L, 500L, 8)
        assertEquals(ProtectionMode.COUNTDOWN, unknown.mode)
        assertEquals(11_000L, unknown.deadlineEpochMs)

        val unplugged = ProtectionEngine.reconcileCountdown(previousBoot, false, 50_000L, 500L, 8)
        assertEquals(ProtectionMode.COUNTDOWN, unplugged.mode)
        assertEquals(60_000L, unplugged.deadlineEpochMs)
        assertEquals(10_500L, unplugged.deadlineElapsedRealtimeMs)
        assertEquals(8, unplugged.bootCount)
    }

    @Test fun countdownFromPreviousBootCancelsWhenChargerIsConnected() {
        val previousBoot = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, 7)
        val connected = ProtectionEngine.reconcileCountdown(previousBoot, true, 50_000L, 500L, 8)
        assertEquals(ProtectionMode.ARMED, connected.mode)
    }

    @Test fun unknownBootIdentityCannotExpireMonotonicCountdownUsingWallClock() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, 7)
        val result = ProtectionEngine.reconcileCountdown(countdown, false, 99_000_000L, 99_000L, -1)
        assertEquals(ProtectionMode.COUNTDOWN, result.mode)
        assertEquals(15_000L, result.deadlineElapsedRealtimeMs)
        assertEquals(ProtectionMode.COUNTDOWN, ProtectionEngine.evaluateDeadline(countdown, 99_000_000L, 99_000L, -1).mode)
    }

    @Test fun unknownBootIdentityCanCancelCountdownWhenChargerIsConfirmedConnected() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, 7)
        val result = ProtectionEngine.reconcileCountdown(countdown, true, 99_000_000L, 99_000L, -1)
        assertEquals(ProtectionMode.ARMED, result.mode)
    }

    @Test fun countdownCreatedWithoutBootIdentityIsRebasedWhenIdentityBecomesAvailable() {
        val countdown = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, -1)
        val result = ProtectionEngine.reconcileCountdown(countdown, false, 50_000L, 500L, 8)
        assertEquals(ProtectionMode.COUNTDOWN, result.mode)
        assertEquals(60_000L, result.deadlineEpochMs)
        assertEquals(10_500L, result.deadlineElapsedRealtimeMs)
        assertEquals(8, result.bootCount)
    }
    @Test fun authenticatedAlarmWaitsForPlugThenUnplugBeforeNextCycle() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        val disarmed = ProtectionEngine.authenticated(alarm, 12_000)
        assertEquals(ProtectionMode.DISARMED, disarmed.mode)
        assertEquals(ProtectionMode.DISARMED, ProtectionEngine.chargerDisconnected(disarmed, 13_000).mode)
        val rearmed = ProtectionEngine.chargerConnected(disarmed, 14_000)
        assertEquals(ProtectionMode.ARMED, rearmed.mode)
        assertEquals(ProtectionMode.COUNTDOWN, ProtectionEngine.chargerDisconnected(rearmed, 15_000).mode)
    }

    @Test fun reconnectCannotCancelTriggeredAlarm() {
        val s = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(s, ProtectionEngine.chargerConnected(s, 12_000))
    }
    @Test fun rebootPreservesTriggeredAlarm() {
        val s = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        assertEquals(s, ProtectionEngine.restoreAfterBoot(s, true, 20_000))
    }
    @Test fun secureUnlockedKeyguardCanCompleteLatchedAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, 11_000L)
        assertEquals(true, ProtectionEngine.canAcceptSystemUnlock(alarm, true, false))
    }

    @Test fun insecureDeviceCannotUseSystemUnlockToClearAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, 11_000L)
        assertEquals(false, ProtectionEngine.canAcceptSystemUnlock(alarm, false, false))
    }

    @Test fun stillLockedKeyguardCannotClearAlarm() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, 11_000L)
        assertEquals(false, ProtectionEngine.canAcceptSystemUnlock(alarm, true, true))
    }

    @Test fun ordinaryUnlockDoesNotClearNonAlarmState() {
        val armed = ProtectionState(ProtectionMode.ARMED, 0L, 1_000L)
        assertEquals(false, ProtectionEngine.canAcceptSystemUnlock(armed, true, false))
    }

    @Test fun onlyTriggeredStateCanBeClearedByAuthentication() {
        val alarm = ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0, 11_000)
        val armed = ProtectionState(ProtectionMode.ARMED, 0, 1_000)
        assertEquals(ProtectionMode.DISARMED, ProtectionEngine.authenticated(alarm, 12_000).mode)
        assertEquals(armed, ProtectionEngine.authenticated(armed, 12_000))
    }
    @Test fun staleDeadlineCallbackCannotMatchNewCountdown() {
        val active = ProtectionState(ProtectionMode.COUNTDOWN, 22_000L, 12_000L, 26_000L, 7)
        assertEquals(false, ProtectionEngine.isCurrentDeadline(active, 11_000L, 15_000L, 7))
        assertEquals(true, ProtectionEngine.isCurrentDeadline(active, 22_000L, 26_000L, 7))
        assertEquals(false, ProtectionEngine.isCurrentDeadline(active, 22_000L, 26_001L, 7))
        assertEquals(false, ProtectionEngine.isCurrentDeadline(active, 22_000L, 26_000L, 8))
        assertEquals(false, ProtectionEngine.isCurrentDeadline(
            ProtectionState(ProtectionMode.ARMED, 0L, 23_000L), 22_000L, 26_000L, 7
        ))
    }

    @Test fun countdownIsTheOnlyStateThatNeedsDeadlineAlarm() {
        assertEquals(true, ProtectionEngine.shouldScheduleDeadline(ProtectionMode.COUNTDOWN))
        assertEquals(false, ProtectionEngine.shouldScheduleDeadline(ProtectionMode.DISARMED))
        assertEquals(false, ProtectionEngine.shouldScheduleDeadline(ProtectionMode.ARMED))
        assertEquals(false, ProtectionEngine.shouldScheduleDeadline(ProtectionMode.ALARM_TRIGGERED))
    }
}
