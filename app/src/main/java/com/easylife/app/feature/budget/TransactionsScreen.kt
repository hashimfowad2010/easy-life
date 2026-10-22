package com.easylife.app.feature.budget

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.budget.RuleField
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.db.TransactionEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.navigation.LocalSnackbar
import com.easylife.app.navigation.TransactionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(private val repo: BudgetRepository, prefs: PrefsRepository) : ViewModel() {
    val transactions: StateFlow<List<TransactionEntity>?> = repo.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<Map<String, CategoryEntity>> = repo.categories.map { l -> l.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val lockEnabled: StateFlow<Boolean> = prefs.prefs.map { it.biometricLockBudget }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setExcluded(ids: List<Long>, excluded: Boolean) = viewModelScope.launch { repo.setExcluded(ids, excluded) }

    // ---- detail
    private val id = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<TransactionEntity?> = id.flatMapLatest { if (it == null) flowOf(null) else repo.transaction(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(txId: Long) {
        id.value = txId
    }

    fun setCategory(tx: TransactionEntity, category: String, always: Boolean) = viewModelScope.launch { repo.setCategory(tx, category, always) }
    fun saveNotes(tx: TransactionEntity, notes: String) = viewModelScope.launch { repo.updateTransaction(tx.copy(notes = notes.ifBlank { null })) }
    fun excludeSimilar(field: RuleField, value: String, applyToPast: Boolean, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(repo.createExclusionRule(field, value, applyToPast))
    }
    fun delete(tx: TransactionEntity, onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteTransaction(tx.id)
        onDone()
    }
}

@Composable
fun TransactionsScreen(nav: NavHostController, vm: TransactionsViewModel = hiltViewModel()) {
    val list by vm.transactions.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val lock by vm.lockEnabled.collectAsStateWithLifecycle()
    var excludedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val excludedMsg = SBudget.excludedSnack.t()
    val includedMsg = SBudget.includedSnack.t()
    val undo = SCommon.undo.t()

    fun toggle(tx: TransactionEntity) {
        val nowExcluded = !tx.excluded
        vm.setExcluded(listOf(tx.id), nowExcluded)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(if (nowExcluded) excludedMsg else includedMsg, actionLabel = undo, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.setExcluded(listOf(tx.id), !nowExcluded)
        }
    }

    BudgetLockGate(enabled = lock) {
        Scaffold(
            topBar = {
                if (selected.isEmpty()) {
                    EasyTopBar(SBudget.transactions.t(), onBack = { nav.popBackStack() })
                } else {
                    EasyTopBar(SBudget.selected.t(selected.size), onBack = null, actions = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, SCommon.cancel.t()) }
                    })
                }
            },
            bottomBar = {
                if (selected.isNotEmpty()) {
                    BottomAppBar {
                        TextButton(onClick = { vm.setExcluded(selected.toList(), true); selected = emptySet() }) { Text(SBudget.excludeSelected.t()) }
                        TextButton(onClick = { vm.setExcluded(selected.toList(), false); selected = emptySet() }) { Text(SBudget.includeSelected.t()) }
                    }
                }
            },
        ) { padding ->
            val all = list ?: run {
                LoadingBox(modifier = Modifier.padding(padding))
                return@Scaffold
            }
            val shown = if (excludedOnly) all.filter { it.excluded } else all
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!excludedOnly, { excludedOnly = false }, { Text(SBudget.all.t()) })
                        FilterChip(excludedOnly, { excludedOnly = true }, { Text(SBudget.excludedFilter.t() + " (${all.count { it.excluded }})") })
                    }
                }
                if (shown.isEmpty()) item { Text(SCommon.nothingHereYet.t(), modifier = Modifier.padding(16.dp)) }
                items(shown, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        category = cats[tx.category],
                        selected = tx.id in selected,
                        selecting = selected.isNotEmpty(),
                        onClick = {
                            if (selected.isNotEmpty()) {
                                selected = if (tx.id in selected) selected - tx.id else selected + tx.id
                            } else {
                                nav.navigate(TransactionDetailRoute(tx.id))
                            }
                        },
                        onLongClick = { selected = selected + tx.id },
                        onToggleExclude = { toggle(tx) },
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionDetailScreen(id: Long, nav: NavHostController, vm: TransactionsViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val tx by vm.detail.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val lock by vm.lockEnabled.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    var askAlways by remember { mutableStateOf<String?>(null) }
    var similar by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val excludedMsg = SBudget.excludedSnack.t()

    BudgetLockGate(enabled = lock) {
        Scaffold(
            topBar = {
                EasyTopBar(tx?.merchant ?: tx?.counterparty ?: SBudget.transactions.t(), onBack = { nav.popBackStack() }, actions = {
                    tx?.let { t -> IconButton(onClick = { vm.delete(t) { nav.popBackStack() } }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) } }
                })
            },
        ) { padding ->
            val t = tx ?: run {
                LoadingBox(modifier = Modifier.padding(padding))
                return@Scaffold
            }
            var notes by remember(t.id) { mutableStateOf(t.notes ?: "") }
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EasyCard {
                    LabeledValue(SBudget.money.t().substringBefore(" ("), (if (t.direction == "DEBIT") "−" else "+") + Fmt.rupees(t.amount, lang))
                    LabeledValue(SCommon.date.t(), Fmt.dateTime(t.timestamp.atZone(ZoneId.systemDefault()).toLocalDateTime(), lang))
                    t.merchant?.let { LabeledValue(SBudget.payee.t(), it) }
                    t.counterparty?.let { LabeledValue(SBudget.payee.t(), it) }
                    LabeledValue(SBudget.byChannel.t("").trim().trimEnd(':'), SBudget.channel(t.channel).t())
                    t.accountLabel?.let { LabeledValue(SBudget.banksTitle.t(), it) }
                    t.balanceAfter?.let { LabeledValue(SBudget.balance.t(), Fmt.rupees(it, lang)) }
                    Spacer(Modifier.height(6.dp))
                    SourceChip(Provenance.parse(t.source))
                }
                if (t.excluded) {
                    BigButton(SBudget.include.t(), onClick = { vm.setExcluded(listOf(t.id), false) })
                } else {
                    BigOutlinedButton(SBudget.exclude.t(), onClick = {
                        vm.setExcluded(listOf(t.id), true)
                        scope.launch { snackbar.showSnackbar(excludedMsg) }
                    })
                }
                TextButton(onClick = { similar = true }) { Text(SBudget.excludeSimilar.t()) }
                SectionHeader(SBudget.category.t())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cats.values.sortedBy { it.sortOrder }.forEach { c ->
                        FilterChip(
                            selected = t.category == c.key,
                            onClick = {
                                if (c.key != t.category) {
                                    if (t.merchant != null || t.counterparty != null) askAlways = c.key else vm.setCategory(t, c.key, false)
                                }
                            },
                            label = { Text(c.name(lang)) },
                        )
                    }
                }
                ConfirmField(SBudget.notes.t(), notes, { notes = it.take(200) }, singleLine = false)
                TextButton(onClick = { vm.saveNotes(t, notes) }) { Text(SCommon.save.t()) }
            }

            askAlways?.let { key ->
                val name = t.merchant ?: t.counterparty ?: ""
                AlertDialog(
                    onDismissRequest = { askAlways = null },
                    title = { Text(SBudget.alwaysCategory.t(name, cats[key]?.name(lang) ?: key)) },
                    confirmButton = { TextButton(onClick = { vm.setCategory(t, key, true); askAlways = null }) { Text(SBudget.always.t()) } },
                    dismissButton = { TextButton(onClick = { vm.setCategory(t, key, false); askAlways = null }) { Text(SBudget.justThisOne.t()) } },
                )
            }
            if (similar) ExcludeSimilarDialog(t, onDismiss = { similar = false }) { field, value, past ->
                vm.excludeSimilar(field, value, past) { scope.launch { snackbar.showSnackbar(excludedMsg) } }
                similar = false
            }
        }
    }
}

@Composable
private fun ExcludeSimilarDialog(t: TransactionEntity, onDismiss: () -> Unit, onConfirm: (RuleField, String, Boolean) -> Unit) {
    val options = buildList {
        t.counterparty?.let { add(Triple(RuleField.COUNTERPARTY, it, SBudget.byCounterparty)) }
        t.merchant?.let { add(Triple(RuleField.MERCHANT, it, SBudget.byMerchant)) }
        add(Triple(RuleField.CHANNEL, t.channel.lowercase(), SBudget.byChannel))
        t.accountId?.let { add(Triple(RuleField.ACCOUNT, it.toString(), SBudget.byAccount)) }
    }
    var choice by remember { mutableStateOf(0) }
    var step by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) SBudget.similarTitle.t() else SBudget.applyPast.t()) },
        text = {
            if (step == 0) {
                Column {
                    options.forEachIndexed { i, (field, value, label) ->
                        val shown = if (field == RuleField.CHANNEL) SBudget.channel(value).t() else if (field == RuleField.ACCOUNT) (t.accountLabel ?: value) else value
                        FilterChip(choice == i, { choice = i }, { Text(label.t(shown)) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                TextButton(onClick = { step = 1 }) { Text(SCommon.next.t()) }
            } else {
                TextButton(onClick = { val o = options[choice]; onConfirm(o.first, o.second, true) }) { Text(SBudget.applyPastYes.t()) }
            }
        },
        dismissButton = {
            if (step == 0) {
                TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) }
            } else {
                TextButton(onClick = { val o = options[choice]; onConfirm(o.first, o.second, false) }) { Text(SBudget.applyFutureOnly.t()) }
            }
        },
    )
}
