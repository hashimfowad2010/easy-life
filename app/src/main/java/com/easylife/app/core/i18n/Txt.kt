package com.easylife.app.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val tag: String) {
    EN("en"),
    UR("ur"),
    ;

    val isRtl: Boolean get() = this == UR

    companion object {
        fun fromTag(tag: String?): AppLanguage? = when {
            tag == null -> null
            tag.startsWith("ur") -> UR
            tag.startsWith("en") -> EN
            else -> null
        }
    }
}

/**
 * One piece of UI text in both languages. Every user-visible string in the app is a [Txt],
 * so a missing Urdu translation is impossible by construction.
 * Placeholders use [String.format] syntax (%1$s, %2$d) and are formatted with Western digits.
 */
@Immutable
data class Txt(val en: String, val ur: String) {
    fun get(lang: AppLanguage): String = if (lang == AppLanguage.UR) ur else en
    fun format(lang: AppLanguage, vararg args: Any?): String = String.format(Locale.US, get(lang), *args)
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

/** Resolve text for the current UI language. */
@Composable
@ReadOnlyComposable
fun Txt.t(): String = get(LocalAppLanguage.current)

/** Resolve and format text for the current UI language. */
@Composable
@ReadOnlyComposable
fun Txt.t(vararg args: Any?): String = format(LocalAppLanguage.current, *args)

@Composable
@ReadOnlyComposable
fun currentLanguage(): AppLanguage = LocalAppLanguage.current
