package com.easylife.app.core.impact

import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Totals for one event in one module. [total] sums the (rounded) values, e.g. rupees saved. */
data class ImpactRow(val module: String, val event: String, val count: Int, val total: Double?)

/** How one AI source did: results, failures, and how often the user corrected what it read. */
data class SourceStats(val source: String, val results: Int, val failed: Int, val confirmations: Int, val corrected: Int) {
    val correctedPercent: Int get() = if (confirmations == 0) 0 else (corrected * 100.0 / confirmations).roundToInt()
}

data class ImpactHighlights(
    val warnings: Int,
    val actionsAfterWarning: Int,
    val cyclesUnderAfterWarning: Int,
    val billsPaidOnTime: Int,
    val lateFeesAvoidedRs: Double,
    val documents: Int,
    val comparisons: Int,
    val shoppingSavingsRs: Double,
    val transactionsExcluded: Int,
    val feedbackCount: Int,
    val averageRating: Double?,
)

data class ImpactSummary(
    val from: Instant?,
    val to: Instant,
    val pilotCode: String?,
    val rows: List<ImpactRow>,
    val sources: List<SourceStats>,
    val highlights: ImpactHighlights,
    /** Events per module, without app opens and AI bookkeeping (for the bar chart). */
    val moduleActivity: Map<String, Int>,
) {
    val isEmpty: Boolean get() = rows.none { it.event != ImpactEvents.APP_OPEN }
}

/**
 * Builds the "ILAP impact report" (spec section 8) from the events stored on the phone. Pure, so it is unit-tested;
 * the screen, CSV and PDF all use the same numbers.
 */
object ImpactReport {

    data class Event(val event: String, val module: String, val value: Double?, val detail: String?, val at: Instant)

    private val bookkeeping = setOf(ImpactEvents.APP_OPEN, ImpactEvents.AI_RESULT, ImpactEvents.AI_CORRECTED)

    /** "ON_DEVICE", "on_device", "mock" and "DEMO" all name the same few sources. */
    fun canonicalSource(raw: String): String = when (val s = raw.lowercase(Locale.US)) {
        "mock" -> "demo"
        else -> s
    }

    fun build(events: List<Event>, pilotCode: String?, now: Instant): ImpactSummary {
        val rows = events.groupBy { it.module to it.event }.map { (key, list) ->
            val values = list.mapNotNull { it.value }
            ImpactRow(key.first, key.second, list.size, if (values.isEmpty()) null else values.sum())
        }.sortedWith(compareBy({ it.module }, { it.event }))

        fun count(event: String) = events.count { it.event == event }
        fun sum(event: String) = events.filter { it.event == event }.sumOf { it.value ?: 0.0 }

        val results = events.filter { it.event == ImpactEvents.AI_RESULT }.mapNotNull { e ->
            val parts = e.detail?.split(':') ?: return@mapNotNull null
            if (parts.size < 3) null else Triple(canonicalSource(parts[1]), parts[2] == "ok", parts[0])
        }
        val corrections = events.filter { it.event == ImpactEvents.AI_CORRECTED }.mapNotNull { e ->
            val source = e.detail?.substringAfter(':', "")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            canonicalSource(source) to ((e.value ?: 0.0) > 0)
        }
        val sources = (results.map { it.first } + corrections.map { it.first }).distinct().map { s ->
            SourceStats(
                source = s,
                results = results.count { it.first == s && it.second },
                failed = results.count { it.first == s && !it.second },
                confirmations = corrections.count { it.first == s },
                corrected = corrections.count { it.first == s && it.second },
            )
        }.sortedByDescending { it.results }

        val ratings = events.filter { it.event == ImpactEvents.FEEDBACK }.mapNotNull { it.value }
        val highlights = ImpactHighlights(
            warnings = count(ImpactEvents.LIMIT_WARNING_SHOWN),
            actionsAfterWarning = count(ImpactEvents.ACTION_AFTER_WARNING),
            cyclesUnderAfterWarning = count(ImpactEvents.CYCLE_UNDER_AFTER_WARNING),
            billsPaidOnTime = count(ImpactEvents.BILL_PAID_BEFORE_DUE),
            lateFeesAvoidedRs = sum(ImpactEvents.LATE_FEE_AVOIDED),
            documents = count(ImpactEvents.DOC_READ) + count(ImpactEvents.DOC_TRANSLATED),
            comparisons = count(ImpactEvents.SHOPPING_COMPARED),
            shoppingSavingsRs = sum(ImpactEvents.SHOPPING_SAVINGS),
            transactionsExcluded = count(ImpactEvents.TX_EXCLUDED),
            feedbackCount = ratings.size,
            averageRating = if (ratings.isEmpty()) null else (ratings.average() * 10).roundToInt() / 10.0,
        )
        val moduleActivity = events.filter { it.event !in bookkeeping }.groupingBy { it.module }.eachCount()
            .toList().sortedByDescending { it.second }.toMap()
        return ImpactSummary(events.minOfOrNull { it.at }, now, pilotCode, rows, sources, highlights, moduleActivity)
    }

    /** CSV with a header block, per-module totals and AI source stats. Comma-safe (no free text). */
    fun csv(s: ImpactSummary): String = buildString {
        fun line(vararg cells: Any?) = appendLine(cells.joinToString(",") { c -> csvCell(c) })
        line("report", "Easy Life ILAP impact report")
        line("pilot_code", s.pilotCode ?: "")
        line("from", s.from?.toString() ?: "")
        line("to", s.to.toString())
        appendLine()
        line("module", "event", "count", "total_value")
        s.rows.forEach { line(it.module, it.event, it.count, it.total?.let(::plain)) }
        appendLine()
        line("ai_source", "results_ok", "results_failed", "confirmed_by_user", "corrected_by_user", "corrected_percent")
        s.sources.forEach { line(it.source, it.results, it.failed, it.confirmations, it.corrected, it.correctedPercent) }
        appendLine()
        val h = s.highlights
        line("highlight", "value")
        line("limit_warnings_shown", h.warnings)
        line("actions_after_warning", h.actionsAfterWarning)
        line("cycles_under_limit_after_warning", h.cyclesUnderAfterWarning)
        line("bills_paid_before_due", h.billsPaidOnTime)
        line("late_fees_avoided_rs", plain(h.lateFeesAvoidedRs))
        line("documents_read_or_translated", h.documents)
        line("shopping_comparisons", h.comparisons)
        line("shopping_savings_rs", plain(h.shoppingSavingsRs))
        line("transactions_excluded", h.transactionsExcluded)
        line("feedback_count", h.feedbackCount)
        line("average_usefulness_1_to_5", h.averageRating?.let(::plain) ?: "")
    }

    private fun plain(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private fun csvCell(c: Any?): String {
        val s = c?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
