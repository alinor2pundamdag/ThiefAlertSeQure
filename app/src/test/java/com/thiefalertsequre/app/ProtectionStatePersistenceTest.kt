package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionStatePersistenceTest {
    @Test fun missingStateUsesFirstInstallDisarmedDefault() {
        assertEquals(
            ProtectionState(),
            ProtectionState.fromPersisted(null, 0L, 0L, 0L, -1)
        )
    }

    @Test fun unknownPersistedModeFailsClosedIntoLatchedAlarm() {
        val state = ProtectionState.fromPersisted("UNKNOWN_MODE", 0L, 123L, 0L, -1)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, state.mode)
        assertEquals(123L, state.changedAtEpochMs)
    }

    @Test fun malformedCountdownCannotSilentlyBecomeDisarmed() {
        val state = ProtectionState.fromPersisted("COUNTDOWN", 0L, 123L, 0L, -1)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, state.mode)
    }

    @Test fun negativeMonotonicDeadlineFailsClosed() {
        val state = ProtectionState.fromPersisted("COUNTDOWN", 10_000L, 1_000L, -1L, 4)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, state.mode)
    }

    @Test fun validCountdownRetainsItsDeadlineAndBootIdentity() {
        val expected = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 15_000L, 7)
        assertEquals(
            expected,
            ProtectionState.fromPersisted("COUNTDOWN", 11_000L, 1_000L, 15_000L, 7)
        )
    }

    @Test fun rebootPendingCountdownUsesDedicatedSentinelOnlyWithoutMonotonicDeadline() {
        val pending = ProtectionState.fromPersisted(
            "COUNTDOWN", 51_000L, 50_000L, 0L, BOOT_RECONCILIATION_PENDING
        )
        assertEquals(ProtectionMode.COUNTDOWN, pending.mode)
        assertEquals(BOOT_RECONCILIATION_PENDING, pending.bootCount)

        val invalid = ProtectionState.fromPersisted(
            "COUNTDOWN", 51_000L, 50_000L, 1L, BOOT_RECONCILIATION_PENDING
        )
        assertEquals(ProtectionMode.ALARM_TRIGGERED, invalid.mode)
    }

    @Test fun validCountdownWithoutBootIdentityRemainsPendingAndSafe() {
        val expected = ProtectionState(ProtectionMode.COUNTDOWN, 11_000L, 1_000L, 0L, -1)
        assertEquals(
            expected,
            ProtectionState.fromPersisted("COUNTDOWN", 11_000L, 1_000L, 0L, -1)
        )
    }

    @Test fun latchedAlarmRemainsLatchedAndIgnoresStaleDeadlineFields() {
        val state = ProtectionState.fromPersisted("ALARM_TRIGGERED", 99_000L, 1_000L, 88_000L, 7)
        assertEquals(ProtectionMode.ALARM_TRIGGERED, state.mode)
        assertEquals(0L, state.deadlineEpochMs)
        assertEquals(0L, state.deadlineElapsedRealtimeMs)
    }

    @Test fun armedAndDisarmedStatesDoNotRetainStaleCountdownDeadlines() {
        val armed = ProtectionState.fromPersisted("ARMED", 99_000L, 1_000L, 88_000L, 7)
        val disarmed = ProtectionState.fromPersisted("DISARMED", 99_000L, 1_000L, 88_000L, 7)
        assertEquals(ProtectionMode.ARMED, armed.mode)
        assertEquals(0L, armed.deadlineEpochMs)
        assertEquals(ProtectionMode.DISARMED, disarmed.mode)
        assertEquals(0L, disarmed.deadlineElapsedRealtimeMs)
    }
}
