package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessScorePolicyTest {
    @Test fun scoreUsesOnlyApplicableSignalsAndExplainsMissingItems() {
        val score = ReadinessScorePolicy.calculate(
            listOf(
                ReadinessSignal("Monitor running", true, 30),
                ReadinessSignal("Notifications enabled", false, 20),
                ReadinessSignal("Secure lock configured", true, 20)
            )
        )
        assertEquals(50, score.points)
        assertEquals(70, score.possiblePoints)
        assertEquals(71, score.percent)
        assertEquals(listOf("Notifications enabled"), score.missing)
    }

    @Test fun allSatisfiedSignalsProduceOneHundredPercent() {
        val score = ReadinessScorePolicy.calculate(
            listOf(ReadinessSignal("A", true, 1), ReadinessSignal("B", true, 3))
        )
        assertEquals(100, score.percent)
        assertTrue(score.missing.isEmpty())
    }
}
