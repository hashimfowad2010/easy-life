package com.easylife.app.core.limits

import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TariffCalculatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Uses the exact JSON bundled in the app, so the shipped defaults are tested too. */
    private val config: TariffConfig = json.decodeFromString(
        TariffConfig.serializer(),
        File("src/main/assets/config/tariffs.json").readText(),
    )
    private val calc = TariffCalculator(config)

    @Test
    fun protected200IncrementalByHand() {
        // 100 x 10.54 + 100 x 13.01 = 1054 + 1301 = 2355
        val e = calc.estimate(200, ConsumerCategory.PROTECTED)
        assertEquals(ConsumerCategory.PROTECTED, e.category)
        assertEquals(BillingMode.INCREMENTAL, e.mode)
        assertEquals(2355.0, e.energyCharge, 1e-6)
    }

    @Test
    fun protected150() {
        // 100 x 10.54 + 50 x 13.01 = 1054 + 650.5 = 1704.5
        assertEquals(1704.5, calc.estimate(150, ConsumerCategory.PROTECTED).energyCharge, 1e-6)
    }

    @Test
    fun twoHundredOneBecomesNonProtected() {
        val e = calc.estimate(201, ConsumerCategory.PROTECTED)
        assertEquals(ConsumerCategory.NON_PROTECTED, e.category)
        // Config says verify_with_real_bill -> single-slab estimate (201 x 33.10) and flagged.
        assertEquals(BillingMode.SINGLE_SLAB, e.mode)
        assertEquals(201 * 33.10, e.energyCharge, 1e-6)
        assertTrue(e.needsVerification)
    }

    @Test
    fun nonProtectedIncrementalOverride() {
        // 100 x 22.44 + 100 x 28.91 + 50 x 33.10 = 2244 + 2891 + 1655 = 6790
        val e = calc.estimate(250, ConsumerCategory.NON_PROTECTED, BillingMode.INCREMENTAL)
        assertEquals(6790.0, e.energyCharge, 1e-6)
    }

    @Test
    fun lastOpenSlab() {
        // 750 units single-slab: all at 47.69
        assertEquals(750 * 47.69, calc.estimate(750, ConsumerCategory.NON_PROTECTED, BillingMode.SINGLE_SLAB).energyCharge, 1e-6)
    }

    @Test
    fun lifelineUpToHundred() {
        // 50 x 3.95 + 30 x 7.74 = 197.5 + 232.2 = 429.7
        val e = calc.estimate(80, ConsumerCategory.LIFELINE)
        assertEquals(ConsumerCategory.LIFELINE, e.category)
        assertEquals(429.7, e.energyCharge, 1e-6)
        assertEquals(ConsumerCategory.PROTECTED, calc.estimate(120, ConsumerCategory.LIFELINE).category)
    }

    @Test
    fun zeroUnitsCostNothing() {
        assertEquals(0.0, calc.estimate(0, ConsumerCategory.PROTECTED).total)
    }

    @Test
    fun calibrationMultiplier() {
        // Real bill for 180 units protected was Rs 3,000. Energy = 1054 + 80 x 13.01 = 2094.8
        val m = calc.calibrate(180, ConsumerCategory.PROTECTED, 3000.0)
        assertNotNull(m)
        assertEquals(3000.0 / 2094.8, m, 1e-9)
        val e = calc.estimate(180, ConsumerCategory.PROTECTED, calibrationMultiplier = m)
        assertEquals(3000.0, e.total, 0.5)
        assertNull(calc.calibrate(180, ConsumerCategory.PROTECTED, 50.0), "implausible multiplier is rejected")
    }

    @Test
    fun nextBoundary() {
        assertEquals(300, calc.nextBoundary(280, ConsumerCategory.NON_PROTECTED))
        assertEquals(200, calc.nextBoundary(150, ConsumerCategory.PROTECTED))
        assertNull(calc.nextBoundary(800, ConsumerCategory.NON_PROTECTED))
    }

    @Test
    fun sixMonthLookbackAllUnderIsProtected() {
        val h = (1..6).map { MonthUsage(YearMonth.of(2026, it + 3), 150 + it * 5) } // Apr..Sep, max 180
        val s = ProtectedStatusEvaluator.evaluate(h)
        assertEquals(true, s.isProtected)
        assertTrue(s.crossedMonths.isEmpty())
    }

    @Test
    fun exactlyTwoHundredStillProtected() {
        val h = (4..9).map { MonthUsage(YearMonth.of(2026, it), 200) }
        assertEquals(true, ProtectedStatusEvaluator.evaluate(h).isProtected)
    }

    @Test
    fun oneMonthAt201LosesStatusAndShowsReturnDate() {
        val h = listOf(
            MonthUsage(YearMonth.of(2026, 4), 160),
            MonthUsage(YearMonth.of(2026, 5), 190),
            MonthUsage(YearMonth.of(2026, 6), 198),
            MonthUsage(YearMonth.of(2026, 7), 201),
            MonthUsage(YearMonth.of(2026, 8), 185),
            MonthUsage(YearMonth.of(2026, 9), 170),
        )
        val s = ProtectedStatusEvaluator.evaluate(h)
        assertEquals(false, s.isProtected)
        assertEquals(listOf(YearMonth.of(2026, 7)), s.crossedMonths.map { it.month })
        // July crossed: Aug..Jan must stay under, protected again from the Feb 2027 bill.
        assertEquals(YearMonth.of(2027, 2), s.canReturnFrom)
    }

    @Test
    fun crossingOlderThanSixMonthsNoLongerCounts() {
        val h = listOf(MonthUsage(YearMonth.of(2026, 2), 260)) + (4..9).map { MonthUsage(YearMonth.of(2026, it), 150) }
        val s = ProtectedStatusEvaluator.evaluate(h)
        assertEquals(true, s.isProtected)
    }

    @Test
    fun notEnoughHistoryIsUnknown() {
        val h = (7..9).map { MonthUsage(YearMonth.of(2026, it), 150) }
        val s = ProtectedStatusEvaluator.evaluate(h)
        assertNull(s.isProtected)
        assertFalse(s.monthsChecked.isEmpty())
    }

    @Test
    fun applianceAdvisorSuggestsAcCut() {
        val appliances = listOf(
            Appliance(1, ApplianceType.AC_1_5_TON, "AC", 1600, 1, 6.0),
            Appliance(2, ApplianceType.FRIDGE, "Fridge", 110, 1, 24.0),
            Appliance(3, ApplianceType.CEILING_FAN, "Fans", 75, 4, 12.0),
        )
        val advice = ApplianceAdvisor.advise(appliances, currentDailyRate = 8.0, dailyAllowance = 5.5)
        assertEquals(2.5, advice.neededSavingPerDay, 1e-9)
        val first = advice.suggestions.first()
        assertEquals(ApplianceType.AC_1_5_TON, first.type)
        // Needs 2.5 / 1.6 kW = 1.56 h; AC cuts come in 1.5 h steps, so two steps = 3.0 h.
        assertEquals(3.0, first.reduceHours, 1e-9)
        assertTrue(advice.suggestions.sumOf { it.unitsSavedPerDay } >= 2.5)
        assertTrue(advice.suggestions.none { it.type == ApplianceType.FRIDGE }, "never suggest switching off the fridge")
    }
}
