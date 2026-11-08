package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationThrottleTest {
    @Test fun firstFourFailuresDoNotStartCooldown() {
        var state = AuthThrottleState()
        repeat(4) { state = AuthenticationThrottle.onFailure(state, 1_000L + it) }
        assertEquals(4, state.failedAttempts)
        assertEquals(0L, state.cooldownUntilEpochMs)
    }

    @Test fun fifthFailureStartsThirtySecondCooldown() {
        var state = AuthThrottleState()
        repeat(5) { state = AuthenticationThrottle.onFailure(state, 1_000L + it) }
        assertEquals(0, state.failedAttempts)
        assertEquals(31_004L, state.cooldownUntilEpochMs)
        assertEquals(30_000L, AuthenticationThrottle.remainingMs(state, 1_004L))
    }

    @Test fun failuresAreIgnoredDuringCooldown() {
        val cooling = AuthThrottleState(0, 31_000L)
        assertEquals(cooling, AuthenticationThrottle.onFailure(cooling, 10_000L))
    }

    @Test fun attemptsResetAfterCooldownAndAnotherFiveFailuresCooldownAgain() {
        var state = AuthThrottleState(0, 31_000L)
        state = AuthenticationThrottle.normalize(state, 31_000L)
        repeat(5) { state = AuthenticationThrottle.onFailure(state, 32_000L + it) }
        assertEquals(62_004L, state.cooldownUntilEpochMs)
        assertTrue(AuthenticationThrottle.remainingMs(state, 32_004L) > 0L)
    }

    @Test fun cooldownUsesMonotonicClockAgainstWallClockManipulation() {
        val cooling = AuthThrottleState(0, 31_000L, 50_000L, 9)
        assertEquals(10_000L, AuthenticationThrottle.remainingMs(cooling, 99_000_000L, 40_000L, 9))
        assertEquals(30_000L, AuthenticationThrottle.remainingMs(cooling, 1L, 20_000L, 9))
    }

    @Test fun rebootRebasesCooldownOnMonotonicClockInsteadOfTrustingWallClock() {
        val cooling = AuthThrottleState(0, 31_000L, 50_000L, 9)
        assertEquals(30_000L, AuthenticationThrottle.remainingMs(cooling, 21_000L, 1_000L, 10))
    }

    @Test fun movingWallClockForwardCannotBypassCooldownAfterReboot() {
        val cooling = AuthThrottleState(0, 31_000L, 50_000L, 9)
        assertEquals(30_000L, AuthenticationThrottle.remainingMs(cooling, 99_000_000L, 1_000L, 10))
    }

    @Test fun corruptThrottleDataRecoversWithBoundedCooldownAndCurrentBootIdentity() {
        val recovered = AuthenticationThrottle.recoverCorruptState(10_000L, 50_000L, 7)
        assertEquals(30_000L, AuthenticationThrottle.remainingMs(recovered, 10_000L, 50_000L, 7))
        assertEquals(7, recovered.bootCount)
    }

    @Test fun cooldownCanBeStartedByAndroidBiometricLockout() {
        val state = AuthenticationThrottle.startCooldown(AuthThrottleState(2, 0L), 5_000L)
        assertEquals(35_000L, state.cooldownUntilEpochMs)
        assertEquals(0, state.failedAttempts)
    }
}
