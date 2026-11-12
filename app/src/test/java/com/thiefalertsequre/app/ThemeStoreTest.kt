package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreTest {
    @Test fun hasSixteenUniqueThemes() {
        assertEquals(16, ThemeStore.all.size)
        assertEquals(16, ThemeStore.all.map { it.id }.toSet().size)
    }

    @Test fun allPaletteColorsAreOpaque() {
        ThemeStore.all.forEach { palette ->
            listOf(
                palette.primary, palette.onPrimary, palette.background, palette.surface,
                palette.variant, palette.text, palette.muted, palette.success,
                palette.warning, palette.danger
            ).forEach { color ->
                assertEquals(palette.name + ": palette color must be opaque", 0xff, (color ushr 24) and 0xff)
            }
        }
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

    @Test fun everyThemeMeetsAaContrastForTextAndActions() {
        ThemeStore.all.forEach { palette ->
            assertAa(palette, "primary text/background", contrast(palette.text, palette.background))
            assertAa(palette, "muted text/background", contrast(palette.muted, palette.background))
            assertAa(palette, "primary text/surface", contrast(palette.text, palette.surface))
            assertAa(palette, "muted text/surface", contrast(palette.muted, palette.surface))
            assertAa(palette, "button text/primary", contrast(palette.onPrimary, palette.primary))
            assertAa(palette, "primary accent/surface", contrast(palette.primary, palette.surface))
        }
    }

    private fun assertAa(palette: ThemePalette, pair: String, ratio: Double) {
        assertTrue(palette.name + ": " + pair + " contrast was " + ratio, ratio >= 4.5)
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
        val red = (color shr 16) and 0xff
        val green = (color shr 8) and 0xff
        val blue = color and 0xff
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }
}
