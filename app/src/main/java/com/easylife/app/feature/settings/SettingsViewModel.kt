package com.easylife.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.ai.ModelState
import com.easylife.app.core.ai.OnDeviceTranslator
import com.easylife.app.core.cloud.AppConfig
import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.DataManager
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.prefs.UserPrefs
import com.easylife.app.core.data.repo.DemoDataSeeder
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ColorPalette
import com.easylife.app.core.designsystem.TextSize
import com.easylife.app.core.designsystem.ThemeMode
import com.easylife.app.core.designsystem.UrduFont
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.LanguageManager
import com.easylife.app.core.notify.NotificationChannels
import com.easylife.app.core.notify.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val prefs: UserPrefs = UserPrefs(),
    val config: AppConfig? = null,
    val model: ModelState = ModelState.UNKNOWN,
    val firebase: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val config: RemoteConfigRepository,
    private val translator: OnDeviceTranslator,
    private val firebase: FirebaseGateway,
    private val demo: DemoDataSeeder,
    private val data: DataManager,
    private val channels: NotificationChannels,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(prefs.prefs, config.config, translator.state) { p, c, m ->
        SettingsState(p, c, m, firebase.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private var versionTaps = 0

    init {
        viewModelScope.launch { translator.refreshState() }
    }

    fun setLanguage(lang: AppLanguage) = viewModelScope.launch {
        prefs.setLanguage(lang)
        channels.ensureCreated(lang)
        LanguageManager.apply(lang)
    }

    fun setFont(f: UrduFont) = viewModelScope.launch { prefs.setUrduFont(f) }
    fun setTheme(m: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(m) }
    fun setPalette(p: ColorPalette) = viewModelScope.launch { prefs.setPalette(p) }
    fun setTextSize(s: TextSize) = viewModelScope.launch { prefs.setTextSize(s) }
    fun toggleModule(m: Module, on: Boolean) = viewModelScope.launch {
        val cur = prefs.current().enabledModules
        prefs.setEnabledModules(if (on) cur + m else cur - m)
    }
    fun setCity(c: String) = viewModelScope.launch { prefs.setCity(c) }
    fun setPilot(c: String?) = viewModelScope.launch { prefs.setPilotCode(c) }
    fun setImpact(on: Boolean) = viewModelScope.launch {
        prefs.setImpactOptIn(on)
        if (on) scheduler.uploadSoon()
    }
    fun setBiometric(on: Boolean) = viewModelScope.launch { prefs.setBiometricLockBudget(on) }
    fun setNotify(kind: String, on: Boolean) = viewModelScope.launch {
        when (kind) {
            "limits" -> prefs.setNotifyLimits(on)
            "bills" -> prefs.setNotifyBills(on)
            "budget" -> prefs.setNotifyBudget(on)
            "meter" -> prefs.setNotifyMeterPhoto(on)
        }
    }
    fun setQuietHours(start: Int, end: Int) = viewModelScope.launch { prefs.setQuietHours(start, end) }

    fun setDemo(on: Boolean) = viewModelScope.launch {
        _busy.value = true
        runCatching { if (on) demo.load() else demo.clear() }
        _busy.value = false
    }

    fun downloadModel(wifiOnly: Boolean) = viewModelScope.launch { runCatching { translator.download(wifiOnly) } }
    fun deleteModel() = viewModelScope.launch { translator.delete() }

    suspend fun export(uri: Uri): Int = data.exportTo(uri)

    fun deleteEverything(onDone: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        data.deleteEverything()
        _busy.value = false
        onDone()
    }

    /** Returns taps still needed for developer tools, or 0 once unlocked. */
    fun tapVersion(): Int {
        versionTaps++
        val left = 7 - versionTaps
        if (left <= 0) viewModelScope.launch { prefs.setDevToolsUnlocked(true) }
        return left.coerceAtLeast(0)
    }

    fun refreshConfig() = viewModelScope.launch { config.refresh(force = true) }
}
