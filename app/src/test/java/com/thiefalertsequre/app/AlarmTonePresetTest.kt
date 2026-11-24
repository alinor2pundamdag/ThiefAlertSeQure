package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTonePresetTest {
    @Test fun exactlyFiveSelectableBuiltInPresetsExist() {
        assertEquals(5, AlarmTonePreset.builtIns.size)
        assertTrue(AlarmTonePreset.builtIns.none { it == AlarmTonePreset.DEFAULT_AUDIO })
    }

    @Test fun storedPresetFallsBackSafely() {
        assertEquals(AlarmTonePreset.DEFAULT_AUDIO, AlarmTonePreset.fromStored(null))
        assertEquals(AlarmTonePreset.DEFAULT_AUDIO, AlarmTonePreset.fromStored("unknown"))
        assertEquals(AlarmTonePreset.SIREN, AlarmTonePreset.fromStored("SIREN"))
    }

    @Test fun presetsHaveDistinctDefinitions() {
        assertEquals(5, AlarmTonePreset.builtIns.map { it.name }.distinct().size)
        assertTrue(AlarmTonePreset.builtIns.all { it.durationMs > 0 && it.gapMs >= 0 })
        assertNotEquals(
            AlarmTonePreset.SIREN.durationMs,
            AlarmTonePreset.PULSE.durationMs
        )
    }
    @Test fun sixAlarmChoicesIncludeThePackagedDefaultFirst() {
        assertEquals(6, AlarmTonePreset.entries.size)
        assertEquals(AlarmTonePreset.DEFAULT_AUDIO, AlarmTonePreset.entries.first())
        assertEquals(5, AlarmTonePreset.builtIns.size)
        assertTrue(AlarmTonePreset.DEFAULT_AUDIO !in AlarmTonePreset.builtIns)
    }

}
