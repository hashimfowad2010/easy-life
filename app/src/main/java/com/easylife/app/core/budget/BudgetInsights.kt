package com.easylife.app.core.budget

import com.easylife.app.core.limits.LimitEngine
import com.easylife.app.core.limits.LimitInput
import com.easylife.app.core.limits.LimitMode
import com.easylife.app.core.limits.LimitStatus
import com.easylife.app.core.limits.Observation
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

data class MonthSummary(
    val spent: Double,
    val received: Double,
    val byCategory: List<Pair<String, Double>>,
    val topMerchants: List<Pair<String, Double>>,
    val previousSpent: Double,
) {
    val changeVsPrevious: Double? get() = if (previousSpent > 0) (spent - previousSpent) / previousSpent else null
}

object BudgetInsights {
    fun summarize(month: List<Tx>, previous: List<Tx>): MonthSummary {
        val counted = month.filter { !it.excluded }
        val debits = counted.filter { it.debit }
        return MonthSummary(
            spent = Exclusions.spent(month),
            received = Exclusions.received(month),
            byCategory = debits.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }.toList().sortedByDescending { it.second },
            topMerchants = debits.groupBy { (it.merchant ?: it.counterparty ?: "—").trim().uppercase() }
                .mapValues { (_, v) -> v.sumOf { it.amount } }.toList().sortedByDescending { it.second }.take(5),
            previousSpent = Exclusions.spent(previous),
        )
    }

    /** A category budget on the Shared Limit Engine ("runs out on the 24th; keep to Rs 650/day"). */
    fun budgetStatus(limit: Double, category: String, month: YearMonth, txs: List<Tx>, now: LocalDateTime): LimitStatus =
        LimitEngine.evaluate(
            LimitInput(
                limit = limit,
                periodStart = month.atDay(1),
                periodEnd = month.atEndOfMonth(),
                mode = LimitMode.ADDITIVE_AMOUNT,
                observations = txs.filter { it.debit && !it.excluded && it.category == category }
                    .map { Observation(LocalDateTime.ofInstant(it.at, ZoneId.systemDefault()), it.amount, it.id) },
                now = now,
                halfLifeDays = 10.0,
                watchRatio = 0.8,
            ),
        )
}
