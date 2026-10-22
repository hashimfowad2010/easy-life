package com.easylife.app.feature.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BillState
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.state
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.title
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SNav
import com.easylife.app.core.i18n.Txt
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.feature.onboarding.description
import com.easylife.app.navigation.route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

private val billsDue = Txt("%1\$s bills due", "%1\$s بل باقی")
private val noBillsDue = Txt("No bills due", "کوئی بل باقی نہیں")
private val setUp = Txt("Tap to set up", "ترتیب دینے کے لیے دبائیں")
private val shopReady = Txt("Lists, prices, budget check", "فہرست، قیمتیں، بجٹ")
private val spent = Txt("%1\$s spent this month", "اس مہینے %1\$s خرچ")
private val urduReady = Txt("Translate, scan, listen", "ترجمہ، اسکین، سنیں")

data class ModuleTile(val module: Module, val enabled: Boolean, val status: (AppLanguage) -> String)

@HiltViewModel
class ModulesViewModel @Inject constructor(
    prefs: PrefsRepository,
    limits: LimitsRepository,
    bills: BillsRepository,
    budget: com.easylife.app.core.data.repo.BudgetRepository,
) : ViewModel() {
    val tiles: StateFlow<List<ModuleTile>> = combine(prefs.prefs, limits.selectedMeter, bills.unpaid, budget.month(java.time.YearMonth.now())) { p, meter, unpaid, txs ->
        val spentThisMonth = txs.filter { it.direction == "DEBIT" && !it.excluded }.sumOf { it.amount }
        Module.entries.map { m ->
            val status: (AppLanguage) -> String = when (m) {
                Module.LIMITS -> { lang ->
                    meter?.dashboard?.status?.let { "${Fmt.fraction(it.usedSoFar, meter.dashboard.limit.toDouble(), lang)} ${SCommon.units.get(lang)}" } ?: setUp.get(lang)
                }
                Module.BILLS -> { lang ->
                    val due = unpaid.count { it.state(LocalDate.now()) != BillState.PAID }
                    if (due > 0) billsDue.format(lang, due) else noBillsDue.get(lang)
                }
                Module.BUDGET -> { lang ->
                    if (spentThisMonth > 0) spent.format(lang, Fmt.rupees(spentThisMonth, lang)) else setUp.get(lang)
                }
                Module.URDU -> { lang -> urduReady.get(lang) }
                Module.SHOPPING -> { lang -> shopReady.get(lang) }
            }
            ModuleTile(m, m in p.enabledModules, status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ModulesScreen(nav: NavHostController, vm: ModulesViewModel = hiltViewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(SNav.modules.t(), style = MaterialTheme.typography.headlineMedium)
        }
        items(tiles, key = { it.module.name }) { tile ->
            val colors = EasyTheme.colors.module(tile.module)
            EasyCard(
                modifier = Modifier.heightIn(min = 170.dp).alpha(if (tile.enabled) 1f else 0.5f),
                containerColor = colors.container,
                onClick = { nav.navigate(tile.module.route()) },
            ) {
                ModuleIcon(tile.module, 48.dp)
                Spacer(Modifier.height(10.dp))
                Text(tile.module.title.t(), style = MaterialTheme.typography.titleMedium, color = colors.onContainer)
                Text(tile.module.description().t(), style = MaterialTheme.typography.bodySmall, color = colors.onContainer.copy(alpha = 0.8f), maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (tile.enabled) tile.status(lang) else SNav.moduleOff.t(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                )
            }
        }
    }
}
