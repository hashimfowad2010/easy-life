package com.easylife.app.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Per-app language: the whole UI (text + RTL) follows it. Android 13+ also shows it in system settings. */
object LanguageManager {
    fun apply(lang: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }

    fun current(): AppLanguage? = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.language)
}
