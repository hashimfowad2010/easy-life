package com.easylife.app.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.SettingsSwitchRow
import com.easylife.app.core.designsystem.SmartAiChip
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SAiConsent
import com.easylife.app.core.i18n.SBills
import com.easylife.app.core.i18n.SPrivacy
import com.easylife.app.core.i18n.SSettings
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SmartAiViewModel @Inject constructor(private val prefs: PrefsRepository, config: RemoteConfigRepository) : ViewModel() {
    val state = combine(prefs.prefs, config.config) { p, c -> p to c }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSmartAi(on: Boolean) = viewModelScope.launch { prefs.setSmartAiEnabled(on) }

    fun consentGemini(given: Boolean) = viewModelScope.launch {
        prefs.setGeminiConsent(given)
        if (given) prefs.setSmartAiEnabled(true)
    }

    fun consentClaude(given: Boolean) = viewModelScope.launch {
        prefs.setClaudeConsent(given)
        if (given) prefs.setSmartAiEnabled(true)
    }
}

/**
 * Smart AI: explains in plain words what is sent and to whom, and asks before the first cloud call (spec rule 5).
 * When no cloud provider is switched on in Remote Config, it says so honestly.
 */
@Composable
fun SmartAiScreen(onBack: () -> Unit, vm: SmartAiViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    Scaffold(topBar = { EasyTopBar(SAiConsent.title.t(), onBack = onBack) }) { padding ->
        val (p, c) = s ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmartAiChip()
            Text(SAiConsent.intro.t(), style = MaterialTheme.typography.bodyLarge)
            EasyCard {
                Text(SAiConsent.whatIsSent.t(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(SAiConsent.whatIsSentBody.t(), style = MaterialTheme.typography.bodyMedium)
            }
            Text(SAiConsent.alwaysLabelled.t(), style = MaterialTheme.typography.bodyMedium)
            Text(SAiConsent.offlineAlways.t(), style = MaterialTheme.typography.bodyMedium)

            if (!c.geminiEnabled && !c.claudeEnabled) {
                InfoBanner(SAiConsent.notAvailableYet.t(), BannerKind.INFO, icon = Icons.Rounded.AutoAwesome)
            }
            if (c.claudeEnabled) {
                EasyCard {
                    Text(SAiConsent.claudeTitle.t(), style = MaterialTheme.typography.titleMedium)
                    Text(SAiConsent.claudeBody.t(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val at = p.claudeConsentAt
                    if (at == null) {
                        BigButton(SAiConsent.agree.t(), onClick = { vm.consentClaude(true) })
                    } else {
                        Text(SAiConsent.consentGiven.t(Fmt.date(at.atZone(ZoneId.systemDefault()).toLocalDate(), lang)), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.consentClaude(false) }) { Text(SAiConsent.withdraw.t()) }
                    }
                }
            }
            if (c.geminiEnabled) {
                EasyCard {
                    Text(SAiConsent.geminiTitle.t(), style = MaterialTheme.typography.titleMedium)
                    Text(SAiConsent.geminiBody.t(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val at = p.geminiConsentAt
                    if (at == null) {
                        BigOutlinedButton(SAiConsent.agree.t(), onClick = { vm.consentGemini(true) })
                    } else {
                        Text(SAiConsent.consentGiven.t(Fmt.date(at.atZone(ZoneId.systemDefault()).toLocalDate(), lang)), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.consentGemini(false) }) { Text(SAiConsent.withdraw.t()) }
                    }
                }
            }
            SettingsSwitchRow(
                SSettings.smartAi.t(),
                subtitle = if (p.smartAiEnabled) SAiConsent.statusOn.t() else SAiConsent.statusOff.t(),
                checked = p.smartAiEnabled,
                onCheckedChange = vm::setSmartAi,
                enabled = (c.geminiEnabled && p.geminiConsentAt != null) || (c.claudeEnabled && p.claudeConsentAt != null),
            )
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(topBar = { EasyTopBar(SPrivacy.title.t(), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SPrivacy.points.forEach { (title, body) ->
                EasyCard {
                    Text(title.t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(body.t(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(SPrivacy.notAdvice.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel(), notifierVm: NotifierCheckViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = s.prefs
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifierVm.refresh() }
    val allowed by notifierVm.allowed.collectAsStateWithLifecycle()
    Scaffold(topBar = { EasyTopBar(SSettings.notifications.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(8.dp)) {
            if (!allowed && Build.VERSION.SDK_INT >= 33) {
                InfoBanner(SBills.notificationsAsk.t(), BannerKind.WARNING, modifier = Modifier.padding(8.dp), action = {
                    TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(SBills.allow.t()) }
                })
            }
            SettingsSwitchRow(SSettings.notifyLimits.t(), checked = p.notifyLimits, onCheckedChange = { vm.setNotify("limits", it) })
            SettingsSwitchRow(SSettings.notifyBills.t(), checked = p.notifyBills, onCheckedChange = { vm.setNotify("bills", it) })
            SettingsSwitchRow(SSettings.notifyBudget.t(), checked = p.notifyBudget, onCheckedChange = { vm.setNotify("budget", it) })
            SettingsSwitchRow(SSettings.notifyMeter.t(), checked = p.notifyMeterPhoto, onCheckedChange = { vm.setNotify("meter", it) })
            Text(SSettings.quietHours.t(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            Text(
                SSettings.quietHoursValue.t("${p.quietStartHour}:00", "${p.quietEndHour}:00"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(21 to 7, 22 to 8, 23 to 7, 0 to 0).forEach { (a, b) ->
                    FilterChip(
                        selected = p.quietStartHour == a && p.quietEndHour == b,
                        onClick = { vm.setQuietHours(a, b) },
                        label = { Text(if (a == b) com.easylife.app.core.i18n.SCommon.off.t() else "$a:00–$b:00") },
                    )
                }
            }
        }
    }
}

@HiltViewModel
class NotifierCheckViewModel @Inject constructor(private val notifier: Notifier) : ViewModel() {
    val allowed = kotlinx.coroutines.flow.MutableStateFlow(notifier.canNotify())
    fun refresh() {
        allowed.value = notifier.canNotify()
    }
}
