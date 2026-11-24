package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceStudioCatalogTest {
    @Test
    fun appearanceCatalogsHaveTheAgreedCompleteCounts() {
        assertEquals(15, AppearanceStudioStore.icons.size)
        assertEquals(15, AppearanceStudioStore.icons.distinct().size)
        assertEquals(8, AppearanceStudioStore.backgrounds.size)
        assertEquals(8, AppearanceStudioStore.backgrounds.distinct().size)
        assertEquals(5, AppearanceStudioStore.animations.size)
        assertEquals(5, AppearanceStudioStore.animations.distinct().size)
        assertEquals(5, AlarmTonePreset.builtIns.size)
        assertEquals(5, AlarmTonePreset.builtIns.map { it.name }.distinct().size)
    }
}
