package com.easylife.app.feature.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.DemoDataSeeder
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.MeterBundle
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.DateField
import com.easylife.app.core.designsystem.DemoChip
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.NumberStyle
import com.easylife.app.core.designsystem.ProgressRing
import com.easylife.app.core.designsystem.RiskPill
import com.easylife.app.core.designsystem.StatTile
import com.easylife.app.core.designsystem.Pill
import com.easylife.app.core.designsystem.icon
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.SToday
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.limits.ConsumerCategory
import com.easylife.app.core.limits.MeterDashboard
import com.easylife.app.core.limits.Risk
import com.easylife.app.navigation.AddReadingRoute
import com.easylife.app.navigation.AppliancesRoute
import com.easylife.app.navigation.CalibrateRoute
import com.easylife.app.navigation.CustomLimitsRoute
import com.easylife.app.navigation.MeterHistoryRoute
import com.easylife.app.navigation.MeterSetupRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LimitsState(
    val loading: Boolean = true,
    val bundle: MeterBundle? = null,
    val meterNames: List<Pair<Long, String>> = emptyList(),
    val tariffUpdated: String = "",
)

@HiltViewModel
class LimitsViewModel @Inject constructor(
    private val limits: LimitsRepository,
    private val prefs: PrefsRepository,
    private val demo: DemoDataSeeder,
    private val impact: ImpactTracker,
    config: RemoteConfigRepository,
) : ViewModel() {
    val state: StateFlow<LimitsState> = combine(limits.selectedMeter, limits.meters, config.config) { b, meters, cfg ->
        LimitsState(false, b, meters.map { it.id to it.name }, cfg.tariffs.updatedOn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsState())

    fun select(id: Long) = viewModelScope.launch { prefs.setSelectedMeter(id) }

    fun loadDemo() = viewModelScope.launch { demo.load() }

    fun setExpectedReadingDate(date: LocalDate?) = viewModelScope.launch {
        val m = state.value.bundle?.meter ?: return@launch
        limits.saveMeter(m.copy(expectedReadingDate = date))
    }

    /** Counts "action after a warning" (opened tips, added a reading) for the impact evidence. */
    fun actionAfterWarning(what: String) {
        val risk = state.value.bundle?.dashboard?.status?.risk ?: return
        if (risk != Risk.SAFE) impact.log(ImpactEvents.ACTION_AFTER_WARNING, "limits", detail = what)
    }
}

@Composable
fun LimitsScreen(nav: NavHostController, onScan: () -> Unit, vm: LimitsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    Scaffold(
        topBar = {
            EasyTopBar(SLimits.title.t(), onBack = { nav.popBackStack() }, actions = {
                s.bundle?.let { b -> IconButton(onClick = { nav.navigate(MeterSetupRoute(b.meter.id)) }) { Icon(Icons.Rounded.Settings, SLimits.meterSettings.t()) } }
            })
        },
    ) { padding ->
        if (s.loading) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val bundle = s.bundle
        if (bundle == null) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyState(Module.LIMITS.icon, SLimits.noMeterTitle.t(), SLimits.noMeterBody.t())
                BigButton(SLimits.scanMyBill.t(), onClick = onScan)
                BigOutlinedButton(SLimits.enterByHand.t(), onClick = { nav.navigate(MeterSetupRoute()) })
                TextButton(onClick = { vm.loadDemo() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(SToday.tryDemo.t()) }
            }
            return@Scaffold
        }
        val d = bundle.dashboard
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s.meterNames.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.meterNames) { (id, name) -> FilterChip(selected = id == bundle.meter.id, onClick = { vm.select(id) }, label = { Text(name) }) }
                    }
                }
            }
            item { RingCard(d, bundle.meter.isDemo, lang) }
            item { StatsGrid(d, lang) }
            meterNotes(d, lang).forEach { note -> item { InfoBanner(note, BannerKind.WARNING) } }
            item {
                BigButton(SLimits.addReading.t(), icon = Icons.Rounded.CameraAlt, onClick = {
                    vm.actionAfterWarning("add_reading")
                    nav.navigate(AddReadingRoute(bundle.meter.id))
                })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate(MeterHistoryRoute(bundle.meter.id)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Insights, null); Spacer(Modifier.width(6.dp)); Text(SLimits.history.t())
                    }
                    OutlinedButton(onClick = {
                        vm.actionAfterWarning("tips")
                        nav.navigate(AppliancesRoute)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Kitchen, null); Spacer(Modifier.width(6.dp)); Text(SLimits.tips.t())
                    }
                }
            }
            item { SlipCard(d, lang, onDate = vm::setExpectedReadingDate) }
            item { BillEstimateCard(d, lang, s.tariffUpdated, onCalibrate = { nav.navigate(CalibrateRoute(bundle.meter.id)) }) }
            item { ProtectedCard(d, lang) }
            item {
                OutlinedButton(onClick = { nav.navigate(CustomLimitsRoute) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(6.dp)); Text(SLimits.customLimits.t())
                }
            }
            d.lastReading?.let { r ->
                item {
                    Text(
                        SLimits.lastReading.t(Fmt.ltr(Fmt.number(r), lang), d.lastReadingAt?.let { Fmt.dateTime(it, lang) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RingCard(d: MeterDashboard, isDemo: Boolean, lang: AppLanguage) {
    val s = d.status
    val risk = s?.risk ?: Risk.SAFE
    val color = EasyTheme.colors.risk(risk)
    EasyCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (s != null) RiskPill(risk, risk.label().t())
            Spacer(Modifier.weight(1f))
            if (isDemo) DemoChip()
        }
        Spacer(Modifier.height(8.dp))
        val used = s?.usedSoFar ?: 0.0
        val ringDescription = SLimits.ofLimit.t(Fmt.number(d.limit)).let { "${Fmt.number(used.toInt())} $it ${SLimits.unitsThisCycle.t()}" }
        ProgressRing(
            fraction = (used / d.limit).toFloat(),
            color = color,
            projectedFraction = d.projectedUnits?.let { it.toFloat() / d.limit },
            description = ringDescription,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.number(used.toInt()), style = MaterialTheme.typography.displaySmall.merge(NumberStyle), color = color, fontWeight = FontWeight.Bold)
                Text(SLimits.ofLimit.t(Fmt.ltr(Fmt.number(d.limit), lang)), style = MaterialTheme.typography.bodyMedium)
                Text(SLimits.unitsThisCycle.t(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(meterHeadline(d, lang), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatsGrid(d: MeterDashboard, lang: AppLanguage) {
    val s = d.status ?: return
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                SLimits.dailyAllowance.t(),
                s.dailyAllowance?.let { SLimits.allowanceValue.t(Fmt.ltr(Fmt.smart(it), lang)) } ?: "—",
                Modifier.weight(1f),
                accent = EasyTheme.colors.safe,
            )
            StatTile(
                SLimits.usingNow.t(),
                s.dailyRate?.let { SLimits.perDayValue.t(Fmt.ltr(Fmt.smart(it), lang)) } ?: "—",
                Modifier.weight(1f),
                accent = EasyTheme.colors.risk(s.risk),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(SLimits.projected.t(), d.projectedUnits?.let { Fmt.units(it.toDouble(), lang) } ?: "—", Modifier.weight(1f))
            StatTile(
                if (s.projectedCrossDate != null) SLimits.crossDate.t() else SLimits.daysLeft.t(),
                s.projectedCrossDate?.let { Fmt.shortDate(it, lang) } ?: d.cycleEnd?.let { Fmt.days(java.time.temporal.ChronoUnit.DAYS.between(today, it).coerceAtLeast(0), lang) } ?: "—",
                Modifier.weight(1f),
                accent = if (s.projectedCrossDate != null) EasyTheme.colors.danger else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SlipCard(d: MeterDashboard, lang: AppLanguage, onDate: (LocalDate?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    EasyCard {
        Text(SLimits.slipTitle.t(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SLimits.readingDate.t(d.cycleEnd?.let { Fmt.date(it, lang) } ?: "—"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = !editing }) { Text(SLimits.changeDate.t()) }
        }
        if (editing) DateField(SLimits.nextReadingDate.t(), d.cycleEnd, { onDate(it); editing = false })
        val firstOver = d.slip.firstOrNull { it.second > d.limit }
        if (firstOver != null && (d.projectedUnits ?: 0) <= d.limit) {
            InfoBanner(SLimits.slipWarn.t(firstOver.first, Fmt.ltr(Fmt.number(firstOver.second.toInt()), lang)), BannerKind.WARNING)
            Spacer(Modifier.height(6.dp))
        }
        d.slip.forEach { (days, units) ->
            val over = units > d.limit
            LabeledValue(
                SLimits.slipDays.t(Fmt.ltr(days.toString(), lang)),
                Fmt.units(units, lang),
                valueColor = if (over) EasyTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BillEstimateCard(d: MeterDashboard, lang: AppLanguage, tariffDate: String, onCalibrate: () -> Unit) {
    val under = d.estimateUnder ?: return
    val over = d.estimateOver ?: return
    EasyCard {
        Text(SLimits.billTitle.t(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LabeledValue(SLimits.stayUnder.t(Fmt.ltr(Fmt.number(under.units), lang)), Fmt.rupees(under.total, lang), valueColor = EasyTheme.colors.safe)
        LabeledValue(SLimits.crossTo.t(Fmt.ltr(Fmt.number(over.units), lang)), Fmt.rupees(over.total, lang), valueColor = EasyTheme.colors.danger)
        val diff = over.total - under.total
        if (diff > 0) Text(SLimits.difference.t(Fmt.rupees(diff, lang)), style = MaterialTheme.typography.titleSmall, color = EasyTheme.colors.danger)
        d.sixMonthExtra?.takeIf { it > 0 }?.let {
            Spacer(Modifier.height(8.dp))
            InfoBanner(SLimits.sixMonth.t(Fmt.rupees(it, lang)), BannerKind.WARNING)
        }
        Spacer(Modifier.height(8.dp))
        if (under.multiplier != 1.0) Pill(SLimits.calibrated.t(Fmt.number(under.multiplier, 2)), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Text(SCommon.estimated.t() + " · " + SLimits.estimateNote.t(tariffDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onCalibrate) { Text(SLimits.calibrate.t()) }
    }
}

@Composable
private fun ProtectedCard(d: MeterDashboard, lang: AppLanguage) {
    val p = d.protected
    EasyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = if (d.category == ConsumerCategory.NON_PROTECTED) EasyTheme.colors.danger else EasyTheme.colors.safe)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    d.categoryIsAssumed -> SLimits.protectedUnknown.t()
                    d.category == ConsumerCategory.NON_PROTECTED -> SLimits.protectedNo.t()
                    else -> SLimits.protectedYes.t()
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            d.categoryIsAssumed -> Text(SLimits.unknownExplain.t(), style = MaterialTheme.typography.bodyMedium)
            d.category == ConsumerCategory.NON_PROTECTED -> {
                p.crossedMonths.lastOrNull()?.let { Text(SLimits.nonProtectedExplain.t(Fmt.month(it.month, lang)), style = MaterialTheme.typography.bodyMedium) }
                p.canReturnFrom?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(SLimits.canReturn.t(Fmt.monthLong(it, lang)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                d.nextBoundary?.let { Text(SLimits.nextBoundary.t(Fmt.ltr(Fmt.number(it), lang)), style = MaterialTheme.typography.bodySmall) }
            }
            else -> Text(SLimits.protectedExplain.t(), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(SLimits.rulesChanging.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
