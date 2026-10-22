package com.easylife.app.feature.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.data.db.ApplianceEntity
import com.easylife.app.core.data.db.CustomLimitEntity
import com.easylife.app.core.data.repo.CustomLimitBundle
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.MeterBundle
import com.easylife.app.core.data.repo.toLocal
import com.easylife.app.core.designsystem.BarChart
import com.easylife.app.core.designsystem.BarEntry
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.HorizontalBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LimitBar
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.RiskPill
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SAppliance
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.limits.Appliance
import com.easylife.app.core.limits.ApplianceAdvisor
import com.easylife.app.core.limits.ApplianceType
import com.easylife.app.core.limits.LimitMode
import com.easylife.app.core.limits.LimitStatus
import com.easylife.app.core.limits.MeterDashboardCalculator
import com.easylife.app.core.limits.Risk
import com.easylife.app.navigation.CustomLimitRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// ------------------------------------------------------------------------------------------------ History

@HiltViewModel
class MeterHistoryViewModel @Inject constructor(private val limits: LimitsRepository) : ViewModel() {
    private val meterId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val bundle: StateFlow<MeterBundle?> = meterId.flatMapLatest { id -> if (id == null) flowOf(null) else limits.observeBundle(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        meterId.value = id
    }

    fun deleteReading(id: Long) = viewModelScope.launch { limits.deleteReading(id) }
}

@Composable
fun MeterHistoryScreen(meterId: Long, onBack: () -> Unit, vm: MeterHistoryViewModel = hiltViewModel()) {
    LaunchedEffect(meterId) { vm.load(meterId) }
    val b by vm.bundle.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Scaffold(topBar = { EasyTopBar(SLimits.history.t(), onBack = onBack) }) { padding ->
        val bundle = b ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val limit = bundle.meter.limitUnits
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val baseline = bundle.meter.cycleStartDate?.let { d -> bundle.meter.cycleStartReading?.let { d.atTime(12, 0) to it } }
            val daily = MeterDashboardCalculator.dailyUsage(
                bundle.readings.map { MeterDashboardCalculator.Reading(it.takenAt.toLocal(), it.reading, it.id) },
                baseline,
            )
            item {
                EasyCard {
                    Text(SLimits.dailyUse.t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (daily.isEmpty()) {
                        Text(SLimits.needReading.t(), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val allowance = bundle.dashboard.status?.dailyAllowance?.toFloat()
                        BarChart(
                            entries = daily.takeLast(14).map { (d, v) -> BarEntry(Fmt.shortDate(d, lang), v.toFloat()) },
                            limitLine = allowance,
                            color = EasyTheme.colors.limits.accent,
                            showLabelsEvery = 2,
                            description = SLimits.dailyUse.t(),
                        )
                    }
                }
            }
            item {
                EasyCard {
                    Text(SLimits.monthlyUse.t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (bundle.usage.isEmpty()) {
                        Text(SLimits.noHistory.t(), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        BarChart(
                            entries = bundle.usage.takeLast(12).map { u -> BarEntry(Fmt.monthShort(YearMonth.parse(u.month), lang), u.units.toFloat()) },
                            limitLine = limit.toFloat(),
                            color = EasyTheme.colors.safe,
                            showLabelsEvery = 2,
                            description = SLimits.monthlyUse.t(),
                        )
                        Spacer(Modifier.height(8.dp))
                        val over = bundle.usage.filter { it.units > limit }
                        Text(SLimits.monthsOver.t(), style = MaterialTheme.typography.titleSmall)
                        if (over.isEmpty()) {
                            Text(SLimits.noMonthsOver.t(), style = MaterialTheme.typography.bodyMedium, color = EasyTheme.colors.safe)
                        } else {
                            over.forEach { LabeledValue(Fmt.month(YearMonth.parse(it.month), lang), Fmt.units(it.units.toDouble(), lang), valueColor = EasyTheme.colors.danger) }
                        }
                    }
                }
            }
            item { SectionHeader(SLimits.readings.t()) }
            items(bundle.readings.sortedByDescending { it.takenAt }, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(Fmt.number(r.reading), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(Fmt.dateTime(r.takenAt.toLocal(), lang)) },
                    leadingContent = { SourceChip(Provenance.parse(r.source)) },
                    trailingContent = {
                        IconButton(onClick = { confirmDelete = r.id }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) }
                    },
                )
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(SLimits.deleteReading.t()) },
            confirmButton = { TextButton(onClick = { vm.deleteReading(id); confirmDelete = null }) { Text(SCommon.delete.t()) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(SCommon.cancel.t()) } },
        )
    }
}

// ------------------------------------------------------------------------------------------------ Appliances

@HiltViewModel
class AppliancesViewModel @Inject constructor(private val limits: LimitsRepository) : ViewModel() {
    val state: StateFlow<Pair<List<ApplianceEntity>, MeterBundle?>> = combine(limits.appliances, limits.selectedMeter) { a, b -> a to b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ApplianceEntity>() to null)

    fun save(a: ApplianceEntity) = viewModelScope.launch { limits.saveAppliance(a) }
    fun delete(id: Long) = viewModelScope.launch { limits.deleteAppliance(id) }
}

@Composable
fun AppliancesScreen(onBack: () -> Unit, vm: AppliancesViewModel = hiltViewModel()) {
    val (list, bundle) = vm.state.collectAsStateWithLifecycle().value
    val lang = currentLanguage()
    var editing by remember { mutableStateOf<ApplianceEntity?>(null) }
    val appliances = list.map { Appliance(it.id, ApplianceType.entries.firstOrNull { t -> t.name == it.type } ?: ApplianceType.OTHER, it.label, it.watts, it.quantity, it.hoursPerDay) }
    val advice = ApplianceAdvisor.advise(appliances, bundle?.dashboard?.status?.dailyRate, bundle?.dashboard?.status?.dailyAllowance)
    Scaffold(
        topBar = { EasyTopBar(SLimits.appliances.t(), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ApplianceEntity(type = ApplianceType.CEILING_FAN.name, label = "", watts = ApplianceType.CEILING_FAN.defaultWatts, quantity = 1, hoursPerDay = ApplianceType.CEILING_FAN.defaultHours) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(SLimits.addAppliance.t()) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(SLimits.appliancesIntro.t(), style = MaterialTheme.typography.bodyMedium) }
            if (appliances.isNotEmpty()) {
                item {
                    EasyCard {
                        Text(SLimits.estimatedPerDay.t(Fmt.ltr(Fmt.smart(advice.estimatedUnitsPerDay), lang)), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val max = advice.breakdown.maxOfOrNull { it.second } ?: 1.0
                        advice.breakdown.forEach { (a, perDay) ->
                            HorizontalBar(
                                label = a.label.ifBlank { SAppliance.name(a.type.name).t() },
                                valueText = SLimits.unitsDay.t(Fmt.smart(perDay)),
                                fraction = (perDay / max).toFloat(),
                                color = EasyTheme.colors.limits.accent,
                            )
                        }
                    }
                }
                item {
                    EasyCard(containerColor = EasyTheme.colors.safe.copy(alpha = 0.10f)) {
                        Text(
                            if (advice.neededSavingPerDay > 0) SLimits.needSaving.t(Fmt.ltr(Fmt.smart(advice.neededSavingPerDay), lang)) else SLimits.easyCuts.t(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        advice.suggestions.forEach { c ->
                            Text(
                                "• " + SLimits.cutLine.t(
                                    c.label.ifBlank { SAppliance.name(c.type.name).t() },
                                    Fmt.hours(c.reduceHours, lang),
                                    Fmt.ltr(Fmt.smart(c.unitsSavedPerDay), lang),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (appliances.any { it.type.name.startsWith("AC") }) {
                            Text("• " + SLimits.inverterTip.t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            items(list, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.label.ifBlank { SAppliance.name(a.type).t() }) },
                    supportingContent = { Text("${a.quantity} × ${a.watts} W · ${Fmt.hours(a.hoursPerDay, lang)} · ${SCommon.approximate.t()}") },
                    trailingContent = { IconButton(onClick = { vm.delete(a.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) } },
                    modifier = Modifier.padding(0.dp),
                )
                TextButton(onClick = { editing = a }) { Text(SCommon.edit.t()) }
            }
        }
    }
    editing?.let { a -> ApplianceDialog(a, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null }) }
}

@Composable
private fun ApplianceDialog(initial: ApplianceEntity, onDismiss: () -> Unit, onSave: (ApplianceEntity) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var watts by remember { mutableStateOf(initial.watts.toString()) }
    var qty by remember { mutableStateOf(initial.quantity.toString()) }
    var hours by remember { mutableStateOf(Fmt.smart(initial.hoursPerDay)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SLimits.addAppliance.t()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApplianceType.entries.forEach { t ->
                        FilterChip(selected = type == t.name, onClick = {
                            type = t.name
                            watts = t.defaultWatts.toString()
                            hours = Fmt.smart(t.defaultHours)
                        }, label = { Text(SAppliance.name(t.name).t()) })
                    }
                }
                ConfirmField(SLimits.watts.t(), watts, { watts = it.filter(Char::isDigit).take(5) }, numeric = true)
                ConfirmField(SLimits.quantity.t(), qty, { qty = it.filter(Char::isDigit).take(2) }, numeric = true)
                ConfirmField(SLimits.hoursPerDay.t(), hours, { hours = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, numeric = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        type = type,
                        label = "",
                        watts = watts.toIntOrNull()?.coerceIn(1, 10_000) ?: 100,
                        quantity = qty.toIntOrNull()?.coerceIn(1, 50) ?: 1,
                        hoursPerDay = hours.toDoubleOrNull()?.coerceIn(0.0, 24.0) ?: 1.0,
                    ),
                )
            }) { Text(SCommon.save.t()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) } },
    )
}

// ------------------------------------------------------------------------------------------------ Custom limits

@HiltViewModel
class CustomLimitsViewModel @Inject constructor(private val limits: LimitsRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val list: StateFlow<List<CustomLimitBundle>> = combine(limits.customLimits, refresh) { _, _ -> limits.allCustomStatuses() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        refresh.value++
    }

    private val id = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<CustomLimitBundle?> = id.flatMapLatest { if (it == null) flowOf(null) else limits.observeCustom(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(limitId: Long) {
        id.value = limitId
    }

    fun create(name: String, unit: String, limit: Double, days: Int, mode: LimitMode) = viewModelScope.launch {
        limits.saveCustom(CustomLimitEntity(name = name, unit = unit, limitValue = limit, mode = mode.name, periodStart = LocalDate.now(), periodDays = days))
    }

    fun addEntry(value: Double) = viewModelScope.launch { id.value?.let { limits.addCustomEntry(it, value, null) } }
    fun deleteEntry(entryId: Long) = viewModelScope.launch { limits.deleteCustomEntry(entryId) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        id.value?.let { limits.deleteCustom(it) }
        onDone()
    }
}

@Composable
private fun LimitStatusView(status: LimitStatus, unit: String, lang: AppLanguage) {
    val color = EasyTheme.colors.risk(status.risk)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${Fmt.smart(status.usedSoFar)} / ${Fmt.smart(status.limit)} $unit", style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        RiskPill(status.risk, status.risk.label().t())
    }
    Spacer(Modifier.height(6.dp))
    LimitBar((status.usedSoFar / status.limit).toFloat(), color, projected = status.projectedAtPeriodEnd?.let { (it / status.limit).toFloat() })
    status.dailyAllowance?.let {
        Spacer(Modifier.height(4.dp))
        Text("${SLimits.dailyAllowance.t()}: ${Fmt.ltr(Fmt.smart(it), lang)} $unit", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CustomLimitsScreen(nav: NavHostController, vm: CustomLimitsViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val lang = currentLanguage()
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { EasyTopBar(SLimits.customTitle.t(), onBack = { nav.popBackStack() }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(SLimits.newLimit.t()) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(SLimits.customIntro.t(), style = MaterialTheme.typography.bodyMedium) }
            if (list.isEmpty()) item { EmptyState(Icons.Rounded.Tune, SLimits.customTitle.t(), SLimits.customIntro.t()) }
            items(list, key = { it.limit.id }) { b ->
                EasyCard(onClick = { nav.navigate(CustomLimitRoute(b.limit.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.limit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (b.limit.isDemo) com.easylife.app.core.designsystem.DemoChip()
                    }
                    Spacer(Modifier.height(6.dp))
                    LimitStatusView(b.status, b.limit.unit, lang)
                }
            }
        }
    }
    if (creating) CreateLimitDialog(onDismiss = { creating = false }, onCreate = { n, u, l, d, m -> vm.create(n, u, l, d, m); creating = false })
}

@Composable
private fun CreateLimitDialog(onDismiss: () -> Unit, onCreate: (String, String, Double, Int, LimitMode) -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }
    var mode by remember { mutableStateOf(LimitMode.ADDITIVE_AMOUNT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SLimits.newLimit.t()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfirmField(SLimits.limitName.t(), name, { name = it.take(30) })
                ConfirmField(SLimits.unitName.t(), unit, { unit = it.take(10) })
                ConfirmField(SLimits.limitValue.t(), limit, { limit = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, numeric = true)
                ConfirmField(SLimits.periodDays.t(), days, { days = it.filter(Char::isDigit).take(3) }, numeric = true)
                FilterChip(selected = mode == LimitMode.ADDITIVE_AMOUNT, onClick = { mode = LimitMode.ADDITIVE_AMOUNT }, label = { Text(SLimits.modeAmount.t()) })
                FilterChip(selected = mode == LimitMode.CUMULATIVE_READING, onClick = { mode = LimitMode.CUMULATIVE_READING }, label = { Text(SLimits.modeReading.t()) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (limit.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { onCreate(name, unit, limit.toDouble(), days.toIntOrNull()?.coerceIn(1, 366) ?: 30, mode) },
            ) { Text(SCommon.save.t()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) } },
    )
}

@Composable
fun CustomLimitScreen(id: Long, onBack: () -> Unit, vm: CustomLimitsViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val b by vm.detail.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            EasyTopBar(b?.limit?.name ?: "", onBack = onBack, actions = {
                IconButton(onClick = { vm.delete(onBack) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) }
            })
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(SLimits.addEntry.t()) }) },
    ) { padding ->
        val bundle = b ?: run {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { EasyCard { LimitStatusView(bundle.status, bundle.limit.unit, lang) } }
            if (bundle.status.risk != Risk.SAFE) {
                item { InfoBanner(bundle.status.risk.label().t(), com.easylife.app.core.designsystem.BannerKind.WARNING) }
            }
            items(bundle.entries.sortedByDescending { it.at }, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text("${Fmt.smart(e.value)} ${bundle.limit.unit}") },
                    supportingContent = { Text(Fmt.dateTime(e.at.toLocal(), lang)) },
                    trailingContent = { IconButton(onClick = { vm.deleteEntry(e.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) } },
                )
            }
        }
    }
    if (adding) {
        var v by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(SLimits.addEntry.t()) },
            text = { ConfirmField(SLimits.entryValue.t(), v, { v = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, numeric = true) },
            confirmButton = { TextButton(enabled = v.toDoubleOrNull() != null, onClick = { vm.addEntry(v.toDouble()); adding = false }) { Text(SCommon.save.t()) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(SCommon.cancel.t()) } },
        )
    }
}
