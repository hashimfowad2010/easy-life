package com.easylife.app.feature.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.data.db.MeterEntity
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.DateField
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.t
import com.easylife.app.core.limits.BillingMode
import com.easylife.app.core.limits.ConsumerCategory
import com.easylife.app.core.notify.WorkScheduler
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val electricCompanies = listOf("LESCO", "IESCO", "FESCO", "GEPCO", "MEPCO", "PESCO", "HESCO", "SEPCO", "QESCO", "K-Electric")

@HiltViewModel
class MeterSetupViewModel @Inject constructor(
    private val limits: LimitsRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    private val _meter = MutableStateFlow<MeterEntity?>(null)
    val meter = _meter.asStateFlow()

    fun load(id: Long) = viewModelScope.launch {
        _meter.value = (if (id > 0) limits.meter(id) else null) ?: MeterEntity(name = "Home")
    }

    fun save(m: MeterEntity, onDone: () -> Unit) = viewModelScope.launch {
        limits.saveMeter(m)
        scheduler.checkSoon()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        _meter.value?.takeIf { it.id > 0 }?.let { limits.deleteMeter(it) }
        onDone()
    }

    fun calibrate(meterId: Long, units: Int, payable: Double): Double? {
        val meter = _meter.value ?: return null
        val category = ConsumerCategory.entries.firstOrNull { it.name == meter.categoryOverride } ?: ConsumerCategory.PROTECTED
        val m = limits.tariff().calibrate(units, category, payable, BillingMode.entries.firstOrNull { it.name == meter.billingModeOverride })
        if (m != null) viewModelScope.launch { limits.setCalibration(meterId, m) }
        return m
    }

    fun clearCalibration(meterId: Long) = viewModelScope.launch { limits.setCalibration(meterId, null) }
}

@Composable
fun MeterSetupScreen(meterId: Long, onBack: () -> Unit, vm: MeterSetupViewModel = hiltViewModel()) {
    LaunchedEffect(meterId) { vm.load(meterId) }
    val loaded by vm.meter.collectAsStateWithLifecycle()
    Scaffold(topBar = { EasyTopBar(SLimits.setupTitle.t(), onBack = onBack) }) { padding ->
        val m0 = loaded ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        var m by remember(m0) { mutableStateOf(m0) }
        var limitText by remember(m0.id) { mutableStateOf(m0.limitUnits.toString()) }
        var bufferText by remember(m0.id) { mutableStateOf(m0.safetyBuffer.toString()) }
        var readingText by remember(m0.id) { mutableStateOf(m0.cycleStartReading?.let { Fmt.number(it).replace(",", "") } ?: "") }
        var digitsText by remember(m0.id) { mutableStateOf(m0.meterDigits.toString()) }
        var photoText by remember(m0.id) { mutableStateOf(m0.photoReminderDays.toString()) }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConfirmField(SLimits.meterName.t(), m.name, { m = m.copy(name = it.take(30)) })
            Text(SLimits.provider.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                electricCompanies.forEach { c -> FilterChip(selected = m.provider == c, onClick = { m = m.copy(provider = c) }, label = { Text(c) }) }
            }
            SectionHeader(SLimits.lastBillPresentReading.t())
            DateField(SLimits.lastBillReadingDate.t(), m.cycleStartDate, { d -> m = m.copy(cycleStartDate = d, expectedReadingDate = m.expectedReadingDate ?: d?.plusMonths(1)) })
            ConfirmField(SLimits.lastBillPresentReading.t(), readingText, { readingText = it.filter(Char::isDigit).take(7) }, numeric = true)
            DateField(SLimits.nextReadingDate.t(), m.expectedReadingDate, { m = m.copy(expectedReadingDate = it) })
            SectionHeader(SLimits.limitUnits.t())
            ConfirmField(SLimits.limitUnits.t(), limitText, { limitText = it.filter(Char::isDigit).take(5) }, numeric = true)
            ConfirmField(SLimits.safetyBuffer.t(), bufferText, { bufferText = it.filter(Char::isDigit).take(3) }, numeric = true, supporting = SLimits.safetyBufferHelp.t())
            Text(SLimits.consumerStatus.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO" to SLimits.statusAuto, "PROTECTED" to SLimits.statusProtected, "NON_PROTECTED" to SLimits.statusNonProtected, "LIFELINE" to SLimits.statusLifeline)
                    .forEach { (k, label) -> FilterChip(selected = m.categoryOverride == k, onClick = { m = m.copy(categoryOverride = k) }, label = { Text(label.t()) }) }
            }
            Text(SLimits.billingMode.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null to SLimits.billingAuto, "INCREMENTAL" to SLimits.billingIncremental, "SINGLE_SLAB" to SLimits.billingSingle)
                    .forEach { (k, label) -> FilterChip(selected = m.billingModeOverride == k, onClick = { m = m.copy(billingModeOverride = k) }, label = { Text(label.t()) }) }
            }
            ConfirmField(SLimits.meterDigits.t(), digitsText, { digitsText = it.filter(Char::isDigit).take(1) }, numeric = true)
            ConfirmField(SLimits.photoEvery.t(), photoText, { photoText = it.filter(Char::isDigit).take(2) }, numeric = true)
            BigButton(SCommon.save.t(), onClick = {
                vm.save(
                    m.copy(
                        limitUnits = limitText.toIntOrNull()?.coerceIn(1, 100_000) ?: 200,
                        safetyBuffer = bufferText.toIntOrNull()?.coerceIn(0, 1000) ?: 5,
                        cycleStartReading = readingText.toDoubleOrNull(),
                        meterDigits = digitsText.toIntOrNull()?.coerceIn(4, 8) ?: 5,
                        photoReminderDays = photoText.toIntOrNull()?.coerceIn(1, 30) ?: 3,
                    ),
                    onBack,
                )
            })
            if (m0.id > 0) TextButton(onClick = { vm.delete(onBack) }) { Text(SLimits.deleteMeter.t(), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun CalibrateScreen(meterId: Long, onBack: () -> Unit, vm: MeterSetupViewModel = hiltViewModel()) {
    LaunchedEffect(meterId) { vm.load(meterId) }
    var units by remember { mutableStateOf("") }
    var payable by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var bad by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val doneText = SLimits.calibrationDone
    val lang = com.easylife.app.core.i18n.currentLanguage()
    Scaffold(topBar = { EasyTopBar(SLimits.calibrateTitle.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(SLimits.calibrateIntro.t(), style = MaterialTheme.typography.bodyMedium)
            ConfirmField(SLimits.billUnits.t(), units, { units = it.filter(Char::isDigit).take(5) }, numeric = true)
            ConfirmField(SLimits.billPayable.t(), payable, { payable = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, numeric = true)
            if (bad) InfoBanner(SLimits.calibrationBad.t(), BannerKind.WARNING)
            result?.let { InfoBanner(it, BannerKind.SUCCESS) }
            BigButton(SCommon.save.t(), onClick = {
                val m = vm.calibrate(meterId, units.toIntOrNull() ?: 0, payable.toDoubleOrNull() ?: 0.0)
                bad = m == null
                result = m?.let { doneText.format(lang, Fmt.number(it, 2)) }
                if (m != null) scope.launch { snackbar.showSnackbar(result!!) }
            })
            TextButton(onClick = { vm.clearCalibration(meterId); result = null }) { Text(SLimits.clearCalibration.t()) }
        }
    }
}

