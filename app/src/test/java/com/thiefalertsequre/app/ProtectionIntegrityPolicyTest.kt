package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionIntegrityPolicyTest {
    @Test fun scoreCountsOnlyVerifiedSupportedComponents() {
        val score = ProtectionIntegrityPolicy.calculate(
            listOf(
                IntegritySignal("Protection store", IntegrityStatus.VERIFIED, 3),
                IntegritySignal("Evidence vault", IntegrityStatus.FAILED, 2),
                IntegritySignal("Monitor runtime", IntegrityStatus.UNKNOWN, 2),
                IntegritySignal("Unsupported OEM signal", IntegrityStatus.UNSUPPORTED, 5)
            )
        )
        assertEquals(42, score.percent)
        assertEquals(3, score.earnedPoints)
        assertEquals(7, score.possiblePoints)
        assertEquals(listOf("Evidence vault"), score.failed)
        assertEquals(listOf("Monitor runtime"), score.unconfirmed)
        assertEquals(listOf("Unsupported OEM signal"), score.unsupported)
    }

    @Test fun unsupportedChecksDoNotLowerScoreAndDuplicateLabelsAreRejected() {
        val score = ProtectionIntegrityPolicy.calculate(
            listOf(IntegritySignal("Only verified", IntegrityStatus.VERIFIED, 1),
                IntegritySignal("Not on this API", IntegrityStatus.UNSUPPORTED, 10))
        )
        assertEquals(100, score.percent)
        assertTrue(runCatching {
            ProtectionIntegrityPolicy.calculate(
                listOf(IntegritySignal("same", IntegrityStatus.VERIFIED, 1),
                    IntegritySignal("same", IntegrityStatus.FAILED, 1))
            )
        }.isFailure)
    }
}
