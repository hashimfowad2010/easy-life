package com.easylife.app.core.limits

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Appliance types with approximate average running watts. Labelled "approximate" in the UI and editable. */
enum class ApplianceType(val defaultWatts: Int, val defaultHours: Double, val canCut: Boolean = true, val stepHours: Double = 1.0) {
    AC_1_TON(1100, 6.0, stepHours = 1.5),
    AC_1_5_TON(1600, 6.0, stepHours = 1.5),
    AC_1_TON_INVERTER(650, 8.0, stepHours = 1.5),
    AC_1_5_TON_INVERTER(950, 8.0, stepHours = 1.5),
    FRIDGE(110, 24.0, canCut = false),
    FRIDGE_INVERTER(60, 24.0, canCut = false),
    DEEP_FREEZER(130, 24.0, canCut = false),
    WATER_MOTOR(750, 1.0, stepHours = 0.5),
    IRON(1000, 0.5, stepHours = 0.25),
    WASHING_MACHINE(500, 1.0, stepHours = 0.5),
    CEILING_FAN(75, 12.0, stepHours = 2.0),
    INVERTER_FAN(35, 12.0, stepHours = 2.0),
    PEDESTAL_FAN(60, 6.0, stepHours = 2.0),
    AIR_COOLER(180, 8.0, stepHours = 2.0),
    LED_LIGHT(12, 6.0, stepHours = 2.0),
    TUBE_LIGHT(40, 6.0, stepHours = 2.0),
    ENERGY_SAVER(25, 6.0, stepHours = 2.0),
    ELECTRIC_GEYSER(2000, 1.0, stepHours = 0.5),
    ROOM_HEATER(1000, 3.0, stepHours = 1.0),
    TV(90, 5.0, stepHours = 1.0),
    COMPUTER(150, 4.0, stepHours = 1.0),
    MICROWAVE(1200, 0.25, canCut = false),
    ELECTRIC_KETTLE(1500, 0.25, canCut = false),
    UPS_CHARGING(150, 6.0, canCut = false),
    OTHER(100, 2.0),
}

data class Appliance(
    val id: Long,
    val type: ApplianceType,
    val label: String,
    val watts: Int,
    val quantity: Int,
    val hoursPerDay: Double,
) {
    val unitsPerDay: Double get() = watts * quantity * hoursPerDay / 1000.0
}

data class CutSuggestion(
    val applianceId: Long,
    val type: ApplianceType,
    val label: String,
    val reduceHours: Double,
    val unitsSavedPerDay: Double,
)

data class ApplianceAdvice(
    val estimatedUnitsPerDay: Double,
    val breakdown: List<Pair<Appliance, Double>>,
    /** Units/day the household needs to save to stay under the limit (0 if already fine). */
    val neededSavingPerDay: Double,
    val suggestions: List<CutSuggestion>,
)

object ApplianceAdvisor {

    /**
     * @param currentDailyRate measured units/day from the meter (null if unknown; the appliance estimate is used).
     * @param dailyAllowance units/day allowed for the rest of the cycle (from the Limit Engine).
     */
    fun advise(appliances: List<Appliance>, currentDailyRate: Double?, dailyAllowance: Double?): ApplianceAdvice {
        val breakdown = appliances.map { it to it.unitsPerDay }.sortedByDescending { it.second }
        val estimate = breakdown.sumOf { it.second }
        val actual = currentDailyRate ?: estimate
        val needed = if (dailyAllowance == null) 0.0 else max(0.0, actual - dailyAllowance)

        val suggestions = mutableListOf<CutSuggestion>()
        var covered = 0.0
        // Biggest-impact cuts first; always offer at least the top two ideas even when no saving is needed.
        for ((appliance, perDay) in breakdown) {
            if (!appliance.type.canCut || perDay <= 0.05) continue
            val step = appliance.type.stepHours
            val maxCut = max(0.0, appliance.hoursPerDay - minimumHours(appliance.type))
            if (maxCut <= 0) continue
            val perHour = appliance.watts * appliance.quantity / 1000.0
            val stillNeeded = needed - covered
            val hours = if (stillNeeded > 0) {
                min(maxCut, roundUpTo(stillNeeded / perHour, step))
            } else {
                min(maxCut, step)
            }
            if (hours <= 0) continue
            val saved = hours * perHour
            suggestions += CutSuggestion(appliance.id, appliance.type, appliance.label, hours, saved)
            covered += saved
            if ((needed > 0 && covered >= needed && suggestions.size >= 1) || (needed == 0.0 && suggestions.size >= 2)) break
            if (suggestions.size >= 4) break
        }
        return ApplianceAdvice(estimate, breakdown, needed, suggestions)
    }

    private fun minimumHours(type: ApplianceType): Double = when (type) {
        ApplianceType.CEILING_FAN, ApplianceType.INVERTER_FAN -> 4.0
        ApplianceType.LED_LIGHT, ApplianceType.TUBE_LIGHT, ApplianceType.ENERGY_SAVER -> 2.0
        else -> 0.0
    }

    private fun roundUpTo(value: Double, step: Double): Double {
        val steps = kotlin.math.ceil(value / step - 1e-9)
        return max(step, steps * step)
    }

    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
