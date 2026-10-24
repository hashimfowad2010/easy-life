package com.easylife.app.core.designsystem

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.limits.Risk

/** The five mini-apps. Each has its own icon and colour across the app. */
enum class Module { LIMITS, BILLS, BUDGET, URDU, SHOPPING }

@Immutable
data class ModuleColors(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class ExtendedColors(
    val limits: ModuleColors,
    val bills: ModuleColors,
    val budget: ModuleColors,
    val urdu: ModuleColors,
    val shopping: ModuleColors,
    val safe: Color,
    val watch: Color,
    val danger: Color,
    val crossed: Color,
    val demo: ModuleColors,
    val smartAi: ModuleColors,
    val uncertain: Color,
) {
    fun module(m: Module): ModuleColors = when (m) {
        Module.LIMITS -> limits
        Module.BILLS -> bills
        Module.BUDGET -> budget
        Module.URDU -> urdu
        Module.SHOPPING -> shopping
    }

    fun risk(r: Risk): Color = when (r) {
        Risk.SAFE -> safe
        Risk.WATCH -> watch
        Risk.DANGER -> danger
        Risk.CROSSED -> crossed
    }
}

private val LightExtended = ExtendedColors(
    limits = ModuleColors(Color(0xFFB45309), Color(0xFFFFF1D6), Color(0xFF4A2800)),
    bills = ModuleColors(Color(0xFF1E5FBF), Color(0xFFDCE9FF), Color(0xFF0B2350)),
    budget = ModuleColors(Color(0xFF2E7D32), Color(0xFFDDF2DE), Color(0xFF0D3311)),
    urdu = ModuleColors(Color(0xFF7B3FA0), Color(0xFFF1E3FA), Color(0xFF331045)),
    shopping = ModuleColors(Color(0xFFC2185B), Color(0xFFFCE4EC), Color(0xFF4A0A24)),
    safe = Color(0xFF2E7D32),
    watch = Color(0xFFB26A00),
    danger = Color(0xFFD84315),
    crossed = Color(0xFFB71C1C),
    demo = ModuleColors(Color(0xFF6D4C41), Color(0xFFFFE0B2), Color(0xFF3E2723)),
    smartAi = ModuleColors(Color(0xFF3949AB), Color(0xFFE3E7FF), Color(0xFF121A5C)),
    uncertain = Color(0xFFFFE08A),
)

private val DarkExtended = ExtendedColors(
    limits = ModuleColors(Color(0xFFFBBF24), Color(0xFF3D2B0A), Color(0xFFFFE6B0)),
    bills = ModuleColors(Color(0xFF8AB4F8), Color(0xFF0F2A4D), Color(0xFFD6E4FF)),
    budget = ModuleColors(Color(0xFF81C784), Color(0xFF12301A), Color(0xFFCDEFD0)),
    urdu = ModuleColors(Color(0xFFCE93D8), Color(0xFF2F1A3A), Color(0xFFF3DDF8)),
    shopping = ModuleColors(Color(0xFFF48FB1), Color(0xFF3D1225), Color(0xFFFFD9E4)),
    safe = Color(0xFF81C784),
    watch = Color(0xFFFFCA28),
    danger = Color(0xFFFF8A65),
    crossed = Color(0xFFEF9A9A),
    demo = ModuleColors(Color(0xFFFFCC80), Color(0xFF3E2C1C), Color(0xFFFFE0B2)),
    smartAi = ModuleColors(Color(0xFF9FA8DA), Color(0xFF1C2250), Color(0xFFDDE1FF)),
    uncertain = Color(0xFF6B5200),
)

internal val TealLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2E5DD),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFFA65200),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF341100),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F3),
    surfaceContainer = Color(0xFFEAEFED),
    surfaceContainerHigh = Color(0xFFE4EAE8),
    surfaceContainerHighest = Color(0xFFDEE4E2),
)

internal val TealDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC0),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFA0F2E6),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF552100),
    tertiaryContainer = Color(0xFF793100),
    onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1514),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0F1514),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

enum class UrduFont { NASTALIQ, NASKH }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object EasyTheme {
    val colors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}

/** Wallpaper colours (Material You) exist on Android 12 and newer. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsWallpaperColors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun EasyLifeTheme(
    language: AppLanguage,
    urduFont: UrduFont,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ColorPalette = ColorPalette.TEAL,
    textSize: TextSize = TextSize.NORMAL,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (palette == ColorPalette.WALLPAPER && supportsWallpaperColors()) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) paletteDarkScheme(palette) else paletteLightScheme(palette)
    }
    // The app's own text size multiplies the phone's font size (both are respected).
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalExtendedColors provides if (dark) DarkExtended else LightExtended,
        LocalDensity provides Density(density.density, density.fontScale * textSize.factor),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = easyTypography(language, urduFont),
            shapes = EasyShapes,
            content = content,
        )
    }
}
