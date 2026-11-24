package com.thiefalertsequre.app

import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmHapticsPolicyTest {
    @Test fun everyHapticPatternHasARepeatingWaveform() {
        AlarmHapticPattern.entries.forEach { pattern ->
            val waveform = AlarmHapticsPolicy.waveform(pattern)
            assertTrue(waveform.size >= 4)
            assertTrue(waveform.all { it >= 0L })
            assertTrue(waveform.any { it > 0L })
        }
    }

    @Test fun patternsHaveDistinctCadence() {
        val pulse = AlarmHapticsPolicy.waveform(AlarmHapticPattern.PULSE)
        val rapid = AlarmHapticsPolicy.waveform(AlarmHapticPattern.RAPID)
        val sos = AlarmHapticsPolicy.waveform(AlarmHapticPattern.SOS)
        assertTrue(!pulse.contentEquals(rapid))
        assertTrue(!rapid.contentEquals(sos))
        assertTrue(!pulse.contentEquals(sos))
    }
}
