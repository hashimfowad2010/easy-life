package com.easylife.app.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.easylife.app.BuildConfig
import com.easylife.app.core.ai.ModelState
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SettingsNavRow
import com.easylife.app.core.designsystem.SettingsSwitchRow
import com.easylife.app.core.designsystem.ThemeMode
import com.easylife.app.core.designsystem.UrduFont
import com.easylife.app.core.designsystem.title
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.SAiConsent
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SSettings
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.feature.onboarding.PakistanCities
import com.easylife.app.navigation.DevToolsRoute
import com.easylife.app.navigation.FeedbackRoute
import com.easylife.app.navigation.ImpactReportRoute
import com.easylife.app.navigation.LocalSnackbar
import com.easylife.app.navigation.NotificationSettingsRoute
import com.easylife.app.navigation.PrivacyRoute
import com.easylife.app.navigation.SmartAiRoute
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val p = s.prefs
    val lang = currentLanguage()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var editCity by remember { mutableStateOf(false) }
    val exportedText = SSettings.exported.t()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val rows = runCatching { vm.export(uri) }.getOrNull()
            snackbar.showSnackbar(if (rows != null) "$exportedText ($rows)" else "—")
        }
    }
    if (busy) {
        LoadingBox()
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 16.dp)) {
        Text(SSettings.title.t(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))

        SectionHeader(SSettings.language.t(), Modifier.padding(horizontal = 8.dp))
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == AppLanguage.UR, onClick = { vm.setLanguage(AppLanguage.UR) }, label = { Text("اردو") })
            FilterChip(selected = lang == AppLanguage.EN, onClick = { vm.setLanguage(AppLanguage.EN) }, label = { Text("English") })
        }
        if (lang == AppLanguage.UR) {
            SectionHeader(SSettings.fontStyle.t(), Modifier.padding(horizontal = 8.dp))
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(p.urduFont == UrduFont.NASTALIQ, { vm.setFont(UrduFont.NASTALIQ) }, { Text(SSettings.nastaliq.t()) })
                FilterChip(p.urduFont == UrduFont.NASKH, { vm.setFont(UrduFont.NASKH) }, { Text(SSettings.naskh.t()) })
            }
        }
        SectionHeader(SSettings.theme.t(), Modifier.padding(horizontal = 8.dp))
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(p.themeMode == ThemeMode.SYSTEM, { vm.setTheme(ThemeMode.SYSTEM) }, { Text(SSettings.themeSystem.t()) })
            FilterChip(p.themeMode == ThemeMode.LIGHT, { vm.setTheme(ThemeMode.LIGHT) }, { Text(SSettings.themeLight.t()) })
            FilterChip(p.themeMode == ThemeMode.DARK, { vm.setTheme(ThemeMode.DARK) }, { Text(SSettings.themeDark.t()) })
        }

        SectionHeader(SSettings.modules.t(), Modifier.padding(horizontal = 8.dp))
        Module.entries.forEach { m ->
            ListItem(
                headlineContent = { Text(m.title.t()) },
                leadingContent = { ModuleIcon(m, 36.dp) },
                trailingContent = { androidx.compose.material3.Switch(checked = m in p.enabledModules, onCheckedChange = { vm.toggleModule(m, it) }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SettingsNavRow(
            SSettings.smartAi.t(),
            subtitle = if (p.smartAiEnabled) SAiConsent.statusOn.t() else SAiConsent.statusOff.t(),
            icon = Icons.Rounded.AutoAwesome,
            onClick = { nav.navigate(SmartAiRoute) },
        )
        TranslationModelRow(s.model, onDownload = vm::downloadModel, onDelete = vm::deleteModel)
        SettingsNavRow(SSettings.notifications.t(), icon = Icons.Rounded.Notifications, onClick = { nav.navigate(NotificationSettingsRoute) })
        SettingsSwitchRow(SSettings.budgetLock.t(), checked = p.biometricLockBudget, onCheckedChange = vm::setBiometric, icon = Icons.Rounded.Fingerprint)
        SettingsNavRow(SSettings.city.t(), trailing = p.city + (p.pilotCode?.let { " · $it" } ?: ""), icon = Icons.Rounded.LocationCity, onClick = { editCity = true })

        SectionHeader(SSettings.dataSection.t(), Modifier.padding(horizontal = 8.dp))
        SettingsNavRow(SSettings.exportData.t(), subtitle = SSettings.exportDataNote.t(), icon = Icons.Rounded.UploadFile, onClick = { exportLauncher.launch("easy-life-export.json") })
        SettingsNavRow(SSettings.deleteData.t(), icon = Icons.Rounded.DeleteForever, onClick = { confirmDelete = true })
        SettingsNavRow(SSettings.privacy.t(), icon = Icons.Rounded.Policy, onClick = { nav.navigate(PrivacyRoute) })

        SectionHeader(SSettings.impactSection.t(), Modifier.padding(horizontal = 8.dp))
        SettingsSwitchRow(SSettings.impactOptIn.t(), checked = p.impactOptIn, onCheckedChange = vm::setImpact, icon = Icons.Rounded.VolunteerActivism)
        SettingsSwitchRow(SSettings.demoMode.t(), subtitle = SSettings.demoModeNote.t(), checked = p.demoMode, onCheckedChange = vm::setDemo, icon = Icons.Rounded.Science)
        SettingsNavRow(SSettings.impactReport.t(), icon = Icons.Rounded.Assessment, onClick = { nav.navigate(ImpactReportRoute) })
        SettingsNavRow(SSettings.feedback.t(), icon = Icons.Rounded.RateReview, onClick = { nav.navigate(FeedbackRoute) })

        SectionHeader(SSettings.about.t(), Modifier.padding(horizontal = 8.dp))
        val versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val tapsText = SSettings.tapsToDev
        val unlocked = SSettings.devToolsUnlocked.t()
        ListItem(
            headlineContent = { Text(SSettings.version.t()) },
            supportingContent = { Text(versionText) },
            leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.Info, null) },
            modifier = Modifier.clickable {
                val left = vm.tapVersion()
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(if (left == 0) unlocked else if (left <= 3) tapsText.format(lang, left) else return@launch)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        s.config?.let { c ->
            ListItem(
                headlineContent = { Text(SSettings.tariffData.t()) },
                supportingContent = { Text(SSettings.tariffUpdated.t(c.tariffs.updatedOn) + " · " + c.tariffs.version + " · " + c.source) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (!s.firebase) {
            Text(SSettings.firebaseOff.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (p.devToolsUnlocked) SettingsNavRow(SSettings.devTools.t(), icon = Icons.Rounded.Build, onClick = { nav.navigate(DevToolsRoute) })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(SSettings.deleteConfirmTitle.t()) },
            text = { Text(SSettings.deleteConfirmBody.t()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteEverything { (context as? Activity)?.recreate() }
                }) { Text(SCommon.delete.t(), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(SCommon.cancel.t()) } },
        )
    }
    if (editCity) {
        var city by remember { mutableStateOf(p.city) }
        var pilot by remember { mutableStateOf(p.pilotCode ?: "") }
        AlertDialog(
            onDismissRequest = { editCity = false },
            title = { Text(SSettings.city.t()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PakistanCities.forEach { c -> FilterChip(city == c, { city = c }, { Text(c) }) }
                    }
                    ConfirmField(SSettings.pilot.t(), pilot, { pilot = it.take(20) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCity(city)
                    vm.setPilot(pilot)
                    editCity = false
                }) { Text(SCommon.save.t()) }
            },
            dismissButton = { TextButton(onClick = { editCity = false }) { Text(SCommon.cancel.t()) } },
        )
    }
}

@Composable
private fun TranslationModelRow(state: ModelState, onDownload: (Boolean) -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(SSettings.translationModel.t()) },
        supportingContent = {
            Column {
                Text(
                    when (state) {
                        ModelState.READY -> SSettings.modelReady.t()
                        ModelState.DOWNLOADING -> SSettings.modelDownloading.t()
                        ModelState.FAILED -> SSettings.modelFailed.t()
                        else -> SSettings.modelMissing.t()
                    },
                )
                if (state == ModelState.DOWNLOADING) LinearProgressIndicator(Modifier.padding(top = 6.dp))
                when (state) {
                    ModelState.READY -> TextButton(onClick = onDelete) { Text(SSettings.deleteModel.t()) }
                    ModelState.DOWNLOADING -> Unit
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onDownload(true) }) { Text(SSettings.download.t()) }
                        TextButton(onClick = { onDownload(false) }) { Text(SSettings.downloadAnyNetwork.t()) }
                    }
                }
            }
        },
        leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.Translate, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
