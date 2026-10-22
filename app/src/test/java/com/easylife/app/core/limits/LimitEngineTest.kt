package com.easylife.app.core.limits

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitEngineTest {

    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 31)

    private fun meter(
        baseline: Double? = 10_000.0,
        vararg readings: Pair<LocalDateTime, Double>,
        now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 12, 0),
        buffer: Double = 5.0,
        digits: Int? = 5,
        prior: Double? = null,
    ) = LimitEngine.evaluate(
        LimitInput(
            limit = 200.0,
            buffer = buffer,
            periodStart = start,
            periodEnd = end,
            mode = LimitMode.CUMULATIVE_READING,
            baseline = baseline,
            observations = readings.mapIndexed { i, (at, v) -> Observation(at, v, i.toLong()) },
            now = now,
            meterDigits = digits,
            priorDailyRate = prior,
        ),
    )

    private fun at(day: Int, hour: Int = 12) = LocalDateTime.of(2026, 10, day, hour, 0)

    @Test
    fun steadyUseMatchesHandCalculation() {
        // Baseline on Oct 1 noon, 6 units/day for 15 days -> 90 used on Oct 16 noon.
        val s = meter(10_000.0, at(6) to 10_030.0, at(11) to 10_060.0, at(16) to 10_090.0)
        assertEquals(90.0, s.usedSoFar, 1e-9)
        assertEquals(6.0, s.dailyRate!!, 1e-9)
        // 15.5 days elapsed since Oct 1 00:00, remaining until Oct 31 23:59:59.999.
        assertEquals(15.5, s.daysElapsed, 1e-6)
        assertEquals(15.5, s.daysRemaining, 1e-3)
        assertEquals(90.0 + 6.0 * 15.5, s.projectedAtPeriodEnd!!, 1e-2)
        // Projected 183 > 0.9 * 200 = 180 -> WATCH.
        assertEquals(Risk.WATCH, s.risk)
        // (200 - 5 - 90) / 15.5
        assertEquals(105.0 / 15.5, s.dailyAllowance!!, 1e-3)
        assertEquals(RateConfidence.HIGH, s.rateConfidence)
        assertNull(s.projectedCrossDate)
    }

    @Test
    fun fastUseIsDangerWithCrossDate() {
        // 10 units/day: 150 used by Oct 16 noon, will pass 200 five days later.
        val s = meter(10_000.0, at(6) to 10_050.0, at(11) to 10_100.0, at(16) to 10_150.0)
        assertEquals(Risk.DANGER, s.risk)
        assertEquals(LocalDate.of(2026, 10, 21), s.projectedCrossDate)
    }

    @Test
    fun boundaryTwoHundredIsSafeTwoHundredOneCrosses() {
        val ok = meter(10_000.0, at(30) to 10_200.0, now = at(31))
        assertTrue(ok.risk != Risk.CROSSED, "exactly 200 units keeps protected status")
        val over = meter(10_000.0, at(30) to 10_201.0, now = at(31))
        assertEquals(Risk.CROSSED, over.risk)
    }

    @Test
    fun recentBehaviourCountsMore() {
        // Slow first week (3/day), fast recently (9/day): EWMA must be above the simple average of 6.
        val s = meter(10_000.0, at(8) to 10_021.0, at(15) to 10_084.0, now = at(15))
        val simpleAverage = 84.0 / 14.0
        assertTrue(s.dailyRate!! > simpleAverage, "rate ${s.dailyRate} should favour recent days")
        assertTrue(s.dailyRate!! < 9.0)
    }

    @Test
    fun singleReadingAsksForAnother() {
        val s = meter(baseline = null, at(5) to 10_040.0)
        assertTrue(s.issues.contains(LimitIssue.NeedMoreReadings))
        assertNull(s.dailyRate)
        assertEquals(RateConfidence.NONE, s.rateConfidence)
    }

    @Test
    fun priorRateUsedUntilEnoughData() {
        val s = meter(baseline = null, at(2) to 10_000.0, prior = 5.0, now = at(2))
        assertEquals(5.0, s.dailyRate)
        assertEquals(RateConfidence.PRIOR, s.rateConfidence)
    }

    @Test
    fun readingThatGoesDownIsFlaggedAndIgnored() {
        val s = meter(10_000.0, at(6) to 10_030.0, at(8) to 10_013.0, at(11) to 10_060.0)
        val issue = s.issues.filterIsInstance<LimitIssue.ReadingWentDown>().single()
        assertEquals(10_030.0, issue.previous)
        assertEquals(10_013.0, issue.current)
        assertEquals(60.0, s.usedSoFar, 1e-9)
    }

    @Test
    fun meterRolloverIsUnwrapped() {
        val s = meter(99_950.0, at(6) to 99_990.0, at(11) to 30.0, digits = 5)
        assertEquals(80.0, s.usedSoFar, 1e-9)
        assertTrue(s.issues.any { it is LimitIssue.RolloverAssumed })
    }

    @Test
    fun longGapIsSpreadAndFlagged() {
        val s = meter(10_000.0, at(14) to 10_078.0, now = at(14))
        assertEquals(6.0, s.dailyRate!!, 1e-9)
        assertTrue(s.issues.any { it is LimitIssue.LongGap })
    }

    @Test
    fun readingsBeforePeriodStartAreIgnored() {
        val old = LocalDateTime.of(2026, 9, 20, 12, 0)
        val s = meter(10_000.0, old to 9_900.0, at(6) to 10_030.0)
        assertEquals(30.0, s.usedSoFar, 1e-9)
        assertTrue(s.issues.none { it is LimitIssue.ReadingWentDown })
    }

    @Test
    fun readingDateSlipAddsDays() {
        val s = meter(10_000.0, at(6) to 10_030.0, at(11) to 10_060.0, at(16) to 10_090.0)
        val onTime = s.projectedAtPeriodEnd!!
        assertEquals(onTime + 18.0, s.projectedWithDelay(3)!!, 1e-6)
    }

    @Test
    fun afterPeriodEndNoAllowanceAndFlag() {
        val s = meter(10_000.0, at(20) to 10_120.0, now = LocalDateTime.of(2026, 11, 3, 9, 0))
        assertNull(s.dailyAllowance)
        assertTrue(s.issues.contains(LimitIssue.PeriodEnded))
        assertEquals(0.0, s.daysRemaining)
    }

    @Test
    fun budgetAdditiveModeProjectsMonthEnd() {
        val obs = (1..10).map { d -> Observation(at(d, 15), 500.0) }
        val s = LimitEngine.evaluate(
            LimitInput(
                limit = 15_000.0,
                periodStart = start,
                periodEnd = end,
                mode = LimitMode.ADDITIVE_AMOUNT,
                observations = obs,
                now = at(10, 20),
            ),
        )
        assertEquals(5_000.0, s.usedSoFar, 1e-9)
        assertEquals(500.0, s.dailyRate!!, 1e-6)
        assertNotNull(s.projectedAtPeriodEnd)
        // 5000 + 500 * ~21.2 days = ~15,600 > 15,000 -> DANGER.
        assertEquals(Risk.DANGER, s.risk)
    }

    @Test
    fun budgetWithNoSpendingIsSafe() {
        val s = LimitEngine.evaluate(
            LimitInput(15_000.0, 0.0, start, end, LimitMode.ADDITIVE_AMOUNT, observations = emptyList(), now = at(12)),
        )
        assertEquals(0.0, s.usedSoFar)
        assertEquals(Risk.SAFE, s.risk)
    }
}
