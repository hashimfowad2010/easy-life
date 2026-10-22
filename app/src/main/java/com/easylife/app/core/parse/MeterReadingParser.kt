package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.ocr.OcrDocument
import kotlin.math.abs
import kotlin.math.max

/**
 * Reads the kWh counter from OCR of a meter photo (on-device, free).
 * Picks the most plausible 4-6 digit number: big digits, near "kWh", not a serial number/year/voltage,
 * and consistent with the previous reading. Below 0.8 confidence the confirm screen forces a manual check.
 */
object MeterReadingParser {

    data class Context(
        val previousReading: Double? = null,
        /** Days since the previous reading (for a plausibility window). */
        val daysSincePrevious: Double? = null,
        val meterDigits: Int? = null,
    )

    private data class Candidate(
        val digits: String,
        val value: Long,
        val heightRatio: Double,
        val lineText: String,
        var score: Double = 0.0,
        var decimalDropped: Boolean = false,
    )

    private val badContext = listOf("NO", "S/N", "SR", "SERIAL", "TYPE", "MODEL", "CLASS", "IMP", "HZ", "V ", "VOLT", "AMP", "YEAR", "MFG", "IEC", "REV")

    fun parse(doc: OcrDocument, ctx: Context = Context()): MeterReadResult {
        val maxHeight = doc.lines.mapNotNull { it.box?.height }.maxOrNull()?.toDouble()
        val candidates = mutableListOf<Candidate>()
        for (line in doc.lines) {
            val text = Num.fixDigitsInText(line.text)
            val heightRatio = if (maxHeight != null && line.box != null) line.box.height / maxHeight else 0.5
            // Join drum digits split by single spaces: "0 1 2 3 4" -> "01234".
            val joined = Regex("""(?<!\d)(?:\d\s){3,6}\d(?!\d)""").replace(text) { it.value.replace(" ", "") }
            for (m in Regex("""(?<![\d.,])\d{4,7}(?:\.\d)?(?![\d,])""").findAll(joined)) {
                val raw = m.value
                val intPart = raw.substringBefore('.')
                candidates += Candidate(intPart, intPart.toLong(), heightRatio, text.uppercase())
            }
        }
        if (candidates.isEmpty()) {
            return MeterReadResult(reading = null, digitsSeen = "", confidence = 0.0, issues = listOf("no_digits_found"), uncertainFields = listOf("reading"))
        }

        val prev = ctx.previousReading
        for (c in candidates) {
            var s = 0.0
            s += when (c.digits.length) {
                5 -> 3.0
                6 -> 2.0
                4 -> 1.0
                else -> -1.0
            }
            if (ctx.meterDigits != null && c.digits.length == ctx.meterDigits) s += 1.5
            s += c.heightRatio * 3.0
            if (c.lineText.contains("KWH")) s += 2.0
            if (badContext.any { c.lineText.contains(it) }) s -= 3.0
            if (c.digits.length == 4 && c.value in 1990..2035) s -= 2.0
            if (c.lineText.length > c.digits.length + 18) s -= 1.0 // long text lines are labels, not the display

            if (prev != null) {
                val maxPerDay = 60.0
                val window = max(1.0, ctx.daysSincePrevious ?: 30.0) * maxPerDay + 5
                val diff = c.value - prev
                when {
                    diff >= 0 && diff <= window -> s += 4.0 - 2.0 * (diff / window)
                    diff < 0 -> {
                        // A trailing decimal drum read as a digit? (0123456 instead of 012345.6)
                        val dropped = c.value / 10
                        if (c.digits.length >= 5 && dropped >= prev && dropped - prev <= window) {
                            c.decimalDropped = true
                            s += 2.5
                        } else {
                            s -= 3.0
                        }
                    }
                    else -> {
                        val dropped = c.value / 10
                        if (dropped >= prev && dropped - prev <= window) {
                            c.decimalDropped = true
                            s += 2.5
                        } else {
                            s -= 1.5
                        }
                    }
                }
            }
            c.score = s
        }
        val ranked = candidates.sortedByDescending { it.score }
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        val reading = if (best.decimalDropped) best.value / 10 else best.value
        val margin = if (second == null) 3.0 else best.score - second.score

        val issues = mutableListOf<String>()
        if (best.decimalDropped) issues += "decimal_digit_ignored"
        if (prev != null && reading < prev) issues += "lower_than_previous"
        if (second != null && margin < 1.0 && second.value != best.value) issues += "multiple_displays"

        var confidence = 0.45 + 0.12 * margin.coerceIn(0.0, 3.0) + if (best.heightRatio >= 0.9) 0.1 else 0.0
        if (prev != null && reading >= prev) confidence += 0.1
        if ("lower_than_previous" in issues) confidence = minOf(confidence, 0.4)
        if ("multiple_displays" in issues) confidence = minOf(confidence, 0.7)
        confidence = confidence.coerceIn(0.05, 0.97)

        return MeterReadResult(
            reading = reading.toDouble(),
            digitsSeen = if (best.decimalDropped) best.digits.dropLast(1) else best.digits,
            unit = "kWh",
            confidence = confidence,
            issues = issues,
            uncertainFields = if (confidence < 0.8) listOf("reading") else emptyList(),
            candidates = ranked.drop(1).map { (if (it.decimalDropped) it.value / 10 else it.value).toDouble() }
                .filter { abs(it - reading) > 0.5 }.distinct().take(3),
        )
    }
}
