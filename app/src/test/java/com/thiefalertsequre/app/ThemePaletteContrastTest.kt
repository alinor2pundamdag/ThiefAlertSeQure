package com.thiefalertsequre.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteContrastTest {
    @Test fun everyPaletteMeetsAaForTextAndSemanticStatusColors() {
        ThemeStore.all.forEach { palette ->
            val pairs = listOf(
                "text" to palette.text,
                "muted" to palette.muted,
                "primary" to palette.primary,
                "success" to palette.success,
                "warning" to palette.warning,
                "danger" to palette.danger
            )
            pairs.forEach { (label, foreground) ->
                val ratio = contrastRatio(foreground, palette.surface)
                assertTrue(
                    "${palette.id}: $label on surface contrast was $ratio; expected >= 4.5",
                    ratio >= 4.5
                )
            }
            assertTrue(
                "${palette.id}: onPrimary on primary contrast was insufficient",
                contrastRatio(palette.onPrimary, palette.primary) >= 4.5
            )
        }
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        val red = channel((color shr 16) and 0xFF)
        val green = channel((color shr 8) and 0xFF)
        val blue = channel(color and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }
}
