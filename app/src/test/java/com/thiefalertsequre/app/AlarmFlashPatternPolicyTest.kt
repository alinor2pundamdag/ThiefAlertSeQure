package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmFlashPatternPolicyTest {
    @Test fun allSevenPatternsHavePositiveCadence() {
        AlarmFlashPattern.entries.forEach { pattern ->
            assertTrue(AlarmFlashPatternPolicy.delayMs(pattern, 0) > 0L)
            assertTrue(AlarmFlashPatternPolicy.delayMs(pattern, 1) > 0L)
        }
    }

    @Test fun steadyPatternNeverTurnsTorchOff() {
        assertTrue(AlarmFlashPatternPolicy.nextTorchState(AlarmFlashPattern.STEADY, true))
        assertTrue(AlarmFlashPatternPolicy.nextTorchState(AlarmFlashPattern.STEADY, false))
    }

    @Test fun pulsingPatternsAlternateTorchState() {
        AlarmFlashPattern.entries.filter { it != AlarmFlashPattern.STEADY }.forEach { pattern ->
            assertEquals(false, AlarmFlashPatternPolicy.nextTorchState(pattern, true))
            assertEquals(true, AlarmFlashPatternPolicy.nextTorchState(pattern, false))
        }
    }

    @Test fun burstAndSosHaveDistinctCadence() {
        assertTrue(
            (0 until 12).any { step ->
                AlarmFlashPatternPolicy.delayMs(AlarmFlashPattern.BURST, step) !=
                    AlarmFlashPatternPolicy.delayMs(AlarmFlashPattern.SOS, step)
            }
        )
    }
}
