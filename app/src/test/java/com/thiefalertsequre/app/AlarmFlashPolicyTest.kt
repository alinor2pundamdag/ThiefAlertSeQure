package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmFlashPolicyTest {
    @Test
    fun flashRunsOnlyForLatchedAlarmState() {
        assertTrue(AlarmFlashSettings.shouldRun(ProtectionMode.ALARM_TRIGGERED))
        assertFalse(AlarmFlashSettings.shouldRun(ProtectionMode.ARMED))
        assertFalse(AlarmFlashSettings.shouldRun(ProtectionMode.DISARMED))
    }

    @Test
    fun flashPulseAlternatesAndUsesMatchingDwellTimes() {
        assertTrue(AlarmFlashSettings.nextTorchState(false))
        assertFalse(AlarmFlashSettings.nextTorchState(true))
        assertTrue(AlarmFlashSettings.delayAfter(true) == AlarmFlashSettings.ON_DURATION_MS)
        assertTrue(AlarmFlashSettings.delayAfter(false) == AlarmFlashSettings.OFF_DURATION_MS)
    }

    @Test
    fun flashPulseCadenceRemainsBelowThreeHertz() {
        val cycleMs = AlarmFlashSettings.ON_DURATION_MS + AlarmFlashSettings.OFF_DURATION_MS
        val pulsesPerSecond = 1_000.0 / cycleMs
        assertTrue(AlarmFlashSettings.ON_DURATION_MS >= 500L)
        assertTrue(AlarmFlashSettings.OFF_DURATION_MS >= 500L)
        assertTrue("Flash cadence must remain below 3 Hz", pulsesPerSecond < 3.0)
    }
}
