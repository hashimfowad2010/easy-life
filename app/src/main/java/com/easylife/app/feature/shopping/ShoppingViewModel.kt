package com.easylife.app.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.budget.BudgetInsights
import com.easylife.app.core.cloud.Retailer
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.db.PriceReportEntity
import com.easylife.app.core.data.db.ShoppingItemEntity
import com.easylife.app.core.data.db.ShoppingListEntity
import com.easylife.app.core.data.db.StoreEntity
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.ShoppingRepository
import com.easylife.app.core.data.repo.toTx
import com.easylife.app.core.shopping.BasketComparison
import com.easylife.app.core.shopping.BasketOptimizer
import com.easylife.app.core.shopping.ListNeed
import com.easylife.app.core.shopping.Quantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class ListDetail(
    val list: ShoppingListEntity,
    val items: List<ShoppingItemEntity>,
    val prices: List<PriceReportEntity>,
    val stores: Map<String, StoreEntity>,
    val comparison: BasketComparison,
    /** Estimated total for unchecked items (cheapest known price, or the price you typed). */
    val estimate: Double,
    val estimateMustHave: Double,
    val groceriesLeft: Double?,
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repo: ShoppingRepository,
    budget: BudgetRepository,
    config: RemoteConfigRepository,
) : ViewModel() {

    val lists: StateFlow<List<ShoppingListEntity>?> = repo.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val retailers: StateFlow<List<Retailer>> = config.config.map { c -> c.retailers.retailers.filter { it.grocery } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val listId = MutableStateFlow<Long?>(null)
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val groceriesLeft: kotlinx.coroutines.flow.Flow<Double?> =
        combine(budget.month(YearMonth.now()), budget.categories) { txs, cats ->
            val c: CategoryEntity = cats.firstOrNull { it.key == "groceries" } ?: return@combine null
            val limit = c.monthlyBudget ?: return@combine null
            BudgetInsights.budgetStatus(limit, "groceries", YearMonth.now(), txs.map { it.toTx() }, LocalDateTime.now()).remaining
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<ListDetail?> = listId.flatMapLatest { id ->
        if (id == null) flowOf(null) else combine(repo.list(id), repo.items(id)) { l, i -> l to i }.flatMapLatest { (l, items) ->
            if (l == null) {
                flowOf(null)
            } else {
                val keys = items.mapNotNull { it.itemKey }
                combine(repo.prices(keys), repo.stores, groceriesLeft) { prices, stores, left -> build(l, items, prices, stores, left) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(l: ShoppingListEntity, items: List<ShoppingItemEntity>, prices: List<PriceReportEntity>, stores: List<StoreEntity>, left: Double?): ListDetail {
        val open = items.filter { !it.checked && it.itemKey != null }
        val needs = open.map { ListNeed(it.itemKey!!, Quantity.of(it.qty, it.unit)) }
        val comparison = BasketOptimizer.compare(needs, repo.pricePoints(prices))
        fun costOf(i: ShoppingItemEntity): Double = i.manualPrice ?: (comparison.cheapestPerItem[i.itemKey]?.cost ?: 0.0)
        return ListDetail(
            list = l,
            items = items,
            prices = prices,
            stores = stores.associateBy { it.id },
            comparison = comparison,
            estimate = open.sumOf { costOf(it) },
            estimateMustHave = open.filter { it.priority == 1 }.sumOf { costOf(it) },
            groceriesLeft = left,
        )
    }

    fun open(id: Long) {
        listId.value = id
    }

    fun createList(name: String, text: String, onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(repo.createList(name, text)) }
    fun addItems(text: String) = viewModelScope.launch { listId.value?.let { repo.addItems(it, text) } }
    fun toggleChecked(i: ShoppingItemEntity) = viewModelScope.launch { repo.updateItem(i.copy(checked = !i.checked)) }
    fun togglePriority(i: ShoppingItemEntity) = viewModelScope.launch { repo.updateItem(i.copy(priority = if (i.priority == 1) 2 else 1)) }
    fun updateQty(i: ShoppingItemEntity, qty: Double) = viewModelScope.launch { repo.updateItem(i.copy(qty = qty)) }
    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }
    fun deleteList(onDone: () -> Unit) = viewModelScope.launch {
        listId.value?.let { repo.deleteList(it) }
        onDone()
    }
    fun setManualPrice(i: ShoppingItemEntity, price: Double, retailer: String, pack: Double, unit: String) =
        viewModelScope.launch { repo.setManualPrice(i, price, retailer, pack, unit) }

    fun sync(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _syncing.value = true
        val keys = detail.value?.items?.mapNotNull { it.itemKey }.orEmpty()
        val ok = repo.syncCommunity(keys)
        _syncing.value = false
        onDone(ok)
    }

    fun logComparison() {
        detail.value?.let { repo.logComparison(it.comparison.savingsVsAverage) }
    }

    fun shareText(items: List<ShoppingItemEntity>, lang: com.easylife.app.core.i18n.AppLanguage) = repo.shareText(items, lang)
}
