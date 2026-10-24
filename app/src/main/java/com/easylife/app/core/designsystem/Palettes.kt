package com.easylife.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's main colour, chosen in Settings → Appearance. Module colours (Bijli Guard amber, Bills blue, …) and
 * risk colours stay the same in every scheme, so each module is always recognisable.
 */
enum class ColorPalette { TEAL, BLUE, GREEN, PURPLE, ORANGE, ROSE, WALLPAPER }

/** Extra text size on top of the phone's own font size (Settings → Appearance), for older eyes. */
enum class TextSize(val factor: Float) { NORMAL(1f), LARGE(1.15f), LARGER(1.3f) }

/**
 * Recipe for a generated scheme: main hue and saturation, a companion (tertiary) hue, and [tone], the lightness of
 * the light-mode primary. Tones are chosen so text keeps at least 4.5:1 contrast (checked in PaletteTest).
 */
internal data class PaletteSpec(val hue: Float, val sat: Float, val tertiaryHue: Float, val tertiarySat: Float, val tone: Float)

internal val PaletteSpecs: Map<ColorPalette, PaletteSpec> = mapOf(
    ColorPalette.BLUE to PaletteSpec(215f, 0.70f, 280f, 0.35f, 0.36f),
    ColorPalette.GREEN to PaletteSpec(140f, 0.55f, 45f, 0.60f, 0.30f),
    ColorPalette.PURPLE to PaletteSpec(270f, 0.50f, 330f, 0.45f, 0.40f),
    ColorPalette.ORANGE to PaletteSpec(25f, 0.85f, 200f, 0.40f, 0.34f),
    ColorPalette.ROSE to PaletteSpec(345f, 0.65f, 25f, 0.60f, 0.38f),
)

private fun hsl(h: Float, s: Float, l: Float): Color = Color.hsl(h, s.coerceIn(0f, 1f), l)

private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

/** Light scheme for [palette]; TEAL is the original hand-tuned scheme, WALLPAPER falls back to it. */
fun paletteLightScheme(palette: ColorPalette): ColorScheme {
    val p = PaletteSpecs[palette] ?: return TealLightScheme
    return lightColorScheme(
        primary = hsl(p.hue, p.sat, p.tone),
        onPrimary = Color.White,
        primaryContainer = hsl(p.hue, p.sat * 0.9f, 0.90f),
        onPrimaryContainer = hsl(p.hue, p.sat, 0.12f),
        secondary = hsl(p.hue, 0.18f, 0.38f),
        onSecondary = Color.White,
        secondaryContainer = hsl(p.hue, 0.30f, 0.90f),
        onSecondaryContainer = hsl(p.hue, 0.30f, 0.12f),
        tertiary = hsl(p.tertiaryHue, p.tertiarySat, p.tone),
        onTertiary = Color.White,
        tertiaryContainer = hsl(p.tertiaryHue, p.tertiarySat, 0.90f),
        onTertiaryContainer = hsl(p.tertiaryHue, p.tertiarySat, 0.12f),
        error = ErrorLight,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = hsl(p.hue, 0.20f, 0.975f),
        onBackground = hsl(p.hue, 0.08f, 0.10f),
        surface = hsl(p.hue, 0.20f, 0.975f),
        onSurface = hsl(p.hue, 0.08f, 0.10f),
        surfaceVariant = hsl(p.hue, 0.15f, 0.88f),
        onSurfaceVariant = hsl(p.hue, 0.08f, 0.28f),
        outline = hsl(p.hue, 0.06f, 0.46f),
        outlineVariant = hsl(p.hue, 0.10f, 0.77f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(p.hue, 0.18f, 0.955f),
        surfaceContainer = hsl(p.hue, 0.16f, 0.935f),
        surfaceContainerHigh = hsl(p.hue, 0.14f, 0.915f),
        surfaceContainerHighest = hsl(p.hue, 0.12f, 0.895f),
    )
}

/** Dark scheme for [palette]; TEAL is the original hand-tuned scheme, WALLPAPER falls back to it. */
fun paletteDarkScheme(palette: ColorPalette): ColorScheme {
    val p = PaletteSpecs[palette] ?: return TealDarkScheme
    return darkColorScheme(
        primary = hsl(p.hue, p.sat * 0.7f, 0.75f),
        onPrimary = hsl(p.hue, p.sat, 0.15f),
        primaryContainer = hsl(p.hue, p.sat * 0.8f, 0.28f),
        onPrimaryContainer = hsl(p.hue, p.sat * 0.7f, 0.90f),
        secondary = hsl(p.hue, 0.20f, 0.75f),
        onSecondary = hsl(p.hue, 0.20f, 0.15f),
        secondaryContainer = hsl(p.hue, 0.18f, 0.28f),
        onSecondaryContainer = hsl(p.hue, 0.30f, 0.90f),
        tertiary = hsl(p.tertiaryHue, p.tertiarySat * 0.8f, 0.75f),
        onTertiary = hsl(p.tertiaryHue, p.tertiarySat, 0.15f),
        tertiaryContainer = hsl(p.tertiaryHue, p.tertiarySat * 0.7f, 0.28f),
        onTertiaryContainer = hsl(p.tertiaryHue, p.tertiarySat * 0.6f, 0.90f),
        error = ErrorDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = hsl(p.hue, 0.12f, 0.065f),
        onBackground = hsl(p.hue, 0.08f, 0.90f),
        surface = hsl(p.hue, 0.12f, 0.065f),
        onSurface = hsl(p.hue, 0.08f, 0.90f),
        surfaceVariant = hsl(p.hue, 0.08f, 0.27f),
        onSurfaceVariant = hsl(p.hue, 0.10f, 0.78f),
        outline = hsl(p.hue, 0.06f, 0.57f),
        outlineVariant = hsl(p.hue, 0.08f, 0.27f),
        surfaceContainerLowest = hsl(p.hue, 0.10f, 0.04f),
        surfaceContainerLow = hsl(p.hue, 0.10f, 0.10f),
        surfaceContainer = hsl(p.hue, 0.10f, 0.12f),
        surfaceContainerHigh = hsl(p.hue, 0.10f, 0.165f),
        surfaceContainerHighest = hsl(p.hue, 0.10f, 0.21f),
    )
}

/** The colour on the palette's round swatch in Settings (the wallpaper swatch draws its own icon). */
fun paletteSwatch(palette: ColorPalette): Color = paletteLightScheme(palette).primary
