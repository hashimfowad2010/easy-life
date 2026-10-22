package com.easylife.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easylife.app.R
import com.easylife.app.core.i18n.AppLanguage

private val Nastaliq = FontFamily(
    Font(R.font.noto_nastaliq_urdu, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_nastaliq_urdu, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_nastaliq_urdu, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_nastaliq_urdu, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val Naskh = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_naskh_arabic, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_naskh_arabic, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val EasyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Slightly larger than Material defaults: many users are older or read Urdu more comfortably. */
private val Base = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * Urdu needs a bigger size and much more line height (Nastaliq glyphs are tall and slanted).
 * includeFontPadding stays on so tall glyphs are never clipped in single-line labels.
 */
fun easyTypography(language: AppLanguage, urduFont: UrduFont): Typography {
    if (language != AppLanguage.UR) return Base
    val family = if (urduFont == UrduFont.NASTALIQ) Nastaliq else Naskh
    val sizeFactor = if (urduFont == UrduFont.NASTALIQ) 1.05f else 1.08f
    val lineFactor = if (urduFont == UrduFont.NASTALIQ) 2.05f else 1.6f
    fun TextStyle.urdu(): TextStyle = copy(
        fontFamily = family,
        fontSize = fontSize * sizeFactor,
        lineHeight = (lineFactor).em,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    return Typography(
        displaySmall = Base.displaySmall.urdu(),
        headlineLarge = Base.headlineLarge.urdu(),
        headlineMedium = Base.headlineMedium.urdu(),
        headlineSmall = Base.headlineSmall.urdu(),
        titleLarge = Base.titleLarge.urdu(),
        titleMedium = Base.titleMedium.urdu(),
        titleSmall = Base.titleSmall.urdu(),
        bodyLarge = Base.bodyLarge.urdu(),
        bodyMedium = Base.bodyMedium.urdu(),
        bodySmall = Base.bodySmall.urdu(),
        labelLarge = Base.labelLarge.urdu(),
        labelMedium = Base.labelMedium.urdu(),
        labelSmall = Base.labelSmall.urdu(),
    )
}

/** Big numbers (units, rupees) always use the Latin system font with tabular figures. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)
