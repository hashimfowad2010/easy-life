package com.easylife.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun readablePairs(s: ColorScheme) = listOf(
        "primary/onPrimary" to (s.primary to s.onPrimary),
        "primaryContainer/onPrimaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
        "secondaryContainer/onSecondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
        "tertiaryContainer/onTertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
        "surface/onSurface" to (s.surface to s.onSurface),
        "surface/onSurfaceVariant" to (s.surface to s.onSurfaceVariant),
        "surfaceContainerHighest/onSurfaceVariant" to (s.surfaceContainerHighest to s.onSurfaceVariant),
        "surface/primary (text buttons)" to (s.surface to s.primary),
    )

    @Test
    fun everyColourSchemeKeepsTextReadable() {
        for (palette in ColorPalette.entries.filter { it != ColorPalette.WALLPAPER }) {
            for ((mode, scheme) in listOf("light" to paletteLightScheme(palette), "dark" to paletteDarkScheme(palette))) {
                for ((name, pair) in readablePairs(scheme)) {
                    val c = contrast(pair.first, pair.second)
                    assertTrue(c >= 4.5, "$palette $mode $name contrast is ${"%.2f".format(c)}, needs 4.5")
                }
            }
        }
    }

    @Test
    fun wallpaperFallsBackToTealWhereUnsupported() {
        assertEquals(paletteLightScheme(ColorPalette.TEAL).primary, paletteLightScheme(ColorPalette.WALLPAPER).primary)
        assertEquals(paletteDarkScheme(ColorPalette.TEAL).primary, paletteDarkScheme(ColorPalette.WALLPAPER).primary)
    }

    @Test
    fun palettesLookDifferent() {
        val swatches = ColorPalette.entries.filter { it != ColorPalette.WALLPAPER }.map { paletteSwatch(it) }
        assertEquals(swatches.size, swatches.toSet().size)
    }

    @Test
    fun textSizesGrow() {
        assertEquals(listOf(1f, 1.15f, 1.3f), TextSize.entries.map { it.factor })
    }
}
