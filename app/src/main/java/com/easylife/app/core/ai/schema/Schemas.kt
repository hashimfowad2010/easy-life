package com.easylife.app.core.ai.schema

import kotlinx.serialization.Serializable

// One data class per AI task. These mirror the JSON schemas in ai-prompts/schemas; every provider returns them.

@Serializable
data class ClassifyResult(
    /** electricity_bill, gas_bill, water_bill, internet_bill, mobile_bill, school_fee, other_bill, meter, receipt, price_tag, document, unknown */
    val kind: String,
    val alternatives: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val uncertainFields: List<String> = emptyList(),
) {
    companion object {
        val KINDS = listOf(
            "electricity_bill", "gas_bill", "water_bill", "internet_bill", "mobile_bill", "school_fee", "other_bill",
            "meter", "receipt", "price_tag", "document", "unknown",
        )
    }
}

@Serializable
data class MeterReadResult(
    val reading: Double? = null,
    val digitsSeen: String = "",
    val unit: String = "kWh",
    val confidence: Double = 0.0,
    val issues: List<String> = emptyList(),
    val uncertainFields: List<String> = emptyList(),
    /** Other plausible readings the phone saw (on-device only), best first. */
    val candidates: List<Double> = emptyList(),
)

@Serializable
data class BillHistoryRow(val month: String, val units: Int? = null, val amount: Double? = null)

@Serializable
data class BillExtractResult(
    val billType: String = "other",
    val provider: String? = null,
    val referenceNo: String? = null,
    val billingMonth: String? = null,
    val issueDate: String? = null,
    val dueDate: String? = null,
    val readingDate: String? = null,
    val amountWithinDue: Double? = null,
    val amountAfterDue: Double? = null,
    val unitsConsumed: Int? = null,
    val previousReading: Double? = null,
    val presentReading: Double? = null,
    val consumerStatusPrinted: String = "unknown",
    val history: List<BillHistoryRow> = emptyList(),
    val confidence: Double = 0.0,
    val uncertainFields: List<String> = emptyList(),
)

@Serializable
data class ReceiptItem(
    val name: String,
    val qty: Double = 1.0,
    val packSize: Double? = null,
    val unit: String? = null,
    val lineTotal: Double? = null,
    val unitPrice: Double? = null,
)

@Serializable
data class ReceiptExtractResult(
    val storeName: String? = null,
    val storeArea: String? = null,
    val date: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    val total: Double? = null,
    val confidence: Double = 0.0,
    val uncertainFields: List<String> = emptyList(),
)

@Serializable
data class SmsParseResult(
    val isTransaction: Boolean,
    /** transaction, security, promo, other */
    val kind: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    /** debit or credit */
    val direction: String? = null,
    val channel: String? = null,
    val counterparty: String? = null,
    val merchant: String? = null,
    val referenceId: String? = null,
    val balanceAfter: Double? = null,
    val timestamp: String? = null,
    val isRefund: Boolean? = null,
    val confidence: Double = 0.0,
    val uncertainFields: List<String> = emptyList(),
    /** On-device only: which template matched. */
    val templateId: String? = null,
)

@Serializable
data class TranslateResult(
    val text: String,
    val notes: List<String> = emptyList(),
    val confidence: Double = 0.0,
)

@Serializable
data class DocAction(val textUr: String, val dueDate: String? = null, val amount: Double? = null, val textEn: String? = null)

@Serializable
data class KeyDetail(val labelUr: String, val value: String, val labelEn: String? = null, val kind: String? = null)

@Serializable
data class DocExplainResult(
    val docType: String = "other",
    val summaryUr: List<String> = emptyList(),
    val actions: List<DocAction> = emptyList(),
    val keyDetails: List<KeyDetail> = emptyList(),
    val summaryEn: List<String> = emptyList(),
    val confidence: Double = 0.0,
)

@Serializable
data class ListItemResult(
    val nameEn: String,
    val nameUr: String,
    val itemKey: String,
    val qty: Double = 1.0,
    val unit: String? = null,
    val brandPref: String? = null,
    val category: String = "other",
    /** On-device only: the text this item came from. */
    val rawText: String? = null,
    val recognized: Boolean = true,
)

@Serializable
data class ListNormalizeResult(val items: List<ListItemResult>, val confidence: Double = 0.0)

@Serializable
data class WeeklySummaryResult(
    val linesEn: List<String>,
    val linesUr: List<String>,
    val confidence: Double = 1.0,
)
