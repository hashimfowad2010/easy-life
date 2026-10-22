package com.easylife.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.designsystem.TintedIcon
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.SToday
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.feature.bills.BillRow
import com.easylife.app.feature.limits.MeterSummaryCard
import com.easylife.app.navigation.AddReadingRoute
import com.easylife.app.navigation.BillDetailRoute
import com.easylife.app.navigation.LimitsRoute
import com.easylife.app.navigation.MeterSetupRoute
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(nav: NavHostController, onScan: () -> Unit, vm: TodayViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    if (s.loading || busy) {
        LoadingBox()
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val greeting = when (LocalTime.now().hour) {
                in 4..11 -> SToday.greetingMorning
                in 12..16 -> SToday.greetingDay
                else -> SToday.greetingEvening
            }
            Text(greeting.t(), style = MaterialTheme.typography.headlineMedium)
            Text(Fmt.dayWithWeekday(LocalDate.now(), lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.prefs.demoMode) {
            item {
                InfoBanner(SToday.demoBanner.t(), BannerKind.INFO, icon = Icons.Rounded.Science, action = {
                    TextButton(onClick = vm::turnOffDemo) { Text(SToday.turnOffDemo.t()) }
                })
            }
        }
        if (s.cards.isEmpty() || s.cards.all { it is TodayCard.SetupMeter }) {
            item { GetStartedCard(onScan = onScan, onManual = { nav.navigate(MeterSetupRoute()) }, onDemo = vm::loadDemo) }
        }
        items(s.cards.filterNot { it is TodayCard.SetupMeter }, key = { it.key() }) { card ->
            when (card) {
                is TodayCard.Meter -> MeterSummaryCard(card.bundle.dashboard, card.bundle.meter.isDemo, onClick = { nav.navigate(LimitsRoute) })
                is TodayCard.MeterPhoto -> SimpleCard(
                    icon = { TintedIcon(Icons.Rounded.CameraAlt, EasyTheme.colors.limits.accent, EasyTheme.colors.limits.container) },
                    title = com.easylife.app.core.i18n.SNotify.meterPhotoTitle.t(),
                    body = com.easylife.app.core.i18n.SNotify.meterPhotoBody.t(card.days.toString()),
                    action = SLimits.addReading.t(),
                    onAction = { nav.navigate(AddReadingRoute(card.bundle.meter.id)) },
                )
                is TodayCard.Bill -> BillRow(card.bill, onClick = { nav.navigate(BillDetailRoute(card.bill.id)) })
                is TodayCard.Reminder -> SimpleCard(
                    icon = { TintedIcon(Icons.Rounded.Event, EasyTheme.colors.urdu.accent, EasyTheme.colors.urdu.container) },
                    title = card.reminder.title,
                    body = SToday.reminderDue.t() + " · " + Fmt.relativeDay(card.reminder.date, LocalDate.now(), lang),
                    action = null,
                    onAction = {},
                )
                is TodayCard.Review -> SimpleCard(
                    icon = { TintedIcon(Icons.Rounded.FactCheck, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer) },
                    title = SToday.reviewItems.t(card.count),
                    body = SToday.reviewBody.t(),
                    action = null,
                    onAction = {},
                )
                TodayCard.SetupMeter -> Unit
            }
        }
        if (s.summaryEn.isNotEmpty()) {
            item {
                EasyCard {
                    Text(SToday.weeklySummary.t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    (if (lang == AppLanguage.UR) s.summaryUr else s.summaryEn).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    s.summarySource?.let {
                        Spacer(Modifier.height(8.dp))
                        SourceChip(if (s.prefs.demoMode) com.easylife.app.core.ai.Provenance.DEMO else it)
                    }
                }
            }
        }
        if (s.hasAnyData && s.cards.none { it is TodayCard.Bill || (it is TodayCard.Meter && it.priority >= 70) }) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TintedIcon(Icons.Rounded.CheckCircle, EasyTheme.colors.safe, EasyTheme.colors.safe.copy(alpha = 0.15f))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(SToday.allClear.t(), style = MaterialTheme.typography.titleMedium)
                        Text(SToday.allClearBody.t(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun TodayCard.key(): String = when (this) {
    is TodayCard.Meter -> "meter-${bundle.meter.id}"
    is TodayCard.MeterPhoto -> "photo-${bundle.meter.id}"
    is TodayCard.Bill -> "bill-${bill.id}"
    is TodayCard.Reminder -> "rem-${reminder.id}"
    is TodayCard.Review -> "review"
    TodayCard.SetupMeter -> "setup"
}

@Composable
private fun GetStartedCard(onScan: () -> Unit, onManual: () -> Unit, onDemo: () -> Unit) {
    EasyCard(containerColor = EasyTheme.colors.limits.container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleIcon(Module.LIMITS)
            Spacer(Modifier.width(12.dp))
            Text(SToday.setupMeter.t(), style = MaterialTheme.typography.titleLarge, color = EasyTheme.colors.limits.onContainer)
        }
        Spacer(Modifier.height(8.dp))
        Text(SToday.setupMeterBody.t(), style = MaterialTheme.typography.bodyMedium, color = EasyTheme.colors.limits.onContainer)
        Spacer(Modifier.height(14.dp))
        BigButton(SToday.scanFirstBill.t(), onClick = onScan)
        Spacer(Modifier.height(8.dp))
        BigOutlinedButton(SLimits.enterByHand.t(), onClick = onManual)
        TextButton(onClick = onDemo, modifier = Modifier.padding(top = 4.dp)) { Text(SToday.tryDemo.t()) }
    }
}

@Composable
private fun SimpleCard(icon: @Composable () -> Unit, title: String, body: String, action: String?, onAction: () -> Unit) {
    EasyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}
