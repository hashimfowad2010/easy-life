package com.easylife.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.data.db.BillEntity
import com.easylife.app.core.data.db.DocumentDao
import com.easylife.app.core.data.db.ReminderEntity
import com.easylife.app.core.data.db.ReviewDao
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.prefs.UserPrefs
import com.easylife.app.core.data.repo.BillState
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.data.repo.DemoDataSeeder
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.MeterBundle
import com.easylife.app.core.data.repo.state
import com.easylife.app.core.data.repo.toTx
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.limits.Risk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One item on the Today feed. Higher [priority] shows first. */
sealed interface TodayCard {
    val priority: Int

    data class Meter(val bundle: MeterBundle) : TodayCard {
        override val priority: Int = when (bundle.dashboard.status?.risk) {
            Risk.CROSSED -> 100
            Risk.DANGER -> 90
            Risk.WATCH -> 70
            else -> 20
        }
    }

    data class MeterPhoto(val bundle: MeterBundle, val days: Long) : TodayCard {
        override val priority = 40
    }

    data class Bill(val bill: BillEntity, val state: BillState, val daysLeft: Long?) : TodayCard {
        override val priority: Int = when {
            state == BillState.OVERDUE -> 85
            daysLeft != null && daysLeft <= 2 -> 80
            state == BillState.DUE_SOON -> 60
            else -> 10
        }
    }

    data class Reminder(val reminder: ReminderEntity) : TodayCard {
        override val priority = 55
    }

    data class Review(val count: Int) : TodayCard {
        override val priority = 30
    }

    data object SetupMeter : TodayCard {
        override val priority = 5
    }

    data class Budget(val category: com.easylife.app.core.data.db.CategoryEntity, val status: com.easylife.app.core.limits.LimitStatus) : TodayCard {
        override val priority: Int = when (status.risk) {
            Risk.CROSSED -> 75
            Risk.DANGER -> 65
            else -> 45
        }
    }
}

private data class TodayExtras(
    val reviews: Int,
    val reminders: List<ReminderEntity>,
    val summary: Triple<List<String>, List<String>, Provenance?>,
    val budgets: List<TodayCard.Budget>,
)

data class TodayState(
    val loading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    val cards: List<TodayCard> = emptyList(),
    val summaryEn: List<String> = emptyList(),
    val summaryUr: List<String> = emptyList(),
    val summarySource: Provenance? = null,
    val hasAnyData: Boolean = false,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val prefsRepo: PrefsRepository,
    limits: LimitsRepository,
    bills: BillsRepository,
    reviewDao: ReviewDao,
    documentDao: DocumentDao,
    private val router: AiRouter,
    private val demo: DemoDataSeeder,
    budget: com.easylife.app.core.data.repo.BudgetRepository,
) : ViewModel() {

    private val summary = MutableStateFlow<Triple<List<String>, List<String>, Provenance?>>(Triple(emptyList(), emptyList(), null))
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val state: StateFlow<TodayState> = combine(
        prefsRepo.prefs,
        limits.meters,
        limits.selectedMeter,
        bills.unpaid,
        combine(
            reviewDao.observeOpen(),
            documentDao.observeOpenReminders(),
            summary,
            combine(budget.month(java.time.YearMonth.now()), budget.categories) { txs, cats ->
                val month = java.time.YearMonth.now()
                val tx = txs.map { it.toTx() }
                cats.filter { (it.monthlyBudget ?: 0.0) > 0 }.mapNotNull { c ->
                    val s = com.easylife.app.core.budget.BudgetInsights.budgetStatus(c.monthlyBudget!!, c.key, month, tx, java.time.LocalDateTime.now())
                    if (s.risk == Risk.SAFE) null else TodayCard.Budget(c, s)
                }
            },
        ) { r, rem, s, b -> TodayExtras(r.size, rem, s, b) },
    ) { prefs, meters, bundle, unpaid, extras ->
        val reviews = extras.reviews
        val reminders = extras.reminders
        val sum = extras.summary
        val today = LocalDate.now()
        val cards = mutableListOf<TodayCard>()
        if (Module.LIMITS in prefs.enabledModules) {
            if (bundle != null) {
                cards += TodayCard.Meter(bundle)
                val days = bundle.dashboard.daysSinceLastReading ?: bundle.dashboard.cycleStart?.let { ChronoUnit.DAYS.between(it, today) }
                if (days != null && days >= bundle.meter.photoReminderDays) cards += TodayCard.MeterPhoto(bundle, days)
            } else if (meters.isEmpty()) {
                cards += TodayCard.SetupMeter
            }
        }
        if (Module.BILLS in prefs.enabledModules) {
            unpaid.forEach { b ->
                cards += TodayCard.Bill(b, b.state(today), b.dueDate?.let { ChronoUnit.DAYS.between(today, it) })
            }
        }
        if (Module.BUDGET in prefs.enabledModules) cards += extras.budgets
        reminders.filter { !it.date.isAfter(today.plusDays(3)) }.forEach { cards += TodayCard.Reminder(it) }
        if (reviews > 0) cards += TodayCard.Review(reviews)
        TodayState(
            loading = false,
            prefs = prefs,
            cards = cards.sortedByDescending { it.priority },
            summaryEn = sum.first,
            summaryUr = sum.second,
            summarySource = sum.third,
            hasAnyData = meters.isNotEmpty() || unpaid.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    init {
        viewModelScope.launch {
            // Weekly summary from aggregated, rounded numbers only.
            val bundle = limits.allBundles().firstOrNull()
            val nextBill = bills.unpaidNow().filter { it.dueDate != null }.minByOrNull { it.dueDate!! }
            val params = buildMap {
                bundle?.dashboard?.let { d ->
                    d.status?.let { s -> put("units_used", s.usedSoFar.toInt().toString()) }
                    put("units_limit", d.limit.toString())
                    d.projectedUnits?.let { put("units_projected", it.toString()) }
                }
                nextBill?.let { b ->
                    put("next_bill_provider", b.provider ?: b.billType)
                    put("next_bill_days", ChronoUnit.DAYS.between(LocalDate.now(), b.dueDate).toString())
                    b.amountWithinDue?.let { put("next_bill_amount", it.toInt().toString()) }
                }
            }
            if (params.isEmpty()) return@launch
            val r = router.run(AiTask.WeeklySummary, AiInput(params = params))
            r.value?.let { summary.value = Triple(it.linesEn, it.linesUr, r.provenance) }
        }
    }

    fun loadDemo() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { demo.load() }
            _busy.value = false
        }
    }

    fun turnOffDemo() {
        viewModelScope.launch { demo.clear() }
    }
}
