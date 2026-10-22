package com.easylife.app.core.parse

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

/** A value found in text, with where it was found. */
data class Found<T>(val value: T, val range: IntRange, val raw: String)

/** Number helpers tolerant of OCR noise. */
object Num {
    private val digitLookalikes = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1', '!' to '1',
        'S' to '5', 's' to '5', 'B' to '8', 'Z' to '2', 'z' to '2',
    )

    /**
     * Fix letters inside tokens that are mostly digits: "2,4l2" -> "2,412", "1O425" -> "10425".
     * Tokens that are mostly letters ("OCT25", "A-1(01)T", "L123456") are left alone.
     */
    fun fixDigits(token: String): String {
        val digits = token.count { it.isDigit() }
        val lookalikes = token.count { it in digitLookalikes }
        val otherLetters = token.count { it.isLetter() && it !in digitLookalikes }
        if (digits == 0 || otherLetters > 0 || digits < lookalikes * 2) return token
        return token.map { digitLookalikes[it] ?: it }.joinToString("")
    }

    fun fixDigitsInText(text: String): String = Regex("""\S+""").replace(text) { fixDigits(it.value) }

    private val amountRegex = Regex(
        """(?<![\d.,])(?:(?:RS|PKR)\.?\s*)?(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?(?:\s*/-)?(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseAmount(raw: String): Double? {
        val m = amountRegex.find(raw.trim()) ?: return null
        val whole = m.groupValues[1].replace(",", "")
        val frac = m.groupValues[2]
        return (if (frac.isEmpty()) whole else "$whole.$frac").toDoubleOrNull()
    }

    /** All amounts in [text], skipping anything inside a date. */
    fun amounts(text: String): List<Found<Double>> {
        val dateRanges = Dates.findAll(text).map { it.range }
        return amountRegex.findAll(text)
            .filter { m -> dateRanges.none { it.first <= m.range.first && m.range.last <= it.last } }
            .mapNotNull { m ->
                val whole = m.groupValues[1].replace(",", "")
                val frac = m.groupValues[2]
                (if (frac.isEmpty()) whole else "$whole.$frac").toDoubleOrNull()?.let { Found(it, m.range, m.value) }
            }.toList()
    }

    /** Plain integers (no thousands separators or decimals) like readings and units. */
    fun integers(text: String): List<Found<Long>> =
        Regex("""(?<![\d.,])\d+(?![\d.,]\d)""").findAll(text).mapNotNull { m ->
            m.value.toLongOrNull()?.let { Found(it, m.range, m.value) }
        }.toList()
}

/** Dates as printed on Pakistani bills, receipts and circulars (day-first). */
object Dates {
    private val monthNames = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )

    /** "OCT", "0CT", "October", "SEPT", "N0V" -> month number. */
    fun monthOf(token: String): Int? {
        val t = token.uppercase().replace('0', 'O').replace('1', 'I').filter { it.isLetter() }
        if (t.length < 3) return null
        val key = t.take(3)
        val month = monthNames[key] ?: return null
        // Reject words that merely start like a month ("MARKET", "DECIDE"), unless they are a full month name.
        val full = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
        )[month - 1]
        return if (t.length == 3 || full.startsWith(t) || (t == "SEPT")) month else null
    }

    private fun year(raw: String): Int? {
        val y = raw.toIntOrNull() ?: return null
        val full = if (raw.length == 2) 2000 + y else y
        return full.takeIf { it in 1990..2100 }
    }

    private fun date(y: Int?, m: Int?, d: Int?): LocalDate? {
        if (y == null || m == null || d == null) return null
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }

    private val ymd = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val dmyNumeric = Regex("""(?<![\d.])(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?![\d.]\d)""")
    private val dMonY = Regex(
        """(?<![A-Za-z0-9])(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*(?:of\s+)?([A-Za-z0][A-Za-z0]{2,8})[\s\-/.,']*(\d{4}|\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )
    private val monDY = Regex(
        """(?<![A-Za-z])([A-Za-z0][A-Za-z]{2,8})[\s.\-]*(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    /** All dates in [text], longest match first when they overlap. */
    fun findAll(text: String): List<Found<LocalDate>> {
        val found = mutableListOf<Found<LocalDate>>()
        ymd.findAll(text).forEach { m ->
            date(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
                ?.let { found += Found(it, m.range, m.value) }
        }
        dmyNumeric.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            // Day-first is the Pakistani convention; switch only if that is impossible.
            val d = if (b > 12 && a <= 12) date(year(m.groupValues[3]), a, b) else date(year(m.groupValues[3]), b, a)
            d?.let { found += Found(it, m.range, m.value) }
        }
        dMonY.findAll(text).forEach { m ->
            date(year(m.groupValues[3]), monthOf(m.groupValues[2]), m.groupValues[1].toIntOrNull())
                ?.let { found += Found(it, m.range, m.value) }
        }
        monDY.findAll(text).forEach { m ->
            date(year(m.groupValues[3]), monthOf(m.groupValues[1]), m.groupValues[2].toIntOrNull())
                ?.let { found += Found(it, m.range, m.value) }
        }
        // Remove overlaps, keeping the longest match.
        val result = mutableListOf<Found<LocalDate>>()
        for (f in found.sortedByDescending { it.range.last - it.range.first }) {
            if (result.none { it.range.first <= f.range.last && f.range.first <= it.range.last }) result += f
        }
        return result.sortedBy { it.range.first }
    }

    fun parse(text: String): LocalDate? = findAll(text).firstOrNull()?.value

    private val monthToken = Regex("""(?<![A-Za-z])([A-Za-z0]{3,9})[\s\-/.']{0,2}(\d{4}|\d{2})(?!\d)""")

    /** Month-year tokens: "OCT25", "OCT 25", "0CT-2025", "October 2025". */
    fun findMonths(text: String): List<Found<YearMonth>> = monthToken.findAll(text).mapNotNull { m ->
        val month = monthOf(m.groupValues[1]) ?: return@mapNotNull null
        val y = year(m.groupValues[2]) ?: return@mapNotNull null
        Found(YearMonth.of(y, month), m.range, m.value)
    }.toList()
}

/** Finds printed labels in OCR rows, tolerating typical recognition mistakes ("DUE DAIE", "WlTHIN", "UNlTS"). */
object Labels {
    private val tokenRegex = Regex("""[A-Za-z0-9|!]+""")

    private fun norm(word: String): String = word.uppercase().map {
        when (it) {
            '0' -> 'O'
            '1', '|', '!' -> 'I'
            '5' -> 'S'
            else -> it
        }
    }.filter { it.isLetter() }.joinToString("")

    private fun wordMatches(actual: String, expected: String): Boolean {
        val a = norm(actual)
        val e = norm(expected)
        if (a == e) return true
        if (e.length < 4) return false
        return editDistance(a, e) <= 1
    }

    /** Range of the first occurrence of any [variants] (each a space-separated word sequence) in [row]. */
    fun find(row: String, vararg variants: String): IntRange? {
        val tokens = tokenRegex.findAll(row).toList()
        var best: IntRange? = null
        for (variant in variants) {
            val words = variant.split(' ').filter { it.isNotBlank() }
            for (i in tokens.indices) {
                if (i + words.size > tokens.size) break
                if (words.indices.all { k -> wordMatches(tokens[i + k].value, words[k]) }) {
                    val r = tokens[i].range.first..tokens[i + words.size - 1].range.last
                    if (best == null || r.first < best.first) best = r
                    break
                }
            }
        }
        return best
    }

    fun contains(row: String, vararg variants: String): Boolean = find(row, *variants) != null

    /** Text after the label on the same row. */
    fun after(row: String, range: IntRange): String = if (range.last + 1 >= row.length) "" else row.substring(range.last + 1)

    internal fun editDistance(a: String, b: String): Int {
        if (abs(a.length - b.length) > 1) return 2
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
