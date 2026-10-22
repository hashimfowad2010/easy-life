package com.easylife.app.core.i18n

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number, money and date formatting for both languages.
 * Always Western digits (0-9). Currency is "Rs 12,500" in both languages.
 * In Urdu, numeric runs are wrapped in Unicode isolates so "142 / 200" is never visually reversed.
 */
object Fmt {
    private const val LRI = '⁦'
    private const val PDI = '⁩'

    private val symbols = DecimalFormatSymbols(Locale.US)

    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val monthsEnLong = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val monthsUr = listOf("جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون", "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر")
    private val weekdaysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val weekdaysUr = listOf("پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ", "اتوار")

    /** Wrap a left-to-right run (numbers, codes) so it renders correctly inside Urdu text. */
    fun ltr(text: String, lang: AppLanguage): String = if (lang.isRtl) "$LRI$text$PDI" else text

    fun number(value: Double, decimals: Int = 0): String {
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(pattern, symbols).format(value)
    }

    fun number(value: Int): String = DecimalFormat("#,##0", symbols).format(value)

    /** 1 decimal only when it matters: 5.2, 12, 0.4. */
    fun smart(value: Double): String =
        if (abs(value - value.roundToLong()) < 0.05 || abs(value) >= 100) number(value) else number(value, 1)

    fun rupees(amount: Double, lang: AppLanguage = AppLanguage.EN): String {
        val rounded = amount.roundToLong()
        val text = (if (rounded < 0) "-Rs " else "Rs ") + number(abs(rounded).toDouble())
        return ltr(text, lang)
    }

    fun rupeesExact(amount: Double, lang: AppLanguage = AppLanguage.EN): String {
        val text = "Rs " + number(amount, if (amount % 1.0 == 0.0) 0 else 2)
        return ltr(text, lang)
    }

    fun units(value: Double, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${smart(value)} units"
        AppLanguage.UR -> "${ltr(smart(value), lang)} یونٹ"
    }

    fun fraction(used: Double, limit: Double, lang: AppLanguage): String = ltr("${smart(used)} / ${smart(limit)}", lang)

    fun percent(fraction: Double, lang: AppLanguage): String = ltr("${(fraction * 100).roundToLong()}%", lang)

    fun date(d: LocalDate, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${d.dayOfMonth} ${monthsEn[d.monthValue - 1]} ${d.year}"
        AppLanguage.UR -> "${d.dayOfMonth} ${monthsUr[d.monthValue - 1]} ${d.year}"
    }

    fun shortDate(d: LocalDate, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${d.dayOfMonth} ${monthsEn[d.monthValue - 1]}"
        AppLanguage.UR -> "${d.dayOfMonth} ${monthsUr[d.monthValue - 1]}"
    }

    fun dayWithWeekday(d: LocalDate, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${weekdaysEn[d.dayOfWeek.value - 1]} ${shortDate(d, lang)}"
        AppLanguage.UR -> "${weekdaysUr[d.dayOfWeek.value - 1]}، ${shortDate(d, lang)}"
    }

    fun dateTime(t: LocalDateTime, lang: AppLanguage): String {
        val hour12 = ((t.hour + 11) % 12) + 1
        val minutes = t.minute.toString().padStart(2, '0')
        val time = when (lang) {
            AppLanguage.EN -> "$hour12:$minutes ${if (t.hour < 12) "am" else "pm"}"
            AppLanguage.UR -> "${ltr("$hour12:$minutes", lang)} ${if (t.hour < 12) "صبح" else if (t.hour < 17) "دوپہر" else "شام"}"
        }
        return "${shortDate(t.toLocalDate(), lang)}, $time"
    }

    fun month(m: YearMonth, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${monthsEn[m.monthValue - 1]} ${m.year}"
        AppLanguage.UR -> "${monthsUr[m.monthValue - 1]} ${m.year}"
    }

    fun monthShort(m: YearMonth, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> monthsEn[m.monthValue - 1]
        AppLanguage.UR -> monthsUr[m.monthValue - 1]
    }

    fun monthLong(m: YearMonth, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${monthsEnLong[m.monthValue - 1]} ${m.year}"
        AppLanguage.UR -> "${monthsUr[m.monthValue - 1]} ${m.year}"
    }

    /** "today", "tomorrow", "in 3 days", "2 days ago". */
    fun relativeDay(target: LocalDate, today: LocalDate, lang: AppLanguage): String {
        val n = ChronoUnit.DAYS.between(today, target)
        return when (lang) {
            AppLanguage.EN -> when {
                n == 0L -> "today"
                n == 1L -> "tomorrow"
                n == -1L -> "yesterday"
                n > 1 -> "in $n days"
                else -> "${-n} days ago"
            }
            AppLanguage.UR -> when {
                n == 0L -> "آج"
                n == 1L -> "کل"
                n == -1L -> "کل (گزشتہ)"
                n > 1 -> "${ltr(n.toString(), lang)} دن میں"
                else -> "${ltr((-n).toString(), lang)} دن پہلے"
            }
        }
    }

    fun days(n: Long, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> if (n == 1L) "1 day" else "$n days"
        AppLanguage.UR -> "${ltr(n.toString(), lang)} دن"
    }

    fun hours(h: Double, lang: AppLanguage): String = when (lang) {
        AppLanguage.EN -> "${smart(h)} h"
        AppLanguage.UR -> "${ltr(smart(h), lang)} گھنٹے"
    }
}
