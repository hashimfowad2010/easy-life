package com.easylife.app.core.ai

import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.ai.schema.ClassifyResult
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.ai.schema.ListNormalizeResult
import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.ai.schema.SmsParseResult
import com.easylife.app.core.ai.schema.TranslateResult
import com.easylife.app.core.ai.schema.WeeklySummaryResult
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

/**
 * Parses and validates JSON from cloud providers (spec: "JSON from cloud providers is validated before use").
 * Structural problems -> [AiError.InvalidJson] (the router falls back). Implausible values are cleared and listed
 * in uncertainFields so the user is asked to check them.
 */
object CloudJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Pulls the first JSON object out of a reply that may be wrapped in ``` fences or text. */
    fun extractObject(raw: String): String? {
        val s = raw.trim().trimStart { it.code == 0xFEFF }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return s.substring(start, end + 1)
    }

    fun <R : Any> decode(task: AiTask<R>, raw: String?, source: ProviderId): AiResult<R> {
        if (raw.isNullOrBlank()) return AiResult.failure(source, AiError.InvalidJson("empty reply"))
        val obj = extractObject(raw) ?: return AiResult.failure(source, AiError.InvalidJson("no JSON object"))
        val value = try {
            json.decodeFromString(task.serializer, obj)
        } catch (e: Exception) {
            return AiResult.failure(source, AiError.InvalidJson(e.message?.take(160) ?: "parse error"))
        }
        return validate(task, value, source)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> validate(task: AiTask<R>, value: R, source: ProviderId): AiResult<R> {
        val extra = mutableListOf<String>()
        val checked: Any = when (value) {
            is ClassifyResult -> {
                if (value.kind !in ClassifyResult.KINDS) return AiResult.failure(source, AiError.InvalidJson("unknown kind ${value.kind}"))
                value.copy(confidence = clamp(value.confidence), alternatives = value.alternatives.filter { it in ClassifyResult.KINDS })
            }
            is MeterReadResult -> {
                val r = value.reading
                if (r != null && (r < 0 || r >= 10_000_000)) {
                    extra += "reading"
                    value.copy(reading = null, confidence = 0.0)
                } else {
                    value.copy(confidence = clamp(value.confidence))
                }
            }
            is BillExtractResult -> validateBill(value, extra)
            is ReceiptExtractResult -> value.copy(
                items = value.items.filter { it.name.isNotBlank() && (it.lineTotal ?: 0.0) >= 0 },
                total = value.total?.takeIf { it >= 0 }.also { if (value.total != null && it == null) extra += "total" },
                date = isoDate(value.date, "date", extra),
                confidence = clamp(value.confidence),
            )
            is SmsParseResult -> {
                if (value.isTransaction && (value.amount == null || value.amount <= 0)) extra += "amount"
                val dir = value.direction?.lowercase()?.takeIf { it == "debit" || it == "credit" }
                if (value.isTransaction && dir == null) extra += "direction"
                value.copy(direction = dir, confidence = clamp(value.confidence))
            }
            is TranslateResult -> {
                if (value.text.isBlank()) return AiResult.failure(source, AiError.InvalidJson("empty translation"))
                value.copy(confidence = clamp(value.confidence))
            }
            is DocExplainResult -> value.copy(
                actions = value.actions.map { it.copy(dueDate = isoDate(it.dueDate, "actions.dueDate", extra)) },
                confidence = clamp(value.confidence),
            )
            is ListNormalizeResult -> value.copy(
                items = value.items.filter { it.nameEn.isNotBlank() }.map { if (it.qty <= 0) it.copy(qty = 1.0) else it },
                confidence = clamp(value.confidence),
            )
            is WeeklySummaryResult -> value
            else -> value
        }
        val (confidence, uncertain) = when (checked) {
            is ClassifyResult -> checked.confidence to checked.uncertainFields
            is MeterReadResult -> checked.confidence to checked.uncertainFields
            is BillExtractResult -> checked.confidence to checked.uncertainFields
            is ReceiptExtractResult -> checked.confidence to checked.uncertainFields
            is SmsParseResult -> checked.confidence to checked.uncertainFields
            is TranslateResult -> checked.confidence to emptyList()
            is DocExplainResult -> checked.confidence to emptyList()
            is ListNormalizeResult -> checked.confidence to emptyList()
            is WeeklySummaryResult -> checked.confidence to emptyList()
            else -> 0.5 to emptyList()
        }
        val penalty = if (extra.isEmpty()) 1.0 else 0.7
        return AiResult(
            value = checked as R,
            confidence = clamp(confidence * penalty),
            uncertainFields = (uncertain + extra).distinct(),
            source = source,
        )
    }

    private fun validateBill(b: BillExtractResult, extra: MutableList<String>): BillExtractResult {
        fun money(v: Double?, name: String): Double? = v?.takeIf { it in 0.0..5_000_000.0 }.also { if (v != null && it == null) extra += name }
        val units = b.unitsConsumed?.takeIf { it in 0..100_000 }.also { if (b.unitsConsumed != null && it == null) extra += "unitsConsumed" }
        var result = b.copy(
            issueDate = isoDate(b.issueDate, "issueDate", extra),
            dueDate = isoDate(b.dueDate, "dueDate", extra),
            readingDate = isoDate(b.readingDate, "readingDate", extra),
            billingMonth = b.billingMonth?.takeIf { runCatching { YearMonth.parse(it) }.isSuccess }
                .also { if (b.billingMonth != null && it == null) extra += "billingMonth" },
            amountWithinDue = money(b.amountWithinDue, "amountWithinDue"),
            amountAfterDue = money(b.amountAfterDue, "amountAfterDue"),
            unitsConsumed = units,
            history = b.history.filter { runCatching { YearMonth.parse(it.month) }.isSuccess && (it.units ?: 0) in 0..100_000 },
            consumerStatusPrinted = b.consumerStatusPrinted.takeIf { it in setOf("protected", "non_protected", "unknown") } ?: "unknown",
            confidence = clamp(b.confidence),
        )
        val prev = result.previousReading
        val present = result.presentReading
        if (prev != null && present != null && units != null && present - prev != units.toDouble() && present >= prev) {
            // Could be a multiplying factor; ask the user to check rather than guess.
            extra += listOf("unitsConsumed", "presentReading", "previousReading")
        }
        val within = result.amountWithinDue
        val after = result.amountAfterDue
        if (within != null && after != null && after < within) extra += "amountAfterDue"
        val due = result.dueDate?.let(LocalDate::parse)
        val issue = result.issueDate?.let(LocalDate::parse)
        if (due != null && issue != null && due.isBefore(issue)) {
            extra += listOf("dueDate", "issueDate")
            result = result.copy(confidence = result.confidence * 0.8)
        }
        return result
    }

    private fun isoDate(value: String?, field: String, extra: MutableList<String>): String? {
        if (value == null) return null
        return if (runCatching { LocalDate.parse(value) }.isSuccess) value else {
            extra += field
            null
        }
    }

    private fun clamp(v: Double): Double = if (v.isNaN()) 0.0 else v.coerceIn(0.0, 1.0)
}
