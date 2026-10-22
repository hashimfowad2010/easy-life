package com.easylife.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.title
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.LanguageManager
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SOnboarding
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.notify.NotificationChannels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

val PakistanCities = listOf("Lahore", "Karachi", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta", "Gujranwala", "Sialkot", "Hyderabad", "Other")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val channels: NotificationChannels,
) : ViewModel() {
    fun chooseLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            prefs.setLanguage(lang)
            channels.ensureCreated(lang)
            LanguageManager.apply(lang)
        }
    }

    fun finish(modules: Set<Module>, city: String, pilot: String?, impactOptIn: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setEnabledModules(modules)
            prefs.setCity(city)
            prefs.setPilotCode(pilot)
            prefs.setImpactOptIn(impactOptIn)
            prefs.setOnboardingDone(true)
            onDone()
        }
    }
}

/** Language -> modules -> city + pilot code -> anonymous impact opt-in. No permissions are asked here. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var modules by rememberSaveable { mutableStateOf(Module.entries.map { it.name }.toSet()) }
    var city by rememberSaveable { mutableStateOf("Lahore") }
    var pilot by rememberSaveable { mutableStateOf("") }
    val total = 4

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(SOnboarding.step.t(step + 1, total), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> LanguageStep { lang ->
                    step = 1
                    vm.chooseLanguage(lang)
                }
                1 -> ModulesStep(modules) { modules = it }
                2 -> CityStep(city, { city = it }, pilot, { pilot = it })
                else -> ImpactStep()
            }
        }
        when (step) {
            0 -> Unit
            3 -> {
                BigButton(SOnboarding.impactYes.t(), onClick = {
                    vm.finish(modules.mapNotNull { n -> Module.entries.firstOrNull { it.name == n } }.toSet(), city, pilot, true, onDone)
                })
                Spacer(Modifier.height(8.dp))
                BigOutlinedButton(SOnboarding.impactNo.t(), onClick = {
                    vm.finish(modules.mapNotNull { n -> Module.entries.firstOrNull { it.name == n } }.toSet(), city, pilot, false, onDone)
                })
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { step -= 1 }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text(SCommon.back.t()) }
                BigButton(SCommon.next.t(), onClick = { step += 1 }, modifier = Modifier.weight(1f), enabled = step != 1 || modules.isNotEmpty())
            }
        }
    }
}

@Composable
private fun LanguageStep(onChoose: (AppLanguage) -> Unit) {
    Text(SOnboarding.welcome.t(), style = MaterialTheme.typography.headlineMedium)
    Text(SOnboarding.tagline.t(), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    // Both buttons are shown in their own language, so anyone can pick.
    Text(SOnboarding.chooseLanguageBoth.t(), style = MaterialTheme.typography.titleMedium)
    BigButton("اردو", onClick = { onChoose(AppLanguage.UR) })
    BigOutlinedButton("English", onClick = { onChoose(AppLanguage.EN) })
    Text(SOnboarding.languageNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(SOnboarding.privacyPromise.t(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModulesStep(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(SOnboarding.pickModules.t(), style = MaterialTheme.typography.headlineSmall)
    Text(SOnboarding.pickModulesNote.t(), style = MaterialTheme.typography.bodyMedium)
    Module.entries.forEach { m ->
        val on = m.name in selected
        EasyCard(
            modifier = Modifier.toggleable(value = on, role = Role.Checkbox, onValueChange = { onChange(if (it) selected + m.name else selected - m.name) }),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleIcon(m)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title.t(), style = MaterialTheme.typography.titleMedium)
                    Text(m.description().t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = on, onCheckedChange = null)
            }
        }
    }
}

fun Module.description() = when (this) {
    Module.LIMITS -> com.easylife.app.core.i18n.SNav.limitsDesc
    Module.BILLS -> com.easylife.app.core.i18n.SNav.billsDesc
    Module.BUDGET -> com.easylife.app.core.i18n.SNav.budgetDesc
    Module.URDU -> com.easylife.app.core.i18n.SNav.urduDesc
    Module.SHOPPING -> com.easylife.app.core.i18n.SNav.shoppingDesc
}

@Composable
private fun CityStep(city: String, onCity: (String) -> Unit, pilot: String, onPilot: (String) -> Unit) {
    Text(SOnboarding.city.t(), style = MaterialTheme.typography.headlineSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PakistanCities.forEach { c -> FilterChip(selected = city == c, onClick = { onCity(c) }, label = { Text(c) }) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = pilot,
        onValueChange = { onPilot(it.take(20)) },
        label = { Text(SOnboarding.pilotCode.t()) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(SOnboarding.pilotCodeHelp.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ImpactStep() {
    Text(SOnboarding.impactTitle.t(), style = MaterialTheme.typography.headlineSmall)
    EasyCard { Text(SOnboarding.impactBody.t(), style = MaterialTheme.typography.bodyLarge) }
    Text(SOnboarding.languageNote.t(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start, fontSize = 13.sp)
}
