package com.easylife.app.core.ai.providers

import com.easylife.app.core.ai.AiError
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiProvider
import com.easylife.app.core.ai.AiResult
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Availability
import com.easylife.app.core.ai.ProviderId
import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.ai.schema.BillHistoryRow
import com.easylife.app.core.ai.schema.ClassifyResult
import com.easylife.app.core.ai.schema.DocAction
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.ai.schema.KeyDetail
import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.ai.schema.ReceiptItem
import com.easylife.app.core.ai.schema.SmsParseResult
import com.easylife.app.core.ai.schema.TranslateResult
import com.easylife.app.core.parse.ShoppingListParser
import com.easylife.app.core.parse.WeeklySummaryTemplates
import javax.inject.Inject

/**
 * Realistic canned results for Demo mode and tests ONLY. Every result is labelled "Demo data" in the UI
 * (Provenance.DEMO). The router never picks it from routing; callers must force it.
 * All names, numbers and references are fictional.
 */
class MockProvider @Inject constructor() : AiProvider {
    override val id = ProviderId.MOCK

    override fun supports(task: AiTask<*>): Boolean = true

    override suspend fun isAvailable(task: AiTask<*>): Availability = Availability.Yes

    override suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R> {
        val value: Any = when (task) {
            AiTask.ClassifyImage -> ClassifyResult(input.params["hint"] ?: "electricity_bill", listOf("document", "receipt"), 0.95)
            AiTask.MeterRead -> MeterReadResult(reading = input.params["demoReading"]?.toDoubleOrNull() ?: 10_697.0, digitsSeen = "10697", confidence = 0.93)
            AiTask.BillExtract -> demoBill
            AiTask.ReceiptExtract -> demoReceipt
            AiTask.SmsParse -> SmsParseResult(
                isTransaction = true, kind = "transaction", amount = 1_250.0, currency = "PKR", direction = "debit",
                channel = "card", merchant = "SAMPLE MART", confidence = 0.9,
            )
            AiTask.Translate -> TranslateResult(demoCircularUrdu, confidence = 0.9)
            AiTask.DocExplain -> demoCircularExplained
            AiTask.ListNormalize -> ShoppingListParser.parse(input.text.orEmpty())
            AiTask.WeeklySummary -> WeeklySummaryTemplates.build(input.params)
        }
        @Suppress("UNCHECKED_CAST")
        val typed = value as R
        val (confidence, uncertain) = confidenceOf(typed)
        if (task == AiTask.Translate && input.params["demo"] != "circular") return AiResult.failure(id, AiError.NotSupported)
        return AiResult(typed, confidence, uncertain, id)
    }

    companion object {
        /** The demo family's LESCO bill: crossed 200 units in July 2026, so it is non-protected now. */
        val demoBill = BillExtractResult(
            billType = "electricity",
            provider = "LESCO",
            referenceNo = "99 11234 0001234 U",
            billingMonth = "2026-10",
            issueDate = "2026-10-11",
            dueDate = "2026-10-23",
            readingDate = "2026-10-09",
            amountWithinDue = 6_407.0,
            amountAfterDue = 6_945.0,
            unitsConsumed = 186,
            previousReading = 10_425.0,
            presentReading = 10_611.0,
            consumerStatusPrinted = "non_protected",
            history = listOf(
                BillHistoryRow("2025-10", 152, 2_185.0), BillHistoryRow("2025-11", 118, 1_642.0),
                BillHistoryRow("2025-12", 97, 1_288.0), BillHistoryRow("2026-01", 104, 1_402.0),
                BillHistoryRow("2026-02", 99, 1_320.0), BillHistoryRow("2026-03", 121, 1_689.0),
                BillHistoryRow("2026-04", 148, 2_120.0), BillHistoryRow("2026-05", 176, 2_598.0),
                BillHistoryRow("2026-06", 195, 2_941.0), BillHistoryRow("2026-07", 201, 7_320.0),
                BillHistoryRow("2026-08", 188, 6_890.0), BillHistoryRow("2026-09", 183, 6_705.0),
            ),
            confidence = 0.96,
            uncertainFields = emptyList(),
        )

        val demoReceipt = ReceiptExtractResult(
            storeName = "SAMPLE MART",
            storeArea = "Model Town",
            date = "2026-10-20",
            items = listOf(
                ReceiptItem("Basmati Rice 5kg", 1.0, 5.0, "kg", 1_850.0, 1_850.0),
                ReceiptItem("Sugar 1kg", 2.0, 1.0, "kg", 330.0, 165.0),
                ReceiptItem("Dalda Cooking Oil 5 Litre", 1.0, 5.0, "l", 2_950.0, 2_950.0),
                ReceiptItem("Eggs 1 Dozen", 1.0, 1.0, "dozen", 390.0, 390.0),
                ReceiptItem("Milk 1L", 2.0, 1.0, "l", 440.0, 220.0),
            ),
            total = 5_960.0,
            confidence = 0.92,
        )

        val demoCircularUrdu = """
            سیمپل گرامر اسکول (فرضی)
            سرکلر نمبر 14/2026
            تاریخ: 20 اکتوبر 2026
            موضوع: سالانہ کھیلوں کا دن اور فیس کی یاد دہانی
            محترم والدین،
            سالانہ کھیلوں کا دن 5 نومبر 2026 کو صبح 9 بجے سے دوپہر 1 بجے تک اسکول گراؤنڈ میں ہوگا۔
            طلبہ ہاؤس ٹی شرٹ اور سفید جوگرز پہنیں۔ کھیلوں کی فیس Rs 1,500 نومبر کی فیس کے ساتھ جمع ہوگی۔
            دستخط شدہ اجازت نامہ جمع کروانے کی آخری تاریخ 28 اکتوبر 2026 ہے۔
            جن والدین نے اکتوبر کی فیس ادا نہیں کی وہ Rs 500 جرمانے سے بچنے کے لیے 31 اکتوبر 2026 سے پہلے ادا کریں۔
        """.trimIndent()

        val demoCircularExplained = DocExplainResult(
            docType = "school_circular",
            summaryUr = listOf(
                "اسکول کا کھیلوں کا دن 5 نومبر کو ہے۔",
                "بچہ ہاؤس ٹی شرٹ اور سفید جوگرز پہن کر جائے۔",
                "کھیلوں کی فیس Rs 1,500 نومبر کی فیس کے ساتھ دینی ہے۔",
                "اجازت نامہ 28 اکتوبر تک دستخط کر کے واپس بھیجیں۔",
                "اکتوبر کی فیس 31 اکتوبر سے پہلے دیں، ورنہ Rs 500 جرمانہ۔",
            ),
            actions = listOf(
                DocAction("اجازت نامے پر دستخط کر کے جمع کروائیں", "2026-10-28", null, "Sign and return the consent form"),
                DocAction("اکتوبر کی فیس ادا کریں", "2026-10-31", null, "Pay the October fee"),
                DocAction("کھیلوں کی فیس نومبر کی فیس کے ساتھ دیں", null, 1_500.0, "Add the sports fee to the November fee"),
            ),
            keyDetails = listOf(
                KeyDetail("کھیلوں کا دن", "2026-11-05", "Sports Day", "event"),
                KeyDetail("آخری تاریخ (اجازت نامہ)", "2026-10-28", "Consent form deadline", "deadline"),
                KeyDetail("آخری تاریخ (فیس)", "2026-10-31", "Fee deadline", "deadline"),
                KeyDetail("کھیلوں کی فیس", "1500.0", "Sports fee", "amount"),
                KeyDetail("جرمانہ", "500.0", "Late fee", "amount"),
            ),
            summaryEn = listOf(
                "Sports Day is on 5 November.",
                "Your child should wear the house T-shirt and white joggers.",
                "Sports fee Rs 1,500 comes with the November fee.",
                "Return the signed consent form by 28 October.",
                "Pay the October fee before 31 October to avoid a Rs 500 fine.",
            ),
            confidence = 0.9,
        )
    }
}
