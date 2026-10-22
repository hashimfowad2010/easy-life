package com.easylife.app.feature.budget

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.budget.BudgetInsights
import com.easylife.app.core.budget.MonthSummary
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.toTx
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.HorizontalBar
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.StatTile
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.limits.LimitStatus
import com.easylife.app.core.limits.Risk
import com.easylife.app.navigation.AddTransactionRoute
import com.easylife.app.navigation.BankSetupRoute
import com.easylife.app.navigation.BudgetsRoute
import com.easylife.app.navigation.LocalSnackbar
import com.easylife.app.navigation.ReviewInboxRoute
import com.easylife.app.navigation.RulesRoute
import com.easylife.app.navigation.TransactionsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class BudgetHome(
    val loading: Boolean = true,
    val summary: MonthSummary? = null,
    val budgets: List<Pair<CategoryEntity, LimitStatus>> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val reviewCount: Int = 0,
    val accounts: Int = 0,
    val txCount: Int = 0,
    val lockEnabled: Boolean = false,
)

@HiltViewModel
class BudgetViewModel @Inject constructor(private val repo: BudgetRepository, prefs: PrefsRepository) : ViewModel() {
    private val month = YearMonth.now()

    val state: StateFlow<BudgetHome> = combine(
        repo.month(month),
        repo.month(month.minusMonths(1)),
        repo.categories,
        combine(repo.reviews, repo.accounts) { r, a -> r.size to a.size },
        prefs.prefs,
    ) { cur, prev, cats, (reviews, accounts), p ->
        val txs = cur.map { it.toTx() }
        val now = LocalDateTime.now()
        BudgetHome(
            loading = false,
            summary = BudgetInsights.summarize(txs, prev.map { it.toTx() }),
            budgets = cats.filter { (it.monthlyBudget ?: 0.0) > 0 }.map { c -> c to BudgetInsights.budgetStatus(c.monthlyBudget!!, c.key, month, txs, now) },
            categories = cats.associateBy { it.key },
            reviewCount = reviews,
            accounts = accounts,
            txCount = cur.size,
            lockEnabled = p.biometricLockBudget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetHome())

    suspend fun importCsv(text: String): Int = repo.importCsv(text)
}

fun budgetLine(status: LimitStatus, category: String, lang: AppLanguage): String = when {
    status.risk == Risk.CROSSED -> SBudget.budgetOver.format(lang, Fmt.rupees(status.usedSoFar - status.limit, lang))
    status.projectedCrossDate != null -> SBudget.budgetHeadline.format(lang, category, Fmt.shortDate(status.projectedCrossDate, lang), Fmt.rupees(status.dailyAllowance ?: 0.0, lang))
    else -> SBudget.budgetOk.format(
        lang,
        Fmt.rupees(status.remaining, lang),
        Fmt.rupees(status.dailyAllowance ?: 0.0, lang),
        Fmt.ltr(status.daysRemaining.toInt().toString(), lang),
    )
}

@Composable
fun BudgetScreen(nav: NavHostController, vm: BudgetViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val importedTxt = SBudget.imported
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            val n = vm.importCsv(text)
            snackbar.showSnackbar(importedTxt.format(lang, n))
        }
    }
    BudgetLockGate(enabled = s.lockEnabled) {
        Scaffold(
            topBar = { EasyTopBar(SBudget.title.t(), onBack = { nav.popBackStack() }) },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { nav.navigate(AddTransactionRoute()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(SBudget.addManually.t()) })
            },
        ) { padding ->
            if (s.loading) {
                LoadingBox(modifier = Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.accounts == 0 && s.txCount == 0) {
                    item {
                        EasyCard(containerColor = EasyTheme.colors.budget.container) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ModuleIcon(Module.BUDGET)
                                Spacer(Modifier.width(12.dp))
                                Text(SBudget.setupTitle.t(), style = MaterialTheme.typography.titleLarge, color = EasyTheme.colors.budget.onContainer)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(SBudget.setupBody.t(), style = MaterialTheme.typography.bodyMedium, color = EasyTheme.colors.budget.onContainer)
                            Spacer(Modifier.height(12.dp))
                            BigButton(SBudget.addBank.t(), onClick = { nav.navigate(BankSetupRoute) })
                            Spacer(Modifier.height(8.dp))
                            BigOutlinedButton(SBudget.importCsv.t(), icon = Icons.Rounded.UploadFile, onClick = { csvLauncher.launch(arrayOf("text/*", "application/csv", "text/comma-separated-values")) })
                        }
                    }
                }
                s.summary?.let { sum ->
                    item {
                        Text(SBudget.thisMonth.t() + " · " + Fmt.monthLong(YearMonth.now(), lang), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(SBudget.spent.t(), Fmt.rupees(sum.spent, lang), Modifier.weight(1f), accent = MaterialTheme.colorScheme.onSurface)
                            StatTile(SBudget.received.t(), Fmt.rupees(sum.received, lang), Modifier.weight(1f), accent = EasyTheme.colors.safe)
                        }
                        sum.changeVsPrevious?.let { ch ->
                            Text(
                                SBudget.vsLastMonth.t((if (ch >= 0) "+" else "") + Fmt.percent(ch, lang)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ch > 0.1) EasyTheme.colors.watch else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (s.reviewCount > 0) {
                    item {
                        EasyCard(onClick = { nav.navigate(ReviewInboxRoute) }, containerColor = EasyTheme.colors.watch.copy(alpha = 0.12f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FactCheck, null, tint = EasyTheme.colors.watch)
                                Spacer(Modifier.width(10.dp))
                                Text(SBudget.review.t() + " (${s.reviewCount})", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                item {
                    SectionHeader(SBudget.budgets.t(), action = { TextButton(onClick = { nav.navigate(BudgetsRoute) }) { Text(SBudget.setBudget.t()) } })
                    if (s.budgets.isEmpty()) {
                        Text(SBudget.noBudgets.t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                s.budgets.forEach { (c, status) ->
                    item(key = "budget-${c.key}") {
                        EasyCard {
                            HorizontalBar(
                                label = c.name(lang),
                                valueText = SBudget.budgetLine.t(Fmt.rupees(status.usedSoFar, lang), Fmt.rupees(status.limit, lang)),
                                fraction = status.fractionUsed.toFloat(),
                                color = EasyTheme.colors.risk(status.risk),
                            )
                            Text(budgetLine(status, c.name(lang), lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                s.summary?.takeIf { it.byCategory.isNotEmpty() }?.let { sum ->
                    item {
                        SectionHeader(SBudget.byCategory.t())
                        EasyCard {
                            val max = sum.byCategory.maxOf { it.second }
                            sum.byCategory.forEach { (key, amount) ->
                                val c = s.categories[key]
                                HorizontalBar(c?.name(lang) ?: key, Fmt.rupees(amount, lang), (amount / max).toFloat(), c?.colorValue() ?: MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        SectionHeader(SBudget.topMerchants.t())
                        EasyCard { sum.topMerchants.forEach { (name, amount) -> LabeledValue(name, Fmt.rupees(amount, lang)) } }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.navigate(TransactionsRoute) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Rounded.List, null); Spacer(Modifier.width(6.dp)); Text(SBudget.transactions.t())
                        }
                        OutlinedButton(onClick = { nav.navigate(BankSetupRoute) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AccountBalance, null); Spacer(Modifier.width(6.dp)); Text(SBudget.banksTitle.t())
                        }
                        OutlinedButton(onClick = { nav.navigate(RulesRoute) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Rule, null); Spacer(Modifier.width(6.dp)); Text(SBudget.rules.t())
                        }
                        OutlinedButton(onClick = { csvLauncher.launch(arrayOf("text/*", "application/csv", "text/comma-separated-values")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(SBudget.importCsv.t())
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxWidth() = this.then(androidx.compose.foundation.layout.fillMaxWidth())
