package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionRecoveryPolicyTest {
    @Test fun newerSameBootCandidateUsesMonotonicTimeEvenIfWallClockMovedBack() {
        assertTrue(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 900L,
                candidateEventElapsedRealtimeMs = 12_000L,
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 20_000L,
                countdownBootCount = 4
            )
        )
    }

    @Test fun sameOrOlderSameBootCandidateDoesNotReplaceCountdown() {
        assertFalse(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 2_000L,
                candidateEventElapsedRealtimeMs = 10_000L,
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 20_000L,
                countdownBootCount = 4
            )
        )
    }

    @Test fun unavailableBootIdentityFallsBackToWallClockEventTime() {
        assertTrue(
            PowerTransitionRecoveryPolicy.isNewerDisconnectCandidate(
                candidateEventEpochMs = 2_000L,
                candidateEventElapsedRealtimeMs = 1_000L,
                candidateBootCount = 4,
                countdownChangedAtEpochMs = 1_000L,
                countdownDeadlineElapsedRealtimeMs = 0L,
                countdownBootCount = -1
            )
        )
    }

    @Test fun pendingCandidateRequiresItsOwnCurrentGenerationAndDisconnectedBaseline() {
        assertTrue(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 8L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(false, 8L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 7L, 8L, false))
        assertFalse(PowerTransitionRecoveryPolicy.isCurrentCandidate(true, 8L, 8L, true))
    }

    @Test fun pendingUnplugCanResumeOnSameKnownBoot() {
        assertTrue(PowerTransitionRecoveryPolicy.isSameBoot(12, 12, 50_000L, 51_000L))
    }

    @Test fun pendingUnplugCannotCrossKnownBootChange() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, 13, 50_000L, 51_000L))
    }

    @Test fun elapsedRealtimeResetRejectsPendingCandidateEvenWithoutBootCount() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, -1, 50_000L, 500L))
    }

    @Test fun missingOneBootIdentityIsNotTreatedAsSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, -1, 50_000L, 51_000L))
    }

    @Test fun unknownBootCountsCannotProveSameBootFromElapsedTimeAlone() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, -1, 50_000L, 51_000L))
    }

    @Test fun missingCandidateBootIdentityCannotProveSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(-1, 12, 50_000L, 51_000L))
    }

    @Test fun missingCurrentBootIdentityCannotProveSameBoot() {
        assertFalse(PowerTransitionRecoveryPolicy.isSameBoot(12, -1, 50_000L, 51_000L))
    }

    @Test fun missedDisconnectRecoveryRequiresAValidSameBootConnectedArmedBaseline() {
        assertTrue(
            PowerTransitionRecoveryPolicy.shouldRecoverMissedDisconnect(
                monitorEnabled = true,
                baselineKnown = true,
                baselineConnected = true,
                baselineBootCount = 12,
                currentBootCount = 12,
                baselineGeneration = 4L,
                pendingDisconnect = false,
                protectionMode = ProtectionMode.ARMED,
                liveConnected = false
            )
        )
    }

    @Test fun missedDisconnectRecoveryRejectsEveryUnsafeOrAmbiguousCondition() {
        val valid = mapOf(
            "monitorEnabled" to true,
            "baselineKnown" to true,
            "baselineConnected" to true,
            "baselineBootCount" to 12,
            "currentBootCount" to 12,
            "baselineGeneration" to 4L,
            "pendingDisconnect" to false,
            "protectionMode" to ProtectionMode.ARMED,
            "liveConnected" to false
        )
        fun evaluate(overrides: Map<String, Any?>): Boolean {
            val values = valid + overrides
            return PowerTransitionRecoveryPolicy.shouldRecoverMissedDisconnect(
                monitorEnabled = values.getValue("monitorEnabled") as Boolean,
                baselineKnown = values.getValue("baselineKnown") as Boolean,
                baselineConnected = values.getValue("baselineConnected") as Boolean,
                baselineBootCount = values.getValue("baselineBootCount") as Int,
                currentBootCount = values.getValue("currentBootCount") as Int,
                baselineGeneration = values.getValue("baselineGeneration") as Long,
                pendingDisconnect = values.getValue("pendingDisconnect") as Boolean,
                protectionMode = values.getValue("protectionMode") as ProtectionMode,
                liveConnected = values["liveConnected"] as Boolean?
            )
        }
        assertFalse(evaluate(mapOf("monitorEnabled" to false)))
        assertFalse(evaluate(mapOf("baselineKnown" to false)))
        assertFalse(evaluate(mapOf("baselineConnected" to false)))
        assertFalse(evaluate(mapOf("baselineBootCount" to -1)))
        assertFalse(evaluate(mapOf("currentBootCount" to 13)))
        assertFalse(evaluate(mapOf("baselineGeneration" to -1L)))
        assertFalse(evaluate(mapOf("pendingDisconnect" to true)))
        assertFalse(evaluate(mapOf("protectionMode" to ProtectionMode.DISARMED)))
        assertFalse(evaluate(mapOf("protectionMode" to ProtectionMode.COUNTDOWN)))
        assertFalse(evaluate(mapOf("protectionMode" to ProtectionMode.ALARM_TRIGGERED)))
        assertFalse(evaluate(mapOf("liveConnected" to true)))
        assertFalse(evaluate(mapOf("liveConnected" to null)))
    }

}
