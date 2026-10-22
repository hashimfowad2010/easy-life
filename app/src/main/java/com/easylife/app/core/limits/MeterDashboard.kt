package com.easylife.app.core.limits

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the meter dashboard shows, computed from raw data. Pure Kotlin, unit-tested. */
data class MeterDashboard(
    val meterId: Long,
    val limit: Int,
    val buffer: Int,
    val cycleStart: LocalDate?,
    val cycleEnd: LocalDate?,
    val status: LimitStatus?,
    val protected: ProtectedStatus,
    /** The status used for this month's estimate (override or from history). */
    val category: ConsumerCategory,
    val categoryIsAssumed: Boolean,
    val projectedUnits: Int?,
    /** Estimated bill if the household stays at or under the limit. */
    val estimateUnder: BillEstimate?,
    /** Estimated bill at the projected units (or at limit + 1 if the projection is under). */
    val estimateOver: BillEstimate?,
    /** Extra cost over the next 6 months if protected status is lost. */
    val sixMonthExtra: Double?,
    val nextBoundary: Int?,
    /** Projected units if the meter is read 1..5 days late. */
    val slip: List<Pair<Int, Double>>,
    val lastReadingAt: LocalDateTime?,
    val lastReading: Double?,
    val daysSinceLastReading: Long?,
    val needsBaseline: Boolean,
    val readingOverdue: Boolean,
)

object MeterDashboardCalculator {

    data class Reading(val at: LocalDateTime, val value: Double, val id: Long)

    data class Input(
        val meterId: Long,
        val limitUnits: Int,
        val safetyBuffer: Int,
        val cycleStartDate: LocalDate?,
        val cycleStartReading: Double?,
        val expectedReadingDate: LocalDate?,
        val categoryOverride: ConsumerCategory?,
        val billingModeOverride: BillingMode?,
        val calibrationMultiplier: Double?,
        val meterDigits: Int,
        val readings: List<Reading>,
        val history: List<MonthUsage>,
        val now: LocalDateTime,
    )

    fun compute(input: Input, tariff: TariffCalculator): MeterDashboard {
        val protectedStatus = ProtectedStatusEvaluator.evaluate(input.history, tariff.protectedThreshold)
        val category = input.categoryOverride ?: when (protectedStatus.isProtected) {
            true -> ConsumerCategory.PROTECTED
            false -> ConsumerCategory.NON_PROTECTED
            // Unknown history: assume protected (the case where a warning matters most) and say so.
            null -> ConsumerCategory.PROTECTED
        }
        val assumed = input.categoryOverride == null && protectedStatus.isProtected == null

        // Cycle: from the last official reading (bill) to the expected next reading (~30 days later).
        val firstReading = input.readings.minByOrNull { it.at }
        val start = input.cycleStartDate ?: firstReading?.at?.toLocalDate()
        val end = input.expectedReadingDate ?: start?.plusDays(30)
        val baseline = input.cycleStartReading ?: if (input.cycleStartDate == null) firstReading?.value else null
        val needsBaseline = input.cycleStartReading == null

        val lastBillMonth = input.history.maxByOrNull { it.month }
        val priorRate = lastBillMonth?.let { it.units / 30.0 }

        val status = if (start != null && end != null) {
            val observations = input.readings
                .filter { input.cycleStartDate == null || it.at.toLocalDate() >= start }
                .filterNot { baseline != null && input.cycleStartDate == null && it.id == firstReading?.id }
                .map { Observation(it.at, it.value, it.id) }
            LimitEngine.evaluate(
                LimitInput(
                    limit = input.limitUnits.toDouble(),
                    buffer = input.safetyBuffer.toDouble(),
                    periodStart = start,
                    periodEnd = end,
                    mode = LimitMode.CUMULATIVE_READING,
                    baseline = baseline,
                    observations = observations,
                    now = input.now,
                    meterDigits = input.meterDigits,
                    priorDailyRate = priorRate,
                ),
            )
        } else {
            null
        }

        val projected = status?.projectedAtPeriodEnd?.let { ceil(it - 1e-9).toInt() }
        val limit = input.limitUnits
        val estimateUnder = tariff.estimate(minOf(projected ?: limit, limit), ConsumerCategory.PROTECTED, input.billingModeOverride, input.calibrationMultiplier)
        val overUnits = if (projected != null && projected > limit) projected else limit + 1
        val estimateOver = tariff.estimate(overUnits, category, input.billingModeOverride, input.calibrationMultiplier)

        // What losing protected status costs over the next 6 months, at the household's usual usage.
        val typical = protectedStatus.monthsChecked.map { it.units }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            ?: projected ?: limit
        val typicalCapped = minOf(typical, limit)
        val sixMonth = if (category == ConsumerCategory.PROTECTED || category == ConsumerCategory.LIFELINE) {
            val protectedCost = tariff.estimate(typicalCapped, ConsumerCategory.PROTECTED, input.billingModeOverride, input.calibrationMultiplier).total
            val nonProtectedCost = tariff.estimate(typicalCapped, ConsumerCategory.NON_PROTECTED, input.billingModeOverride, input.calibrationMultiplier).total
            max(0.0, (nonProtectedCost - protectedCost) * 6)
        } else {
            null
        }

        val used = status?.usedSoFar?.roundToInt() ?: 0
        val boundary = when (category) {
            ConsumerCategory.NON_PROTECTED -> tariff.nextBoundary(maxOf(used, projected ?: used), ConsumerCategory.NON_PROTECTED)
            else -> limit
        }

        val slip = if (status?.dailyRate != null && status.daysRemaining > 0) (1..5).mapNotNull { d -> status.projectedWithDelay(d)?.let { d to it } } else emptyList()

        val last = input.readings.maxByOrNull { it.at }
        val daysSince = last?.let { ChronoUnit.DAYS.between(it.at.toLocalDate(), input.now.toLocalDate()) }
        val overdue = end != null && input.now.toLocalDate().isAfter(end.plusDays(2))

        return MeterDashboard(
            meterId = input.meterId,
            limit = limit,
            buffer = input.safetyBuffer,
            cycleStart = start,
            cycleEnd = end,
            status = status,
            protected = protectedStatus,
            category = category,
            categoryIsAssumed = assumed,
            projectedUnits = projected,
            estimateUnder = estimateUnder,
            estimateOver = estimateOver,
            sixMonthExtra = sixMonth,
            nextBoundary = boundary,
            slip = slip,
            lastReadingAt = last?.at,
            lastReading = last?.value,
            daysSinceLastReading = daysSince,
            needsBaseline = needsBaseline,
            readingOverdue = overdue,
        )
    }

    /** Daily units between consecutive readings, for the history chart. */
    fun dailyUsage(readings: List<Reading>, baseline: Pair<LocalDateTime, Double>?): List<Pair<LocalDate, Double>> {
        val points = (listOfNotNull(baseline) + readings.sortedBy { it.at }.map { it.at to it.value })
        val out = mutableListOf<Pair<LocalDate, Double>>()
        for (i in 1 until points.size) {
            val (a, va) = points[i - 1]
            val (b, vb) = points[i]
            val days = max(1L, ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate()))
            if (vb < va) continue
            out += b.toLocalDate() to (vb - va) / days
        }
        return out
    }

    fun monthsCrossed(history: List<MonthUsage>, threshold: Int): List<YearMonth> = history.filter { it.units > threshold }.map { it.month }
}
