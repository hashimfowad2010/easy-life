package com.easylife.app.feature.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.data.db.BillEntity
import com.easylife.app.core.data.db.MeterDao
import com.easylife.app.core.data.db.UsageMonthEntity
import com.easylife.app.core.data.repo.BillState
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.data.repo.lateFee
import com.easylife.app.core.data.repo.state
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BarChart
import com.easylife.app.core.designsystem.BarEntry
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBills
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.navigation.LimitsRoute
import com.easylife.app.navigation.route
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
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(private val repo: BillsRepository, private val meterDao: MeterDao) : ViewModel() {
    private val id = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Pair<BillEntity?, List<UsageMonthEntity>>?> = id.flatMapLatest<Long?, Pair<BillEntity?, List<UsageMonthEntity>>?> { billId ->
        if (billId == null) {
            flowOf(null)
        } else {
            repo.observe(billId).flatMapLatest { bill ->
                val meterId = bill?.meterId
                if (meterId == null) {
                    flowOf(bill to emptyList<UsageMonthEntity>())
                } else {
                    combine(flowOf(bill), meterDao.observeUsage(meterId)) { b, u -> b to u }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(billId: Long) {
        id.value = billId
    }

    fun togglePaid(bill: BillEntity) = viewModelScope.launch { if (bill.status == "PAID") repo.markUnpaid(bill.id) else repo.markPaid(bill.id) }
    fun delete(bill: BillEntity, onDone: () -> Unit) = viewModelScope.launch {
        repo.delete(bill.id)
        onDone()
    }
}

@Composable
fun BillDetailScreen(id: Long, nav: NavHostController, vm: BillDetailViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var confirmDelete by remember { mutableStateOf(false) }
    val bill = s?.first
    Scaffold(
        topBar = {
            EasyTopBar(bill?.provider ?: SBills.title.t(), onBack = { nav.popBackStack() }, actions = {
                if (bill != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, SBills.deleteBill.t()) }
            })
        },
    ) { padding ->
        if (bill == null) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BillRow(bill, onClick = {})
            SourceChip(Provenance.parse(bill.source))
            if (bill.uncertainFields.isNotEmpty() && bill.source != Provenance.MANUAL.name) InfoBanner(SCommon.checkHighlighted.t(), BannerKind.INFO)
            EasyCard {
                LabeledValue(SBills.billType.t(), SBills.type(bill.billType).t())
                bill.billingMonth?.let { m -> runCatching { YearMonth.parse(m) }.getOrNull()?.let { LabeledValue(SBills.billingMonth.t().substringBefore(" ("), Fmt.monthLong(it, lang)) } }
                bill.issueDate?.let { LabeledValue(SBills.issueDate.t(), Fmt.date(it, lang)) }
                bill.dueDate?.let { LabeledValue(SBills.dueDate.t(), Fmt.date(it, lang)) }
                bill.readingDate?.let { LabeledValue(SBills.readingDate.t(), Fmt.date(it, lang)) }
                bill.amountWithinDue?.let { LabeledValue(SBills.amountWithin.t().substringBefore(" ("), Fmt.rupees(it, lang)) }
                bill.amountAfterDue?.let { LabeledValue(SBills.amountAfter.t().substringBefore(" ("), Fmt.rupees(it, lang)) }
                bill.lateFee?.let { LabeledValue(SBills.lateFee.t("").trim(), Fmt.rupees(it, lang), valueColor = EasyTheme.colors.watch) }
                bill.unitsConsumed?.let { LabeledValue(SBills.units.t(), Fmt.units(it.toDouble(), lang)) }
                bill.previousReading?.let { LabeledValue(SBills.previousReading.t(), Fmt.number(it)) }
                bill.presentReading?.let { LabeledValue(SBills.presentReading.t(), Fmt.number(it)) }
                bill.referenceNo?.let { LabeledValue(SBills.referenceNo.t().substringBefore(" ("), Fmt.ltr(it, lang)) }
            }
            val usage = s?.second.orEmpty()
            if (usage.isNotEmpty()) {
                EasyCard {
                    Text(SBills.historyTitle.t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        usage.takeLast(12).map { BarEntry(Fmt.monthShort(YearMonth.parse(it.month), lang), it.units.toFloat()) },
                        limitLine = 200f,
                        color = EasyTheme.colors.bills.accent,
                        showLabelsEvery = 2,
                        description = SBills.historyTitle.t(),
                    )
                }
            }
            Text(SBills.reminders.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton(
                if (bill.state() == BillState.PAID) SBills.markUnpaid.t() else SBills.markPaid.t(),
                icon = Icons.Rounded.CheckCircle,
                onClick = { vm.togglePaid(bill) },
            )
            if (bill.billType == "electricity") BigOutlinedButton(SBills.openLimits.t(), icon = Icons.Rounded.Bolt, onClick = { nav.navigate(LimitsRoute) })
            if (!bill.ocrText.isNullOrBlank()) BigOutlinedButton(SCommon.showInUrdu.t(), icon = Icons.Rounded.Translate, onClick = { nav.navigate(Module.URDU.route()) })
        }
    }
    if (confirmDelete && bill != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(SBills.deleteConfirm.t()) },
            confirmButton = { TextButton(onClick = { vm.delete(bill) { nav.popBackStack() } }) { Text(SCommon.delete.t()) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(SCommon.cancel.t()) } },
        )
    }
}
