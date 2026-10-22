package com.easylife.app.core.limits

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.YearMonth
import kotlin.math.min
import kotlin.math.roundToLong

@Serializable
data class Slab(val from: Int, val to: Int? = null, val rate: Double)

/** Remote Config key `tariffs`. The same JSON is bundled as the default. Rates are Rs/unit, estimates. */
@Serializable
data class TariffConfig(
    val version: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("updated_on") val updatedOn: String,
    @SerialName("source_note") val sourceNote: String,
    val lifeline: List<Slab>,
    @SerialName("protected") val protectedSlabs: List<Slab>,
    @SerialName("non_protected") val nonProtectedSlabs: List<Slab>,
    @SerialName("billing_mode") val billingMode: Map<String, String> = emptyMap(),
    @SerialName("fixed_charges") val fixedCharges: JsonElement? = null,
    @SerialName("taxes_and_adjustments_multiplier") val taxesMultiplier: Double = 1.0,
    @SerialName("protected_threshold") val protectedThreshold: Int = 200,
    @SerialName("protected_lookback_months") val protectedLookbackMonths: Int = 6,
    @SerialName("lifeline_threshold") val lifelineThreshold: Int = 100,
)

enum class ConsumerCategory { LIFELINE, PROTECTED, NON_PROTECTED }

enum class BillingMode {
    /** Each slab's units are charged at that slab's rate. */
    INCREMENTAL,

    /** All units are charged at the rate of the slab the total falls in. */
    SINGLE_SLAB,
}

data class BillEstimate(
    val units: Int,
    /** Category actually used for this month (a protected home using 201 units is billed as non-protected). */
    val category: ConsumerCategory,
    val mode: BillingMode,
    val energyCharge: Double,
    val fixedCharge: Double,
    val multiplier: Double,
    val total: Double,
    /** True when the config says this mode or the fixed charges still need checking against a real bill. */
    val needsVerification: Boolean,
    val lines: List<Line>,
) {
    data class Line(val units: Int, val rate: Double, val amount: Double)
}

class TariffCalculator(private val config: TariffConfig) {

    val protectedThreshold: Int get() = config.protectedThreshold

    fun slabsFor(category: ConsumerCategory): List<Slab> = when (category) {
        ConsumerCategory.LIFELINE -> config.lifeline
        ConsumerCategory.PROTECTED -> config.protectedSlabs
        ConsumerCategory.NON_PROTECTED -> config.nonProtectedSlabs
    }

    /** The billing mode from config, and whether the config says to verify it. */
    fun modeFor(category: ConsumerCategory, override: BillingMode? = null): Pair<BillingMode, Boolean> {
        if (override != null) return override to false
        val key = when (category) {
            ConsumerCategory.LIFELINE -> "lifeline"
            ConsumerCategory.PROTECTED -> "protected"
            ConsumerCategory.NON_PROTECTED -> "non_protected"
        }
        return when (config.billingMode[key]?.lowercase()) {
            "incremental", null -> BillingMode.INCREMENTAL to (config.billingMode[key] == null && category == ConsumerCategory.NON_PROTECTED)
            "single_slab" -> BillingMode.SINGLE_SLAB to false
            // Unknown until checked against a real bill: use the higher (single-slab) estimate, and say so.
            else -> BillingMode.SINGLE_SLAB to true
        }
    }

    private val fixedChargeValue: Double?
        get() = (config.fixedCharges as? JsonPrimitive)?.doubleOrNull

    /**
     * Estimate the bill for [units] for a household whose status is [status].
     * If units go above the category's top slab (e.g. 201 for protected), this month is billed as non-protected.
     */
    fun estimate(
        units: Int,
        status: ConsumerCategory,
        modeOverride: BillingMode? = null,
        calibrationMultiplier: Double? = null,
    ): BillEstimate {
        val u = units.coerceAtLeast(0)
        val effective = effectiveCategory(u, status)
        val (mode, verify) = modeFor(effective, modeOverride)
        val slabs = slabsFor(effective)
        val lines = when (mode) {
            BillingMode.INCREMENTAL -> incremental(u, slabs)
            BillingMode.SINGLE_SLAB -> singleSlab(u, slabs)
        }
        val energy = lines.sumOf { it.amount }
        val fixed = if (u == 0) 0.0 else fixedChargeValue ?: 0.0
        val multiplier = calibrationMultiplier ?: config.taxesMultiplier
        return BillEstimate(
            units = u,
            category = effective,
            mode = mode,
            energyCharge = energy,
            fixedCharge = fixed,
            multiplier = multiplier,
            total = roundRupees((energy + fixed) * multiplier),
            needsVerification = verify || fixedChargeValue == null,
            lines = lines,
        )
    }

    fun effectiveCategory(units: Int, status: ConsumerCategory): ConsumerCategory = when (status) {
        ConsumerCategory.LIFELINE -> when {
            units <= config.lifelineThreshold -> ConsumerCategory.LIFELINE
            units <= config.protectedThreshold -> ConsumerCategory.PROTECTED
            else -> ConsumerCategory.NON_PROTECTED
        }
        ConsumerCategory.PROTECTED ->
            if (units <= config.protectedThreshold) ConsumerCategory.PROTECTED else ConsumerCategory.NON_PROTECTED
        ConsumerCategory.NON_PROTECTED -> ConsumerCategory.NON_PROTECTED
    }

    /**
     * "Calibrate with my last bill": the multiplier that turns our energy estimate into the real payable amount
     * (covers taxes, fuel adjustment, fixed charges). Returns null if the result is implausible.
     */
    fun calibrate(units: Int, status: ConsumerCategory, actualPayable: Double, modeOverride: BillingMode? = null): Double? {
        if (units <= 0 || actualPayable <= 0) return null
        val base = estimate(units, status, modeOverride, calibrationMultiplier = 1.0)
        val raw = base.energyCharge + base.fixedCharge
        if (raw <= 0) return null
        val m = actualPayable / raw
        return if (m in 0.7..3.5) m else null
    }

    /** Next slab boundary above [units] (e.g. 300 when at 280 units), or null if in the last slab. */
    fun nextBoundary(units: Int, category: ConsumerCategory): Int? =
        slabsFor(category).firstOrNull { it.to != null && units <= it.to }?.to

    private fun incremental(units: Int, slabs: List<Slab>): List<BillEstimate.Line> {
        val lines = mutableListOf<BillEstimate.Line>()
        var remaining = units
        for (slab in slabs.sortedBy { it.from }) {
            if (remaining <= 0) break
            val size = if (slab.to == null) remaining else slab.to - slab.from + 1
            val take = min(size, remaining)
            lines += BillEstimate.Line(take, slab.rate, take * slab.rate)
            remaining -= take
        }
        if (remaining > 0) {
            // Units above the last slab defined: charge at the last rate.
            val rate = slabs.maxBy { it.from }.rate
            lines += BillEstimate.Line(remaining, rate, remaining * rate)
        }
        return lines
    }

    private fun singleSlab(units: Int, slabs: List<Slab>): List<BillEstimate.Line> {
        if (units == 0) return emptyList()
        val slab = slabs.sortedBy { it.from }.firstOrNull { units >= it.from && (it.to == null || units <= it.to) }
            ?: slabs.maxBy { it.from }
        return listOf(BillEstimate.Line(units, slab.rate, units * slab.rate))
    }

    companion object {
        fun roundRupees(value: Double): Double = value.roundToLong().toDouble()
    }
}

data class MonthUsage(val month: YearMonth, val units: Int)

data class ProtectedStatus(
    /** null = not enough history to know (ask the user). */
    val isProtected: Boolean?,
    val monthsChecked: List<MonthUsage>,
    val crossedMonths: List<MonthUsage>,
    /** If not protected: first bill month from which status could return, if every month until then stays under. */
    val canReturnFrom: YearMonth?,
    val monthsOfHistory: Int,
)

object ProtectedStatusEvaluator {
    /**
     * Protected if each of the last [lookback] billing months used <= [threshold] units.
     * [history] can be in any order and may contain gaps; the most recent [lookback] months are used.
     */
    fun evaluate(history: List<MonthUsage>, threshold: Int = 200, lookback: Int = 6): ProtectedStatus {
        val byMonth = history.groupBy { it.month }.map { (_, v) -> v.last() }.sortedByDescending { it.month }
        val recent = byMonth.take(lookback)
        val crossed = recent.filter { it.units > threshold }
        val latest = recent.firstOrNull()?.month
        val isProtected = when {
            crossed.isNotEmpty() -> false
            recent.size < lookback -> null
            else -> true
        }
        val canReturn = if (crossed.isNotEmpty()) crossed.maxOf { it.month }.plusMonths((lookback + 1).toLong()) else null
        return ProtectedStatus(
            isProtected = isProtected,
            monthsChecked = recent,
            crossedMonths = crossed.sortedBy { it.month },
            canReturnFrom = canReturn?.takeIf { latest == null || it.isAfter(latest) },
            monthsOfHistory = byMonth.size,
        )
    }
}
