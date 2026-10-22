package com.easylife.app.feature.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.data.db.BillEntity
import com.easylife.app.core.data.repo.BillState
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.data.repo.lateFee
import com.easylife.app.core.data.repo.state
import com.easylife.app.core.designsystem.BarChart
import com.easylife.app.core.designsystem.BarEntry
import com.easylife.app.core.designsystem.DemoChip
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.NumberStyle
import com.easylife.app.core.designsystem.Pill
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBills
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.navigation.BillConfirmRoute
import com.easylife.app.navigation.BillDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(private val repo: BillsRepository) : ViewModel() {
    val bills: StateFlow<List<BillEntity>?> = repo.bills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun markPaid(id: Long) = viewModelScope.launch { repo.markPaid(id) }
}

private enum class BillTab { UPCOMING, OVERDUE, PAID }

@Composable
fun BillsScreen(nav: NavHostController, onScan: () -> Unit, vm: BillsViewModel = hiltViewModel()) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var tab by rememberSaveable { mutableStateOf(BillTab.UPCOMING) }
    Scaffold(
        topBar = { EasyTopBar(SBills.title.t(), onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScan, icon = { Icon(Icons.Rounded.DocumentScanner, null) }, text = { Text(SBills.scanBill.t()) })
        },
    ) { padding ->
        val all = bills ?: return@Scaffold
        val today = LocalDate.now()
        val shown = all.filter {
            when (tab) {
                BillTab.UPCOMING -> it.state(today) == BillState.UPCOMING || it.state(today) == BillState.DUE_SOON
                BillTab.OVERDUE -> it.state(today) == BillState.OVERDUE
                BillTab.PAID -> it.state(today) == BillState.PAID
            }
        }.sortedBy { it.dueDate }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == BillTab.UPCOMING, { tab = BillTab.UPCOMING }, { Text(SBills.upcoming.t()) })
                    FilterChip(tab == BillTab.OVERDUE, { tab = BillTab.OVERDUE }, { Text(SBills.overdue.t() + " (${all.count { it.state(today) == BillState.OVERDUE }})") })
                    FilterChip(tab == BillTab.PAID, { tab = BillTab.PAID }, { Text(SBills.paid.t()) })
                }
            }
            if (all.isEmpty()) {
                item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, SBills.empty.t(), SBills.emptyBody.t()) }
            }
            items(shown, key = { it.id }) { b -> BillRow(b, onClick = { nav.navigate(BillDetailRoute(b.id)) }, onMarkPaid = { vm.markPaid(b.id) }) }
            item {
                OutlinedButton(onClick = { nav.navigate(BillConfirmRoute(draftId = null, kind = null)) }) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text(SBills.addManually.t())
                }
            }
            if (all.size >= 2) {
                item {
                    // Monthly totals across bill types, last 6 months.
                    val months = (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) }
                    val totals = months.map { m -> m to all.filter { it.billingMonth == m.toString() }.sumOf { it.amountWithinDue ?: 0.0 } }
                    if (totals.any { it.second > 0 }) {
                        EasyCard {
                            Text(SBills.monthlyTotals.t(), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            BarChart(totals.map { (m, v) -> BarEntry(Fmt.monthShort(m, lang), v.toFloat()) }, color = EasyTheme.colors.bills.accent, description = SBills.monthlyTotals.t())
                        }
                    }
                }
            }
        }
    }
}

/** One bill: company, amount, due date (relative), late fee, quick "Mark paid". */
@Composable
fun BillRow(bill: BillEntity, onClick: () -> Unit, onMarkPaid: (() -> Unit)? = null) {
    val lang = currentLanguage()
    val today = LocalDate.now()
    val state = bill.state(today)
    val ext = EasyTheme.colors
    val color = when (state) {
        BillState.OVERDUE -> ext.crossed
        BillState.DUE_SOON -> ext.watch
        BillState.PAID -> ext.safe
        BillState.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    EasyCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleIcon(Module.BILLS, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bill.provider ?: SBills.type(bill.billType).t(), style = MaterialTheme.typography.titleMedium)
                val due = bill.dueDate
                Text(
                    when {
                        state == BillState.PAID -> SBills.paidOn.t(bill.paidAt?.let { Fmt.shortDate(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), lang) } ?: "")
                        due == null -> SBills.type(bill.billType).t()
                        state == BillState.OVERDUE -> SBills.overdueBy.t(Fmt.shortDate(due, lang))
                        else -> SBills.dueRelative.t(Fmt.relativeDay(due, today, lang))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                bill.amountWithinDue?.let { Text(Fmt.rupees(it, lang), style = MaterialTheme.typography.titleMedium.merge(NumberStyle)) }
                if (bill.isDemo) DemoChip()
            }
        }
        val fee = bill.lateFee
        if (state != BillState.PAID && fee != null && bill.dueDate != null) {
            Spacer(Modifier.height(8.dp))
            Pill(SBills.payToSave.t(Fmt.shortDate(bill.dueDate, lang), Fmt.rupees(fee, lang)), ext.watch.copy(alpha = 0.14f), ext.watch)
        }
        if (onMarkPaid != null && state != BillState.PAID) {
            androidx.compose.material3.TextButton(onClick = onMarkPaid, modifier = Modifier.align(Alignment.End)) { Text(SBills.markPaid.t()) }
        }
    }
}
