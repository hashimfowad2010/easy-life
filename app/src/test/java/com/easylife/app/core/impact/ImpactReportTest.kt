package com.easylife.app.core.impact

import com.easylife.app.core.data.prefs.UserPrefs
import com.easylife.app.feature.today.feedbackDue
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImpactReportTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private fun e(event: String, module: String, value: Double? = null, detail: String? = null, day: Long = 0) =
        ImpactReport.Event(event, module, value, detail, t0.plus(day, ChronoUnit.DAYS))

    private val events = listOf(
        e(ImpactEvents.APP_OPEN, "app"),
        e(ImpactEvents.APP_OPEN, "app", day = 1),
        e(ImpactEvents.LIMIT_WARNING_SHOWN, "limits", detail = "WATCH", day = 2),
        e(ImpactEvents.ACTION_AFTER_WARNING, "limits", day = 2),
        e(ImpactEvents.CYCLE_UNDER_AFTER_WARNING, "limits", 190.0, day = 20),
        e(ImpactEvents.BILL_PAID_BEFORE_DUE, "bills", day = 5),
        e(ImpactEvents.LATE_FEE_AVOIDED, "bills", 540.0, day = 5),
        e(ImpactEvents.DOC_READ, "urdu", day = 6),
        e(ImpactEvents.DOC_TRANSLATED, "urdu", day = 6),
        e(ImpactEvents.SHOPPING_COMPARED, "shopping", day = 7),
        e(ImpactEvents.SHOPPING_SAVINGS, "shopping", 320.0, day = 7),
        e(ImpactEvents.TX_EXCLUDED, "budget", day = 8),
        e(ImpactEvents.AI_RESULT, "ai", detail = "bill_extract:on_device:ok", day = 3),
        e(ImpactEvents.AI_RESULT, "ai", detail = "meter_read:on_device:ok", day = 3),
        e(ImpactEvents.AI_RESULT, "ai", detail = "bill_extract:claude:ok", day = 4),
        e(ImpactEvents.AI_RESULT, "ai", detail = "sms_parse:on_device:fail", day = 4),
        e(ImpactEvents.AI_RESULT, "ai", detail = "bill_extract:mock:ok", day = 4),
        // Corrections use Provenance names (upper case): the report merges them with provider keys.
        e(ImpactEvents.AI_CORRECTED, "ai", 2.0, "bill_extract:ON_DEVICE", day = 3),
        e(ImpactEvents.AI_CORRECTED, "ai", 0.0, "meter_read:ON_DEVICE", day = 3),
        e(ImpactEvents.AI_CORRECTED, "ai", 0.0, "bill_extract:CLAUDE", day = 4),
        e(ImpactEvents.FEEDBACK, "app", 4.0, "helped=electricity+bills;changed=yes", day = 9),
        e(ImpactEvents.FEEDBACK, "app", 5.0, "helped=documents;changed=no", day = 10),
    )

    @Test
    fun `highlights add up the spec section 8 events`() {
        val s = ImpactReport.build(events, "BSS-JTC", t0.plus(30, ChronoUnit.DAYS))
        val h = s.highlights
        assertEquals(1, h.warnings)
        assertEquals(1, h.actionsAfterWarning)
        assertEquals(1, h.cyclesUnderAfterWarning)
        assertEquals(1, h.billsPaidOnTime)
        assertEquals(540.0, h.lateFeesAvoidedRs)
        assertEquals(2, h.documents)
        assertEquals(1, h.comparisons)
        assertEquals(320.0, h.shoppingSavingsRs)
        assertEquals(1, h.transactionsExcluded)
        assertEquals(2, h.feedbackCount)
        assertEquals(4.5, h.averageRating)
        assertEquals(t0, s.from)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `AI sources count results, failures and corrections`() {
        val s = ImpactReport.build(events, null, t0)
        val onDevice = s.sources.first { it.source == "on_device" }
        assertEquals(2, onDevice.results)
        assertEquals(1, onDevice.failed)
        assertEquals(2, onDevice.confirmations)
        assertEquals(1, onDevice.corrected)
        assertEquals(50, onDevice.correctedPercent)
        val claude = s.sources.first { it.source == "claude" }
        assertEquals(1, claude.results)
        assertEquals(0, claude.correctedPercent)
        assertEquals(1, s.sources.first { it.source == "demo" }.results)
    }

    @Test
    fun `module activity leaves out app opens and AI bookkeeping`() {
        val s = ImpactReport.build(events, null, t0)
        assertEquals(setOf("limits", "bills", "urdu", "shopping", "budget", "app"), s.moduleActivity.keys)
        assertEquals(3, s.moduleActivity["limits"])
        // "app" only holds the two feedback events here.
        assertEquals(2, s.moduleActivity["app"])
    }

    @Test
    fun `csv has header, rows, sources and highlights`() {
        val csv = ImpactReport.csv(ImpactReport.build(events, "BSS-JTC", t0))
        val lines = csv.lines()
        assertEquals("pilot_code,BSS-JTC", lines[1])
        assertTrue("bills,late_fee_avoided_rs,1,540" in lines)
        assertTrue("limits,cycle_under_limit_after_warning,1,190" in lines)
        assertTrue("on_device,2,1,2,1,50" in lines)
        assertTrue("average_usefulness_1_to_5,4.5" in lines)
    }

    @Test
    fun `an empty phone gives an empty report`() {
        val s = ImpactReport.build(listOf(e(ImpactEvents.APP_OPEN, "app")), null, t0)
        assertTrue(s.isEmpty)
        assertNull(s.highlights.averageRating)
        assertTrue(s.sources.isEmpty())
    }

    @Test
    fun `feedback is asked after 3 days, snoozed for 3 more, never twice or in demo mode`() {
        val installed = t0
        val p = UserPrefs(installedAt = installed)
        assertFalse(feedbackDue(p, installed.plus(2, ChronoUnit.DAYS)))
        assertTrue(feedbackDue(p, installed.plus(3, ChronoUnit.DAYS)))
        val snoozed = p.copy(feedbackSnoozedAt = installed.plus(4, ChronoUnit.DAYS))
        assertFalse(feedbackDue(snoozed, installed.plus(6, ChronoUnit.DAYS)))
        assertTrue(feedbackDue(snoozed, installed.plus(7, ChronoUnit.DAYS)))
        assertFalse(feedbackDue(p.copy(feedbackDone = true), installed.plus(30, ChronoUnit.DAYS)))
        assertFalse(feedbackDue(p.copy(demoMode = true), installed.plus(30, ChronoUnit.DAYS)))
        assertFalse(feedbackDue(UserPrefs(), installed.plus(30, ChronoUnit.DAYS)))
    }
}
