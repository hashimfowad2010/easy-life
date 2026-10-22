package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.ai.schema.BillHistoryRow
import com.easylife.app.core.ocr.OcrDocument
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * On-device bill parser (free, offline). LESCO-style electricity bills first, generic fallback for everything else.
 *
 * Label positions on real LESCO bills are ASSUMPTIONS until checked with a real bill in Parser Lab (see PROGRESS.md).
 * The parser is deliberately tolerant: labels are fuzzy-matched, values are searched on the same row, the next row,
 * or under a header row, and dates/amounts have plausibility fallbacks. Anything found by a fallback is reported in
 * [BillExtractResult.uncertainFields] so the confirm screen highlights it.
 */
object BillParser {

    data class Detected(val billType: String, val provider: String?)

    private data class ProviderRule(val name: String, val type: String, val keywords: List<String>)

    private val providers = listOf(
        ProviderRule("LESCO", "electricity", listOf("LESCO", "LAHORE ELECTRIC")),
        ProviderRule("IESCO", "electricity", listOf("IESCO", "ISLAMABAD ELECTRIC")),
        ProviderRule("FESCO", "electricity", listOf("FESCO", "FAISALABAD ELECTRIC")),
        ProviderRule("GEPCO", "electricity", listOf("GEPCO", "GUJRANWALA ELECTRIC")),
        ProviderRule("MEPCO", "electricity", listOf("MEPCO", "MULTAN ELECTRIC")),
        ProviderRule("PESCO", "electricity", listOf("PESCO", "PESHAWAR ELECTRIC")),
        ProviderRule("HESCO", "electricity", listOf("HESCO", "HYDERABAD ELECTRIC")),
        ProviderRule("SEPCO", "electricity", listOf("SEPCO", "SUKKUR ELECTRIC")),
        ProviderRule("QESCO", "electricity", listOf("QESCO", "QUETTA ELECTRIC")),
        ProviderRule("TESCO", "electricity", listOf("TESCO", "TRIBAL ELECTRIC")),
        ProviderRule("K-Electric", "electricity", listOf("K-ELECTRIC", "K ELECTRIC", "KELECTRIC")),
        ProviderRule("SNGPL", "gas", listOf("SNGPL", "SUI NORTHERN")),
        ProviderRule("SSGC", "gas", listOf("SSGC", "SUI SOUTHERN")),
        ProviderRule("WASA", "water", listOf("WASA", "WATER AND SANITATION", "WATER & SANITATION")),
        ProviderRule("PTCL", "internet", listOf("PTCL", "PAKISTAN TELECOMMUNICATION")),
        ProviderRule("Nayatel", "internet", listOf("NAYATEL")),
        ProviderRule("StormFiber", "internet", listOf("STORMFIBER", "STORM FIBER")),
        ProviderRule("Jazz", "mobile_postpaid", listOf("JAZZ POSTPAID", "JAZZ")),
        ProviderRule("Zong", "mobile_postpaid", listOf("ZONG")),
        ProviderRule("Telenor", "mobile_postpaid", listOf("TELENOR")),
        ProviderRule("Ufone", "mobile_postpaid", listOf("UFONE")),
    )

    private val schoolWords = listOf("FEE VOUCHER", "FEE CHALLAN", "TUITION FEE", "SCHOOL", "COLLEGE", "ACADEMY", "BEACONHOUSE", "ADMISSION")

    fun detect(text: String): Detected {
        val upper = text.uppercase()
        providers.firstOrNull { p -> p.keywords.any { upper.contains(it) } }?.let { return Detected(it.type, it.name) }
        if (upper.contains("ELECTRIC") && (upper.contains("UNITS") || upper.contains("KWH"))) return Detected("electricity", null)
        if (schoolWords.any { upper.contains(it) }) {
            val name = text.lines().firstOrNull { l -> listOf("SCHOOL", "COLLEGE", "ACADEMY", "BEACONHOUSE").any { l.uppercase().contains(it) } }
            return Detected("school_fee", name?.trim()?.take(60))
        }
        if (upper.contains("GAS")) return Detected("gas", null)
        if (upper.contains("INTERNET") || upper.contains("BROADBAND")) return Detected("internet", null)
        if (upper.contains("RENT")) return Detected("rent", null)
        return Detected("other", null)
    }

    fun parse(doc: OcrDocument): BillExtractResult {
        val rows = doc.rows().map { Num.fixDigitsInText(it) }.filter { it.isNotBlank() }
        val detected = detect(rows.joinToString("\n"))
        return Extraction(rows, detected).run()
    }

    // ------------------------------------------------------------------ extraction

    private enum class DField { CONNECTION, BILL_MONTH, READING, ISSUE, DUE }

    private val dateLabels: List<Pair<DField, Array<String>>> = listOf(
        DField.CONNECTION to arrayOf("CONNECTION DATE", "CONN DATE"),
        DField.BILL_MONTH to arrayOf("BILL MONTH", "BILLING MONTH", "FOR THE MONTH OF", "BILL FOR"),
        DField.READING to arrayOf("READING DATE", "METER READING DATE"),
        DField.ISSUE to arrayOf("ISSUE DATE", "BILL DATE", "DATE OF ISSUE", "INVOICE DATE"),
        DField.DUE to arrayOf("DUE DATE", "DUEDATE", "LAST DATE", "PAY BY", "PAYMENT DUE"),
    )

    private val refRegex = Regex("""\b\d{2}\s?\d{5}\s?\d{7}(?:\s?[A-Z])?\b|\b\d{10,16}\b""")

    private class Extraction(val rows: List<String>, val detected: Detected) {
        val uncertain = mutableListOf<String>()
        val electricity = detected.billType == "electricity"

        var billingMonth: YearMonth? = null
        val dates = mutableMapOf<DField, LocalDate>()

        fun run(): BillExtractResult {
            extractDates()
            val referenceNo = extractReference()
            val (within, after) = extractAmounts()
            var units: Int? = null
            var previous: Double? = null
            var present: Double? = null
            var history: List<BillHistoryRow> = emptyList()
            if (electricity) {
                val readings = extractReadings()
                previous = readings.previous
                present = readings.present
                units = readings.units
                history = extractHistory()
            }
            if (billingMonth == null) {
                billingMonth = when {
                    history.isNotEmpty() && electricity -> YearMonth.parse(history.maxOf { it.month }).plusMonths(1)
                    dates[DField.READING] != null -> YearMonth.from(dates[DField.READING])
                    dates[DField.ISSUE] != null -> YearMonth.from(dates[DField.ISSUE])
                    else -> null
                }
                if (billingMonth != null) uncertain += "billingMonth"
            }
            val status = consumerStatus()

            val result = BillExtractResult(
                billType = detected.billType,
                provider = detected.provider,
                referenceNo = referenceNo,
                billingMonth = billingMonth?.toString(),
                issueDate = dates[DField.ISSUE]?.toString(),
                dueDate = dates[DField.DUE]?.toString(),
                readingDate = if (electricity) dates[DField.READING]?.toString() else null,
                amountWithinDue = within,
                amountAfterDue = after,
                unitsConsumed = units,
                previousReading = previous,
                presentReading = present,
                consumerStatusPrinted = status,
                history = history,
                confidence = 0.0,
                uncertainFields = emptyList(),
            )
            return result.copy(confidence = confidence(result), uncertainFields = uncertain.distinct())
        }

        private fun confidence(r: BillExtractResult): Double {
            val core = if (electricity) {
                listOf(r.dueDate, r.amountWithinDue, r.billingMonth, r.unitsConsumed, r.readingDate, r.presentReading)
            } else {
                listOf(r.dueDate, r.amountWithinDue, r.billingMonth, r.provider)
            }
            var score = core.count { it != null }.toDouble() / core.size * 0.75
            val within = r.amountWithinDue
            val after = r.amountAfterDue
            if (within != null && after != null && after > within) score += 0.07
            val issue = r.issueDate?.let(LocalDate::parse)
            val due = r.dueDate?.let(LocalDate::parse)
            if (issue != null && due != null && issue.isBefore(due)) score += 0.06
            if (electricity) {
                val prev = r.previousReading
                val pres = r.presentReading
                if (prev != null && pres != null && r.unitsConsumed != null && (pres - prev).toInt() == r.unitsConsumed) score += 0.07
                if (r.history.size >= 6) score += 0.05
            } else {
                score += 0.12
            }
            val penalty = 0.06 * uncertain.distinct().size
            return (score - penalty).coerceIn(0.05, 0.98)
        }

        // ---- dates

        private fun extractDates() {
            for ((i, row) in rows.withIndex()) {
                if (Labels.contains(row, "PAYABLE", "WITHIN", "AFTER DUE")) continue
                val found = dateLabels.mapNotNull { (field, variants) ->
                    Labels.find(row, *variants)?.let { field to it }
                }.filter { (field, _) -> field !in dates && !(field == DField.BILL_MONTH && billingMonth != null) }
                    .sortedBy { it.second.first }
                if (found.isEmpty()) continue
                if (found.size == 1) {
                    val (field, range) = found.single()
                    val sameRow = valuesIn(Labels.after(row, range))
                    val value = sameRow.firstOrNull { it.fits(field) }
                        ?: rows.getOrNull(i + 1)?.takeIf { next -> dateLabels.none { Labels.contains(next, *it.second) } }
                            ?.let { next -> valuesIn(next).firstOrNull { it.fits(field) } }
                    value?.let { assign(field, it) }
                } else {
                    // Header row: several labels; values sit in the same order on the following row(s).
                    val values = (1..2).flatMap { k -> rows.getOrNull(i + k)?.let { valuesIn(it) } ?: emptyList() }.toMutableList()
                    for ((field, _) in found) {
                        val idx = values.indexOfFirst { it.fits(field) }
                        if (idx >= 0) {
                            assign(field, values[idx])
                            repeat(idx + 1) { values.removeAt(0) }
                        }
                    }
                }
            }
            chronologicalFallback()
        }

        private sealed interface Value {
            fun fits(field: DField): Boolean
        }

        private data class DateValue(val date: LocalDate) : Value {
            override fun fits(field: DField) = field != DField.BILL_MONTH
        }

        private data class MonthValue(val month: YearMonth) : Value {
            override fun fits(field: DField) = field == DField.BILL_MONTH
        }

        private fun valuesIn(text: String): List<Value> {
            val ds = Dates.findAll(text)
            val months = Dates.findMonths(text).filter { m -> ds.none { it.range.first <= m.range.last && m.range.first <= it.range.last } }
            return (ds.map { it.range.first to DateValue(it.value) } + months.map { it.range.first to MonthValue(it.value) })
                .sortedBy { it.first }.map { it.second }
        }

        private fun assign(field: DField, v: Value) {
            when (v) {
                is DateValue -> dates[field] = v.date
                is MonthValue -> billingMonth = v.month
            }
        }

        /** When labels were not read, infer reading <= issue < due from the cluster of recent dates. */
        private fun chronologicalFallback() {
            val missing = listOf(DField.READING, DField.ISSUE, DField.DUE).filter { it !in dates && (electricity || it != DField.READING) }
            if (missing.isEmpty()) return
            val all = rows.flatMap { r -> Dates.findAll(r).map { it.value } }.distinct().sorted()
            val anchor = dates[DField.DUE] ?: dates[DField.ISSUE] ?: dates[DField.READING] ?: all.lastOrNull() ?: return
            // Bill dates sit within ~45 days of each other; older ones are connection dates etc.
            val cluster = all.filter { abs(ChronoUnit.DAYS.between(it, anchor)) <= 45 && it != dates[DField.CONNECTION] }
            if (cluster.isEmpty()) return
            if (DField.DUE in missing) {
                val issue = dates[DField.ISSUE]
                cluster.lastOrNull { issue == null || it.isAfter(issue) }?.let { dates[DField.DUE] = it; uncertain += "dueDate" }
            }
            val due = dates[DField.DUE]
            if (DField.ISSUE in missing && due != null) {
                cluster.lastOrNull { it.isBefore(due) && ChronoUnit.DAYS.between(it, due) in 3..25 }
                    ?.let { dates[DField.ISSUE] = it; uncertain += "issueDate" }
            }
            val issue = dates[DField.ISSUE]
            if (DField.READING in missing && issue != null) {
                cluster.firstOrNull { !it.isAfter(issue) && ChronoUnit.DAYS.between(it, issue) in 0..12 && it != dates[DField.ISSUE] }
                    ?.let { dates[DField.READING] = it; uncertain += "readingDate" }
            }
        }

        // ---- reference number (kept on the phone only)

        private fun extractReference(): String? {
            // Labels in priority order: the payment reference first, then other account identifiers.
            val labels = listOf(
                "REFERENCE NO", "REF NO", "REFERENCE NUMBER", "REFERENCE", "CONSUMER NO", "CONSUMER NUMBER",
                "ACCOUNT NO", "CUSTOMER ID", "CHALLAN NO", "VOUCHER NO",
            )
            for (label in labels) {
                for ((i, row) in rows.withIndex()) {
                    val r = Labels.find(row, label) ?: continue
                    val after = Labels.after(row, r)
                    (refRegex.find(after) ?: rows.getOrNull(i + 1)?.let { refRegex.find(it) })?.let { return it.value.trim() }
                }
            }
            return rows.firstNotNullOfOrNull { refRegex.find(it) }?.value?.trim()?.also { uncertain += "referenceNo" }
        }

        // ---- amounts

        /** First amount next to the highest-priority label that is present (labels are tried in order). */
        private fun amountNear(vararg labels: String): Double? {
            for (label in labels) {
                for ((i, row) in rows.withIndex()) {
                    val r = Labels.find(row, label) ?: continue
                    val after = Labels.after(row, r)
                    val same = Num.amounts(after).firstOrNull { it.value >= 1 }
                    if (same != null) return same.value
                    val next = rows.getOrNull(i + 1) ?: continue
                    Num.amounts(next).firstOrNull { it.value >= 1 }?.let { return it.value }
                }
            }
            return null
        }

        private fun extractAmounts(): Pair<Double?, Double?> {
            var within = amountNear(
                "PAYABLE WITHIN DUE DATE", "WITHIN DUE DATE", "AMOUNT WITHIN DUE", "PAYABLE WITHIN",
                "TOTAL PAYABLE", "NET PAYABLE", "AMOUNT PAYABLE", "AMOUNT DUE", "TOTAL DUE", "TOTAL AMOUNT", "TOTAL FEE",
            )
            var after = amountNear("PAYABLE AFTER DUE DATE", "AFTER DUE DATE", "PAYABLE AFTER", "LATE PAYMENT AMOUNT", "WITH FINE", "WITH LATE FEE")
            val lps = amountNear("L P SURCHARGE", "LP SURCHARGE", "LATE PAYMENT SURCHARGE", "LATE FEE", "FINE AFTER DUE DATE")
            if (within != null && after == null && lps != null && lps < within) after = within + lps
            if (within == null && after != null && lps != null && lps < after) within = after - lps

            if (within == null || after == null) {
                // Fallback: a pair where the later amount is 3-15% higher (late payment surcharge).
                val all = rows.flatMap { r -> Num.amounts(r).map { it.value } }.filter { it >= 50 }.distinct()
                val pair = all.flatMap { a -> all.map { b -> a to b } }
                    .filter { (a, b) -> b > a && b / a in 1.03..1.15 && (lps == null || abs(b - a - lps) <= 2) }
                    .maxByOrNull { it.first }
                if (pair != null) {
                    if (within == null) { within = pair.first; uncertain += "amountWithinDue" }
                    if (after == null) { after = pair.second; uncertain += "amountAfterDue" }
                }
            }
            if (within != null && after != null && after < within) uncertain += listOf("amountWithinDue", "amountAfterDue")
            return within to after
        }

        // ---- meter readings and units

        private data class Readings(val previous: Double?, val present: Double?, val units: Int?)

        private fun extractReadings(): Readings {
            var units: Int? = null
            for ((i, row) in rows.withIndex()) {
                val r = Labels.find(row, "UNITS CONSUMED", "UNIT CONSUMED", "CONSUMED UNITS", "TOTAL UNITS") ?: continue
                units = Num.integers(Labels.after(row, r)).firstOrNull { it.value in 0..99_999 }?.value?.toInt()
                    ?: rows.getOrNull(i + 1)?.let { n -> Num.integers(n).firstOrNull { it.value in 0..99_999 }?.value?.toInt() }
                if (units != null) break
            }

            var previous: Double? = null
            var present: Double? = null
            // Meter table: header "... PREVIOUS ... PRESENT ... MF ... UNITS" with values on the next row.
            for ((i, row) in rows.withIndex()) {
                if (!(Labels.contains(row, "PREVIOUS") && Labels.contains(row, "PRESENT"))) continue
                val sameRowNumbers = Num.integers(Labels.after(row, Labels.find(row, "PRESENT")!!)).map { it.value }
                val candidates = sameRowNumbers + (1..2).flatMap { k -> rows.getOrNull(i + k)?.let { n -> Num.integers(n).map { it.value } } ?: emptyList() }
                val pair = readingPair(candidates, units)
                if (pair != null) {
                    previous = pair.first.toDouble()
                    present = pair.second.toDouble()
                    break
                }
            }
            // Separate labelled rows: "PREVIOUS READING 10425" / "PRESENT READING 10611".
            if (present == null) {
                present = labelledInteger("PRESENT READING", "CURRENT READING", "PRESENT")?.toDouble()
                previous = labelledInteger("PREVIOUS READING", "PREVIOUS")?.toDouble()
            }
            val diff = if (present != null && previous != null && present >= previous) (present - previous).toInt() else null
            if (units == null && diff != null) {
                units = diff
                uncertain += "unitsConsumed"
            } else if (units != null && diff != null && diff != units) {
                // Could be a multiplying factor (MF) or an OCR slip; let the user check.
                uncertain += listOf("unitsConsumed", "presentReading", "previousReading")
            }
            return Readings(previous, present, units)
        }

        /** Two consecutive 3-7 digit numbers where present >= previous, preferring a difference equal to [units]. */
        private fun readingPair(numbers: List<Long>, units: Int?): Pair<Long, Long>? {
            val plausible = numbers.filter { it in 0..9_999_999 }
            var fallback: Pair<Long, Long>? = null
            for (k in 0 until plausible.size - 1) {
                val a = plausible[k]
                val b = plausible[k + 1]
                if (b < a || b - a > 5_000 || a < 100) continue
                if (units != null && b - a == units.toLong()) return a to b
                if (fallback == null) fallback = a to b
            }
            return fallback
        }

        private fun labelledInteger(vararg labels: String): Long? {
            for (row in rows) {
                val r = Labels.find(row, *labels) ?: continue
                Num.integers(Labels.after(row, r)).firstOrNull { it.value >= 100 }?.let { return it.value }
            }
            return null
        }

        // ---- 12-month history table

        private val historyRow = Regex("""(?<![A-Za-z])([A-Za-z0]{3,4})[\s\-']?(\d{2})\s+(\d{1,4})\s+([\d,]{2,9})(?:\s+([\d,]{1,9}))?""")

        private fun extractHistory(): List<BillHistoryRow> {
            val out = linkedMapOf<YearMonth, BillHistoryRow>()
            for (row in rows) {
                if (Labels.contains(row, "BILL MONTH", "READING DATE", "DUE DATE")) continue
                for (m in historyRow.findAll(row)) {
                    val month = Dates.monthOf(m.groupValues[1]) ?: continue
                    val year = 2000 + (m.groupValues[2].toIntOrNull() ?: continue)
                    val units = m.groupValues[3].toIntOrNull() ?: continue
                    val amount = m.groupValues[4].replace(",", "").toDoubleOrNull()
                    val ym = YearMonth.of(year, month)
                    if (ym !in out) out[ym] = BillHistoryRow(ym.toString(), units, amount)
                }
            }
            val sorted = out.values.sortedBy { it.month }
            // A real history table has consecutive months; drop strays far from the cluster.
            val latest = sorted.lastOrNull()?.let { YearMonth.parse(it.month) } ?: return emptyList()
            return sorted.filter { YearMonth.parse(it.month).let { ym -> !ym.isBefore(latest.minusMonths(13)) } }
        }

        private fun consumerStatus(): String {
            val text = rows.joinToString("\n")
            for (row in rows) {
                if (Labels.contains(row, "NON PROTECTED", "NONPROTECTED", "UN PROTECTED", "UNPROTECTED")) return "non_protected"
            }
            val upper = text.uppercase()
            if (Regex("""NON[\s-]?PROTECTED|UN[\s-]?PROTECTED""").containsMatchIn(upper)) return "non_protected"
            if (Regex("""(?<![A-Z])PROTECTED""").containsMatchIn(upper)) return "protected"
            return "unknown"
        }
    }
}
