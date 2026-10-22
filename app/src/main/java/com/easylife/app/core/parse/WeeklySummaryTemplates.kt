package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.WeeklySummaryResult
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt

/**
 * On-device `weekly_summary`: template sentences from aggregated, rounded numbers only (never raw data).
 *
 * Params (all optional): units_used, units_limit, units_projected, days_left, bills_due, next_bill_provider,
 * next_bill_days, next_bill_amount, budget_category_en, budget_category_ur, budget_pct, spend_week, spend_prev_week,
 * docs_read, savings_estimate.
 */
object WeeklySummaryTemplates {

    fun build(p: Map<String, String>): WeeklySummaryResult {
        val en = mutableListOf<String>()
        val ur = mutableListOf<String>()
        val UR = AppLanguage.UR
        fun d(key: String) = p[key]?.toDoubleOrNull()

        val used = d("units_used")
        val limit = d("units_limit")
        val projected = d("units_projected")
        if (used != null && limit != null) {
            if (projected != null && projected > limit) {
                en += "Electricity: ${Fmt.smart(used)} of ${Fmt.smart(limit)} units used. At this pace you may reach ${Fmt.smart(projected)} — time to cut down."
                ur += "بجلی: ${Fmt.ltr(Fmt.smart(used), UR)} یونٹ استعمال ہو چکے (حد ${Fmt.ltr(Fmt.smart(limit), UR)})۔ اسی رفتار سے ${Fmt.ltr(Fmt.smart(projected), UR)} تک پہنچ سکتے ہیں — بچت شروع کریں۔"
            } else {
                en += "Electricity: ${Fmt.smart(used)} of ${Fmt.smart(limit)} units used. You are on track to stay under the limit."
                ur += "بجلی: ${Fmt.ltr(Fmt.smart(used), UR)} یونٹ استعمال ہوئے (حد ${Fmt.ltr(Fmt.smart(limit), UR)})۔ آپ حد کے اندر رہنے کے راستے پر ہیں۔"
            }
        }
        val billDays = d("next_bill_days")
        val billProvider = p["next_bill_provider"]
        if (billDays != null && billProvider != null) {
            val days = billDays.toLong()
            val amount = d("next_bill_amount")
            val amountEn = amount?.let { " (${Fmt.rupees(it)})" } ?: ""
            val amountUr = amount?.let { " (${Fmt.rupees(it, UR)})" } ?: ""
            en += "$billProvider bill$amountEn is due ${Fmt.relativeDay(java.time.LocalDate.now().plusDays(days), java.time.LocalDate.now(), AppLanguage.EN)}."
            ur += "$billProvider کا بل$amountUr ${Fmt.relativeDay(java.time.LocalDate.now().plusDays(days), java.time.LocalDate.now(), UR)} جمع کروانا ہے۔"
        }
        val pct = d("budget_pct")
        val catEn = p["budget_category_en"]
        val catUr = p["budget_category_ur"]
        if (pct != null && catEn != null && catUr != null && pct >= 70) {
            en += "$catEn budget is ${pct.toInt()}% used."
            ur += "$catUr کا بجٹ ${Fmt.ltr("${pct.toInt()}%", UR)} استعمال ہو چکا ہے۔"
        }
        val week = d("spend_week")
        val prev = d("spend_prev_week")
        if (week != null && prev != null && prev > 0) {
            val change = ((week - prev) / prev * 100).toInt()
            if (change >= 10) {
                en += "You spent ${Fmt.rupees(week)} this week, $change% more than last week."
                ur += "اس ہفتے ${Fmt.rupees(week, UR)} خرچ ہوئے، پچھلے ہفتے سے ${Fmt.ltr("$change%", UR)} زیادہ۔"
            } else if (change <= -10) {
                en += "Well done: you spent ${-change}% less than last week."
                ur += "شاباش: پچھلے ہفتے سے ${Fmt.ltr("${-change}%", UR)} کم خرچ ہوا۔"
            }
        }
        val docs = d("docs_read")
        if (docs != null && docs >= 1) {
            en += "Urdu Helper read ${docs.toInt()} document(s) for you."
            ur += "اردو مددگار نے آپ کے لیے ${Fmt.ltr(docs.toInt().toString(), UR)} دستاویز پڑھی۔"
        }
        if (en.isEmpty()) {
            en += "All calm this week. Snap your meter every few days so Easy Life can warn you early."
            ur += "اس ہفتے سب ٹھیک رہا۔ ہر چند دن بعد میٹر کی تصویر لیں تاکہ آسان زندگی آپ کو پہلے خبردار کر سکے۔"
        }
        return WeeklySummaryResult(en.take(4), ur.take(4), 1.0)
    }
}
