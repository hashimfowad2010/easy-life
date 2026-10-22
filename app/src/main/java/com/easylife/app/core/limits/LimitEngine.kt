package com.easylife.app.core.limits

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.pow

/**
 * Shared Limit Engine: "warn before the point of no return".
 *
 * Used by Limits (electricity slabs, custom limits) and Budget (category budgets).
 * Pure Kotlin, no Android types, fully unit-tested.
 */
enum class LimitMode {
    /** Values are meter-style cumulative readings (usage = latest - baseline). */
    CUMULATIVE_READING,

    /** Values are amounts that add up (spending, data used). */
    ADDITIVE_AMOUNT,
}

enum class Risk { SAFE, WATCH, DANGER, CROSSED }

enum class RateConfidence {
    /** No usable rate yet: ask for another reading. */
    NONE,

    /** Rate taken from a previous cycle (e.g. last bill's average). */
    PRIOR,
    LOW,
    MEDIUM,
    HIGH,
}

data class Observation(val at: LocalDateTime, val value: Double, val id: Long? = null)

data class LimitInput(
    val limit: Double,
    val buffer: Double = 0.0,
    val periodStart: LocalDate,
    /** Inclusive end of the period: the expected meter-reading date or the last day of the month. */
    val periodEnd: LocalDate,
    val mode: LimitMode,
    /** CUMULATIVE only: reading at the start of the period (e.g. "present reading" on the last bill). */
    val baseline: Double? = null,
    val observations: List<Observation>,
    val now: LocalDateTime,
    /** CUMULATIVE only: number of meter digits, used to detect rollover (99999 -> 00012). */
    val meterDigits: Int? = null,
    /** Daily rate from a previous period, used until the current period has enough data. */
    val priorDailyRate: Double? = null,
    val halfLifeDays: Double = 7.0,
    val watchRatio: Double = 0.9,
)

sealed interface LimitIssue {
    /** Only one data point: ask the user for one more reading. */
    data object NeedMoreReadings : LimitIssue

    /** A reading lower than the one before it (ignored in the maths). Ask the user to re-check. */
    data class ReadingWentDown(val at: LocalDateTime, val previous: Double, val current: Double, val id: Long?) : LimitIssue

    /** Reading dropped near the meter's maximum: treated as the meter rolling over. */
    data class RolloverAssumed(val at: LocalDateTime) : LimitIssue

    /** Long gap between readings (rate is spread evenly over the gap). */
    data class LongGap(val days: Int) : LimitIssue

    /** The period has ended; a new cycle should start (e.g. a new bill). */
    data object PeriodEnded : LimitIssue
}

data class LimitStatus(
    val limit: Double,
    val buffer: Double,
    val usedSoFar: Double,
    val daysElapsed: Double,
    val daysRemaining: Double,
    /** Expected use per day, exponentially weighted so recent behaviour counts more. Null if unknown. */
    val dailyRate: Double?,
    val rateConfidence: RateConfidence,
    val projectedAtPeriodEnd: Double?,
    val risk: Risk,
    /** (limit - buffer - used) / daysRemaining, never negative. Null once the period is over. */
    val dailyAllowance: Double?,
    /** Date the limit would be crossed at the current pace, if inside the period. */
    val projectedCrossDate: LocalDate?,
    val issues: List<LimitIssue>,
    val now: LocalDateTime,
) {
    val remaining: Double get() = limit - usedSoFar
    val fractionUsed: Double get() = if (limit <= 0) 0.0 else usedSoFar / limit

    /** Projected total if the period ends [extraDays] later than planned (e.g. the meter is read late). */
    fun projectedWithDelay(extraDays: Int): Double? =
        dailyRate?.let { usedSoFar + it * (daysRemaining + extraDays) }
}

object LimitEngine {

    private const val MIN_INTERVAL_DAYS = 0.25
    private const val LONG_GAP_DAYS = 10

    fun evaluate(input: LimitInput): LimitStatus {
        val start = input.periodStart.atStartOfDay()
        val end = input.periodEnd.atTime(LocalTime.MAX)
        val now = when {
            input.now.isBefore(start) -> start
            else -> input.now
        }
        val issues = mutableListOf<LimitIssue>()
        if (now.isAfter(end)) issues += LimitIssue.PeriodEnded

        val daysElapsed = days(start, min(now, end))
        val daysRemaining = max(0.0, days(now, end))

        val usage = when (input.mode) {
            LimitMode.CUMULATIVE_READING -> cumulativeUsage(input, start, now, issues)
            LimitMode.ADDITIVE_AMOUNT -> additiveUsage(input, start, now, daysElapsed)
        }

        var rate = usage.rate
        var confidence = usage.confidence
        if (rate == null && input.priorDailyRate != null && input.priorDailyRate >= 0) {
            rate = input.priorDailyRate
            confidence = RateConfidence.PRIOR
        }

        val used = usage.used
        val projected = rate?.let { used + it * daysRemaining }
        val risk = riskFor(used, projected, input)

        val allowance = if (daysRemaining > 0.0) {
            max(0.0, (input.limit - input.buffer - used) / max(1.0, daysRemaining))
        } else {
            null
        }

        val crossDate = if (rate != null && rate > 0 && used <= input.limit) {
            val daysToCross = (input.limit - used) / rate
            val crossAt = now.plus(Duration.ofSeconds((daysToCross * SECONDS_PER_DAY).toLong()))
            if (!crossAt.isAfter(end)) crossAt.toLocalDate() else null
        } else {
            null
        }

        return LimitStatus(
            limit = input.limit,
            buffer = input.buffer,
            usedSoFar = used,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            dailyRate = rate,
            rateConfidence = confidence,
            projectedAtPeriodEnd = projected,
            risk = risk,
            dailyAllowance = allowance,
            projectedCrossDate = crossDate,
            issues = issues,
            now = now,
        )
    }

    fun riskFor(used: Double, projected: Double?, input: LimitInput): Risk = when {
        used > input.limit -> Risk.CROSSED
        projected == null -> if (used > input.watchRatio * input.limit) Risk.WATCH else Risk.SAFE
        projected > input.limit -> Risk.DANGER
        projected > input.watchRatio * input.limit -> Risk.WATCH
        input.buffer > 0 && projected > input.limit - input.buffer -> Risk.WATCH
        else -> Risk.SAFE
    }

    private data class Usage(val used: Double, val rate: Double?, val confidence: RateConfidence)

    private data class Point(val at: LocalDateTime, val value: Double)

    private fun cumulativeUsage(input: LimitInput, start: LocalDateTime, now: LocalDateTime, issues: MutableList<LimitIssue>): Usage {
        val sorted = input.observations
            .filter { !it.at.isBefore(start) && !it.at.isAfter(now) }
            .sortedBy { it.at }

        // The baseline is the reading taken on the period's first day (meter reader's visit), assumed midday.
        val points = mutableListOf<Point>()
        input.baseline?.let { points += Point(input.periodStart.atTime(12, 0), it) }

        // Build a monotonic series: unwrap rollovers, drop readings that went down.
        var offset = 0.0
        for (obs in sorted) {
            val last = points.lastOrNull()
            var value = obs.value + offset
            if (last != null && value < last.value) {
                val max = input.meterDigits?.let { 10.0.pow(it) }
                val rawLast = last.value - offset
                if (max != null && rawLast > 0.8 * max && obs.value < 0.2 * max) {
                    offset += max
                    value = obs.value + offset
                    issues += LimitIssue.RolloverAssumed(obs.at)
                } else {
                    issues += LimitIssue.ReadingWentDown(obs.at, rawLast, obs.value, obs.id)
                    continue
                }
            }
            if (last != null && days(last.at, obs.at) < MIN_INTERVAL_DAYS) {
                // Two readings a few hours apart: keep the newer one.
                points[points.lastIndex] = Point(obs.at, value)
            } else {
                points += Point(obs.at, value)
            }
        }

        if (points.size < 2) {
            issues += LimitIssue.NeedMoreReadings
            return Usage(used = 0.0, rate = null, confidence = RateConfidence.NONE)
        }

        val used = points.last().value - points.first().value

        var weightSum = 0.0
        var weighted = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val span = days(a.at, b.at)
            if (span <= 0) continue
            if (span > LONG_GAP_DAYS) issues += LimitIssue.LongGap(span.toInt())
            val mid = a.at.plus(Duration.ofSeconds((span * SECONDS_PER_DAY / 2).toLong()))
            val age = max(0.0, days(mid, now))
            val w = span * 0.5.pow(age / input.halfLifeDays)
            weighted += w * ((b.value - a.value) / span)
            weightSum += w
        }
        val rate = if (weightSum > 0) weighted / weightSum else null
        val totalSpan = days(points.first().at, points.last().at)
        val confidence = when {
            rate == null -> RateConfidence.NONE
            totalSpan < 2.0 -> RateConfidence.LOW
            totalSpan < 7.0 || points.size < 3 -> RateConfidence.MEDIUM
            else -> RateConfidence.HIGH
        }
        return Usage(used = used, rate = rate, confidence = confidence)
    }

    private fun additiveUsage(input: LimitInput, start: LocalDateTime, now: LocalDateTime, daysElapsed: Double): Usage {
        val inPeriod = input.observations.filter { !it.at.isBefore(start) && !it.at.isAfter(now) }
        val used = inPeriod.sumOf { it.value }
        val elapsedWholeDays = max(1, kotlin.math.ceil(daysElapsed).toInt())
        if (inPeriod.isEmpty() && daysElapsed < 1.0) {
            return Usage(used = 0.0, rate = null, confidence = RateConfidence.NONE)
        }
        // Daily totals, including days with no spending, weighted towards recent days.
        val perDay = DoubleArray(elapsedWholeDays)
        for (o in inPeriod) {
            val idx = days(start, o.at).toInt().coerceIn(0, elapsedWholeDays - 1)
            perDay[idx] += o.value
        }
        var weightSum = 0.0
        var weighted = 0.0
        for (i in perDay.indices) {
            val age = (elapsedWholeDays - 1 - i).toDouble()
            val w = 0.5.pow(age / input.halfLifeDays)
            weighted += w * perDay[i]
            weightSum += w
        }
        val ewma = weighted / weightSum
        // Early in the period a single big purchase dominates; blend with the plain average.
        val plain = used / elapsedWholeDays
        val rate = if (elapsedWholeDays < 7) (ewma + plain) / 2 else ewma
        val confidence = when {
            elapsedWholeDays < 3 -> RateConfidence.LOW
            elapsedWholeDays < 10 -> RateConfidence.MEDIUM
            else -> RateConfidence.HIGH
        }
        return Usage(used = used, rate = rate, confidence = confidence)
    }

    private const val SECONDS_PER_DAY = 86_400.0

    internal fun days(from: LocalDateTime, to: LocalDateTime): Double =
        Duration.between(from, to).seconds / SECONDS_PER_DAY

    private fun min(a: LocalDateTime, b: LocalDateTime) = if (a.isBefore(b)) a else b
}
