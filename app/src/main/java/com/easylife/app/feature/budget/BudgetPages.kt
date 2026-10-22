package com.easylife.app.feature.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.budget.Tx
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.db.CategoryRuleEntity
import com.easylife.app.core.data.db.ExclusionRuleEntity
import com.easylife.app.core.data.db.ReviewItemEntity
import com.easylife.app.core.data.db.TransactionEntity
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.toInstantLocal
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.DateField
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BudgetPagesViewModel @Inject constructor(private val repo: BudgetRepository) : ViewModel() {
    val reviews: StateFlow<List<ReviewItemEntity>> = repo.reviews.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exclusionRules: StateFlow<List<ExclusionRuleEntity>> = repo.exclusionRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categoryRules: StateFlow<List<CategoryRuleEntity>> = repo.categoryRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun context(item: ReviewItemEntity) = repo.reviewContext(item)
    fun resolve(item: ReviewItemEntity, accept: Boolean) = viewModelScope.launch { repo.resolve(item, accept) }
    fun pause(rule: ExclusionRuleEntity, paused: Boolean) = viewModelScope.launch { repo.setRulePaused(rule, paused) }
    fun deleteRule(id: Long) = viewModelScope.launch { repo.deleteExclusionRule(id) }
    fun deleteCategoryRule(id: Long) = viewModelScope.launch { repo.deleteCategoryRule(id) }
    fun setBudget(key: String, amount: Double?) = viewModelScope.launch { repo.setBudget(key, amount) }

    fun addManual(amount: Double, debit: Boolean, payee: String, category: String, date: LocalDate, notes: String, onDone: () -> Unit) = viewModelScope.launch {
        val at = (if (date == LocalDate.now()) date.atTime(LocalTime.now()) else date.atTime(12, 0)).toInstantLocal()
        repo.saveTransaction(
            Tx(0, at, amount, debit, payee.ifBlank { null }, null, "cash", null, category, false),
            origin = "MANUAL",
            source = Provenance.MANUAL.name,
            notes = notes.ifBlank { null },
            category = category,
        )
        onDone()
    }
}

@Composable
fun ReviewInboxScreen(nav: NavHostController, vm: BudgetPagesViewModel = hiltViewModel()) {
    val items by vm.reviews.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    SecureScreen()
    Scaffold(topBar = { EasyTopBar(SBudget.reviewInbox.t(), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) item { EmptyState(Icons.Rounded.Delete, SBudget.reviewEmpty.t(), "") }
            items(items, key = { it.id }) { item ->
                var ctx by remember(item.id) { mutableStateOf<Pair<TransactionEntity?, String?>?>(null) }
                LaunchedEffect(item.id) { ctx = vm.context(item) }
                val tx = ctx?.first
                val amount = tx?.let { Fmt.rupees(it.amount, lang) } ?: ""
                EasyCard {
                    val (title, body) = when (item.kind) {
                        "SELF_TRANSFER" -> SBudget.reviewSelfTransfer.t() to SBudget.reviewSelfTransferBody.t(amount)
                        "REFUND" -> SBudget.reviewRefund.t() to SBudget.reviewRefundBody.t(amount)
                        "BILL_PAYMENT_MATCH" -> SBudget.reviewBillPaid.t() to SBudget.reviewBillPaidBody.t(
                            amount,
                            tx?.let { Fmt.shortDate(it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate(), lang) } ?: "",
                            ctx?.second ?: "",
                        )
                        "SMS" -> SBudget.reviewUnknownSms.t() to (item.title + (item.payload?.let { "\n" + it } ?: ""))
                        else -> SBudget.reviewLowConfidence.t() to listOfNotNull(tx?.merchant ?: tx?.counterparty, amount).joinToString(" · ")
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        when (item.kind) {
                            "SELF_TRANSFER", "REFUND" -> {
                                TextButton(onClick = { vm.resolve(item, true) }) { Text(SBudget.yesExcludeBoth.t()) }
                                TextButton(onClick = { vm.resolve(item, false) }) { Text(SBudget.dismiss.t()) }
                            }
                            "BILL_PAYMENT_MATCH" -> {
                                TextButton(onClick = { vm.resolve(item, true) }) { Text(SBudget.yesMarkPaid.t()) }
                                TextButton(onClick = { vm.resolve(item, false) }) { Text(SBudget.dismiss.t()) }
                            }
                            "SMS" -> {
                                TextButton(onClick = {
                                    vm.resolve(item, false)
                                    nav.navigate(com.easylife.app.navigation.AddTransactionRoute())
                                }) { Text(SBudget.addAsTransaction.t()) }
                                TextButton(onClick = { vm.resolve(item, false) }) { Text(SCommon.done.t()) }
                            }
                            else -> {
                                tx?.let { t -> TextButton(onClick = { nav.navigate(com.easylife.app.navigation.TransactionDetailRoute(t.id)) }) { Text(SCommon.edit.t()) } }
                                TextButton(onClick = { vm.resolve(item, false) }) { Text(SCommon.done.t()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RulesScreen(nav: NavHostController, vm: BudgetPagesViewModel = hiltViewModel()) {
    val excl by vm.exclusionRules.collectAsStateWithLifecycle()
    val cats by vm.categoryRules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    SecureScreen()
    Scaffold(topBar = { EasyTopBar(SBudget.rules.t(), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(SBudget.rulesIntro.t(), style = MaterialTheme.typography.bodyMedium) }
            item { SectionHeader(SBudget.exclude.t()) }
            if (excl.isEmpty() && cats.isEmpty()) item { Text(SBudget.noRules.t()) }
            items(excl, key = { "e${it.id}" }) { r ->
                val label = when (r.field) {
                    "COUNTERPARTY" -> SBudget.byCounterparty.t(r.value)
                    "MERCHANT" -> SBudget.byMerchant.t(r.value)
                    "CHANNEL" -> SBudget.byChannel.t(SBudget.channel(r.value).t())
                    else -> SBudget.byAccount.t(r.value)
                }
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { if (r.paused) Text(SBudget.pause.t()) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { vm.pause(r, !r.paused) }) { Text(if (r.paused) SBudget.resume.t() else SBudget.pause.t()) }
                            IconButton(onClick = { vm.deleteRule(r.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) }
                        }
                    },
                )
            }
            item { SectionHeader(SBudget.category.t()) }
            items(cats, key = { "c${it.id}" }) { r ->
                ListItem(
                    headlineContent = { Text(r.pattern) },
                    supportingContent = { Text(categories.firstOrNull { it.key == r.category }?.name(lang) ?: r.category) },
                    trailingContent = { IconButton(onClick = { vm.deleteCategoryRule(r.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) } },
                )
            }
        }
    }
}

@Composable
fun BudgetsScreen(nav: NavHostController, vm: BudgetPagesViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    SecureScreen()
    Scaffold(topBar = { EasyTopBar(SBudget.budgets.t(), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(SBudget.noBudgets.t(), style = MaterialTheme.typography.bodyMedium) }
            items(cats.filter { it.key != "income" && it.key != "transfers" }, key = { it.key }) { c ->
                ListItem(
                    headlineContent = { Text(c.name(lang)) },
                    leadingContent = { CategoryIcon(c, 36.dp) },
                    trailingContent = { TextButton(onClick = { editing = c }) { Text(c.monthlyBudget?.let { Fmt.rupees(it, lang) } ?: SBudget.setBudget.t()) } },
                )
            }
        }
    }
    editing?.let { c ->
        var amount by remember(c.key) { mutableStateOf(c.monthlyBudget?.toLong()?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(SBudget.budgetFor.t(c.name(lang))) },
            text = { ConfirmField(SBudget.money.t(), amount, { amount = it.filter(Char::isDigit).take(8) }, numeric = true) },
            confirmButton = { TextButton(onClick = { vm.setBudget(c.key, amount.toDoubleOrNull()); editing = null }) { Text(SCommon.save.t()) } },
            dismissButton = { TextButton(onClick = { vm.setBudget(c.key, null); editing = null }) { Text(SCommon.delete.t()) } },
        )
    }
}

@Composable
fun AddTransactionScreen(nav: NavHostController, vm: BudgetPagesViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var amount by remember { mutableStateOf("") }
    var debit by remember { mutableStateOf(true) }
    var payee by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    SecureScreen()
    Scaffold(topBar = { EasyTopBar(SBudget.addManually.t(), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(debit, { debit = true }, { Text(SBudget.moneyOut.t()) })
                FilterChip(!debit, { debit = false; if (category == "other") category = "income" }, { Text(SBudget.moneyIn.t()) })
            }
            ConfirmField(SBudget.money.t(), amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, numeric = true)
            ConfirmField(SBudget.payee.t(), payee, { payee = it.take(40) })
            Text(SBudget.category.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { c -> FilterChip(category == c.key, { category = c.key }, { Text(c.name(lang)) }) }
            }
            DateField(SCommon.date.t(), date, { date = it ?: LocalDate.now() })
            ConfirmField(SBudget.notes.t(), notes, { notes = it.take(200) }, singleLine = false)
            BigButton(SCommon.save.t(), enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                vm.addManual(amount.toDouble(), debit, payee, category, date, notes) { nav.popBackStack() }
            })
        }
    }
}
