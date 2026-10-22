package com.easylife.app.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ThemeMode
import com.easylife.app.core.designsystem.UrduFont
import com.easylife.app.core.i18n.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserPrefs(
    val language: AppLanguage? = null,
    val onboardingDone: Boolean = false,
    val enabledModules: Set<Module> = Module.entries.toSet(),
    val city: String = "Lahore",
    val pilotCode: String? = null,
    val impactOptIn: Boolean = false,
    /** Only events created after this moment are ever uploaded. */
    val impactOptInAt: Instant? = null,
    val anonId: String = "",
    val smartAiEnabled: Boolean = false,
    val geminiConsentAt: Instant? = null,
    val claudeConsentAt: Instant? = null,
    /** null = never asked. */
    val communityPricesConsent: Boolean? = null,
    val urduFont: UrduFont = UrduFont.NASTALIQ,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    val notifyLimits: Boolean = true,
    val notifyBills: Boolean = true,
    val notifyBudget: Boolean = true,
    val notifyMeterPhoto: Boolean = true,
    val biometricLockBudget: Boolean = false,
    val demoMode: Boolean = false,
    val devToolsUnlocked: Boolean = false,
    val installedAt: Instant? = null,
    val feedbackDone: Boolean = false,
    val feedbackSnoozedAt: Instant? = null,
    val smsSetupDone: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val keepRawSms: Boolean = true,
    val selectedMeterId: Long? = null,
    val activeShoppingListId: Long? = null,
    val ttsGuideShown: Boolean = false,
)

@Singleton
class PrefsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val language = stringPreferencesKey("language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val modules = stringSetPreferencesKey("enabled_modules")
        val city = stringPreferencesKey("city")
        val pilotCode = stringPreferencesKey("pilot_code")
        val impactOptIn = booleanPreferencesKey("impact_opt_in")
        val impactOptInAt = longPreferencesKey("impact_opt_in_at")
        val anonId = stringPreferencesKey("anon_id")
        val smartAi = booleanPreferencesKey("smart_ai")
        val geminiConsent = longPreferencesKey("gemini_consent_at")
        val claudeConsent = longPreferencesKey("claude_consent_at")
        val communityConsent = stringPreferencesKey("community_prices_consent")
        val urduFont = stringPreferencesKey("urdu_font")
        val themeMode = stringPreferencesKey("theme_mode")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val notifyLimits = booleanPreferencesKey("notify_limits")
        val notifyBills = booleanPreferencesKey("notify_bills")
        val notifyBudget = booleanPreferencesKey("notify_budget")
        val notifyMeter = booleanPreferencesKey("notify_meter")
        val biometric = booleanPreferencesKey("biometric_budget")
        val demo = booleanPreferencesKey("demo_mode")
        val dev = booleanPreferencesKey("dev_tools")
        val installedAt = longPreferencesKey("installed_at")
        val feedbackDone = booleanPreferencesKey("feedback_done")
        val feedbackSnoozed = longPreferencesKey("feedback_snoozed_at")
        val smsSetup = booleanPreferencesKey("sms_setup_done")
        val notifListener = booleanPreferencesKey("notification_listener")
        val keepRawSms = booleanPreferencesKey("keep_raw_sms")
        val selectedMeter = longPreferencesKey("selected_meter")
        val activeList = longPreferencesKey("active_shopping_list")
        val ttsGuide = booleanPreferencesKey("tts_guide_shown")
        val notified = stringSetPreferencesKey("notified_keys")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            language = AppLanguage.fromTag(p[K.language]),
            onboardingDone = p[K.onboardingDone] ?: false,
            enabledModules = p[K.modules]?.mapNotNull { n -> Module.entries.firstOrNull { it.name == n } }?.toSet()
                ?: Module.entries.toSet(),
            city = p[K.city] ?: "Lahore",
            pilotCode = p[K.pilotCode],
            impactOptIn = p[K.impactOptIn] ?: false,
            impactOptInAt = p[K.impactOptInAt]?.let(Instant::ofEpochMilli),
            anonId = p[K.anonId] ?: "",
            smartAiEnabled = p[K.smartAi] ?: false,
            geminiConsentAt = p[K.geminiConsent]?.let(Instant::ofEpochMilli),
            claudeConsentAt = p[K.claudeConsent]?.let(Instant::ofEpochMilli),
            communityPricesConsent = p[K.communityConsent]?.toBooleanStrictOrNull(),
            urduFont = p[K.urduFont]?.let { v -> UrduFont.entries.firstOrNull { it.name == v } } ?: UrduFont.NASTALIQ,
            themeMode = p[K.themeMode]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            quietStartHour = p[K.quietStart] ?: 22,
            quietEndHour = p[K.quietEnd] ?: 8,
            notifyLimits = p[K.notifyLimits] ?: true,
            notifyBills = p[K.notifyBills] ?: true,
            notifyBudget = p[K.notifyBudget] ?: true,
            notifyMeterPhoto = p[K.notifyMeter] ?: true,
            biometricLockBudget = p[K.biometric] ?: false,
            demoMode = p[K.demo] ?: false,
            devToolsUnlocked = p[K.dev] ?: false,
            installedAt = p[K.installedAt]?.let(Instant::ofEpochMilli),
            feedbackDone = p[K.feedbackDone] ?: false,
            feedbackSnoozedAt = p[K.feedbackSnoozed]?.let(Instant::ofEpochMilli),
            smsSetupDone = p[K.smsSetup] ?: false,
            notificationListenerEnabled = p[K.notifListener] ?: false,
            keepRawSms = p[K.keepRawSms] ?: true,
            selectedMeterId = p[K.selectedMeter],
            activeShoppingListId = p[K.activeList],
            ttsGuideShown = p[K.ttsGuide] ?: false,
        )
    }

    suspend fun current(): UserPrefs = prefs.first()

    /** First launch bookkeeping: an anonymous random id (never linked to the person) and the install date. */
    suspend fun ensureInstallInfo() {
        context.dataStore.edit { p ->
            if (p[K.anonId].isNullOrBlank()) p[K.anonId] = UUID.randomUUID().toString()
            if (p[K.installedAt] == null) p[K.installedAt] = System.currentTimeMillis()
        }
    }

    suspend fun setLanguage(lang: AppLanguage) = context.dataStore.edit { it[K.language] = lang.tag }
    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[K.onboardingDone] = done }
    suspend fun setEnabledModules(modules: Set<Module>) = context.dataStore.edit { it[K.modules] = modules.map { m -> m.name }.toSet() }
    suspend fun setCity(city: String) = context.dataStore.edit { it[K.city] = city }
    suspend fun setPilotCode(code: String?) = context.dataStore.edit {
        if (code.isNullOrBlank()) it.remove(K.pilotCode) else it[K.pilotCode] = code.trim().uppercase()
    }
    suspend fun setImpactOptIn(v: Boolean) = context.dataStore.edit {
        it[K.impactOptIn] = v
        if (v) it[K.impactOptInAt] = System.currentTimeMillis() else it.remove(K.impactOptInAt)
    }
    suspend fun setSmartAiEnabled(v: Boolean) = context.dataStore.edit { it[K.smartAi] = v }
    suspend fun setGeminiConsent(given: Boolean) = context.dataStore.edit {
        if (given) it[K.geminiConsent] = System.currentTimeMillis() else it.remove(K.geminiConsent)
    }
    suspend fun setClaudeConsent(given: Boolean) = context.dataStore.edit {
        if (given) it[K.claudeConsent] = System.currentTimeMillis() else it.remove(K.claudeConsent)
    }
    suspend fun setCommunityPricesConsent(v: Boolean) = context.dataStore.edit { it[K.communityConsent] = v.toString() }
    suspend fun setUrduFont(f: UrduFont) = context.dataStore.edit { it[K.urduFont] = f.name }
    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[K.themeMode] = m.name }
    suspend fun setQuietHours(start: Int, end: Int) = context.dataStore.edit { it[K.quietStart] = start; it[K.quietEnd] = end }
    suspend fun setNotifyLimits(v: Boolean) = context.dataStore.edit { it[K.notifyLimits] = v }
    suspend fun setNotifyBills(v: Boolean) = context.dataStore.edit { it[K.notifyBills] = v }
    suspend fun setNotifyBudget(v: Boolean) = context.dataStore.edit { it[K.notifyBudget] = v }
    suspend fun setNotifyMeterPhoto(v: Boolean) = context.dataStore.edit { it[K.notifyMeter] = v }
    suspend fun setBiometricLockBudget(v: Boolean) = context.dataStore.edit { it[K.biometric] = v }
    suspend fun setDemoMode(v: Boolean) = context.dataStore.edit { it[K.demo] = v }
    suspend fun setDevToolsUnlocked(v: Boolean) = context.dataStore.edit { it[K.dev] = v }
    suspend fun setFeedbackDone() = context.dataStore.edit { it[K.feedbackDone] = true }
    suspend fun snoozeFeedback() = context.dataStore.edit { it[K.feedbackSnoozed] = System.currentTimeMillis() }
    suspend fun setSmsSetupDone(v: Boolean) = context.dataStore.edit { it[K.smsSetup] = v }
    suspend fun setNotificationListenerEnabled(v: Boolean) = context.dataStore.edit { it[K.notifListener] = v }
    suspend fun setKeepRawSms(v: Boolean) = context.dataStore.edit { it[K.keepRawSms] = v }
    suspend fun setSelectedMeter(id: Long?) = context.dataStore.edit { if (id == null) it.remove(K.selectedMeter) else it[K.selectedMeter] = id }
    suspend fun setActiveShoppingList(id: Long?) = context.dataStore.edit { if (id == null) it.remove(K.activeList) else it[K.activeList] = id }
    suspend fun setTtsGuideShown() = context.dataStore.edit { it[K.ttsGuide] = true }

    /** Notification de-duplication: returns true the first time a key is seen. */
    suspend fun markNotifiedOnce(key: String): Boolean {
        var first = false
        context.dataStore.edit { p ->
            val set = p[K.notified].orEmpty()
            if (key !in set) {
                first = true
                // Keep the set bounded.
                p[K.notified] = (set.toList().takeLast(300) + key).toSet()
            }
        }
        return first
    }

    /** "Delete all my data": wipe every preference, then keep only a fresh anonymous id. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        ensureInstallInfo()
    }
}
