package com.thiefalertsequre.app

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreTest {
    @Test fun hasSixteenUniqueThemes() {
        assertEquals(16, ThemeStore.all.size)
        assertEquals(16, ThemeStore.all.map { it.id }.toSet().size)
    }

    @Test fun hasEightDarkAndEightLight() {
        assertEquals(8, ThemeStore.all.count { it.mode == "Dark" })
        assertEquals(8, ThemeStore.all.count { it.mode == "Light" })
    }

    @Test fun invalidIdsAreRejected() {
        assertFalse(ThemeStore.valid("not-a-theme"))
        assertFalse(ThemeStore.valid(null))
        assertTrue(ThemeStore.valid(ThemeStore.DEFAULT_ID))
    }

    @Test fun everyThemeMeetsAaContrastForPrimaryTextMutedTextAndButtonText() {
        ThemeStore.all.forEach { palette ->
            assertTrue("${palette.name}: primary text contrast", contrast(palette.text, palette.background) >= 4.5)
            assertTrue("${palette.name}: muted text contrast", contrast(palette.muted, palette.background) >= 4.5)
            assertTrue("${palette.name}: button text contrast", contrast(palette.onPrimary, palette.primary) >= 4.5)
        }
    }

    private fun contrast(foreground: Int, background: Int): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
