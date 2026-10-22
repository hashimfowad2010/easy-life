package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.ClassifyResult

/**
 * On-device `classify_image`: keyword and layout heuristics on OCR text. The user always confirms with one tap
 * and can pick another kind.
 */
object ImageClassifier {

    private val keywordScores: Map<String, List<Pair<String, Double>>> = mapOf(
        "electricity_bill" to listOf(
            "LESCO" to 5.0, "ELECTRIC SUPPLY" to 4.0, "ELECTRICITY" to 3.0, "UNITS CONSUMED" to 4.0, "KWH" to 1.5,
            "PRESENT READING" to 3.0, "PREVIOUS READING" to 3.0, "METER NO" to 2.0, "IESCO" to 5.0, "FESCO" to 5.0,
            "GEPCO" to 5.0, "MEPCO" to 5.0, "PESCO" to 5.0, "HESCO" to 5.0, "SEPCO" to 5.0, "QESCO" to 5.0,
            "K-ELECTRIC" to 5.0, "ELECTRICITY DUTY" to 3.0, "READING DATE" to 2.0, "TARIFF" to 1.0,
        ),
        "gas_bill" to listOf("SNGPL" to 5.0, "SUI NORTHERN" to 5.0, "SSGC" to 5.0, "SUI SOUTHERN" to 5.0, "GAS" to 1.5, "HM3" to 3.0, "MMBTU" to 3.0),
        "water_bill" to listOf("WASA" to 5.0, "WATER" to 1.5, "SEWERAGE" to 3.0, "CONSERVANCY" to 2.0),
        "internet_bill" to listOf(
            "PTCL" to 5.0, "NAYATEL" to 5.0, "STORMFIBER" to 5.0, "STORM FIBER" to 5.0, "BROADBAND" to 3.0,
            "INTERNET" to 2.0, "FIBER" to 1.5, "FLASH FIBER" to 4.0,
        ),
        "mobile_bill" to listOf("POSTPAID" to 4.0, "JAZZ" to 2.5, "ZONG" to 2.5, "TELENOR" to 2.5, "UFONE" to 2.5),
        "school_fee" to listOf(
            "FEE VOUCHER" to 5.0, "FEE CHALLAN" to 5.0, "TUITION" to 3.0, "SCHOOL" to 2.0, "COLLEGE" to 2.0,
            "ADMISSION" to 2.0, "STUDENT" to 2.0, "BEACONHOUSE" to 3.0, "ROLL NO" to 2.0, "CLASS" to 0.5,
        ),
        "receipt" to listOf(
            "TOTAL" to 1.5, "SUBTOTAL" to 2.5, "SUB TOTAL" to 2.5, "CASH" to 1.5, "CHANGE" to 2.0, "QTY" to 2.0,
            "RECEIPT" to 3.0, "INVOICE" to 1.5, "THANK YOU" to 2.0, "CASHIER" to 3.0, "POS" to 1.0, "ITEMS" to 1.0,
            "FBR" to 1.5, "GST" to 0.5,
        ),
        "price_tag" to listOf("MRP" to 3.0, "PRICE" to 1.5, "RS." to 1.0, "PER KG" to 2.0, "OFFER" to 1.0, "SAVE" to 1.0),
        "meter" to listOf("KWH" to 3.0, "IMP/KWH" to 4.0, "IMP" to 1.0, "50HZ" to 2.5, "230V" to 2.0, "220V" to 2.0, "CLASS 1" to 2.0, "IEC" to 2.0),
        "document" to listOf(
            "DEAR PARENTS" to 4.0, "CIRCULAR" to 4.0, "NOTICE" to 2.5, "SUBJECT" to 2.5, "REGARDS" to 2.5, "PRINCIPAL" to 2.5,
            "PLEASE" to 1.0, "KINDLY" to 1.5, "INFORMED" to 1.5, "PRESCRIPTION" to 3.0, "TABLET" to 1.5, "AGREEMENT" to 2.5,
            "APPLICATION" to 1.5, "DEAR" to 1.5, "SINCERELY" to 2.0, "LAST DATE" to 0.5,
        ),
    )

    fun classify(text: String): ClassifyResult {
        val upper = text.uppercase()
        if (upper.isBlank()) return ClassifyResult("unknown", listOf("document", "meter"), 0.0)

        val scores = keywordScores.mapValues { (_, kws) -> kws.sumOf { (kw, w) -> if (upper.contains(kw)) w else 0.0 } }.toMutableMap()

        // Every bill has a due date and an amount payable.
        val billish = listOf("DUE DATE", "PAYABLE", "AMOUNT DUE", "LAST DATE", "BILL MONTH").count { upper.contains(it) }
        listOf("electricity_bill", "gas_bill", "water_bill", "internet_bill", "mobile_bill", "school_fee").forEach {
            if ((scores[it] ?: 0.0) > 0) scores[it] = scores.getValue(it) + billish * 1.0
        }
        val generalBill = billish * 1.2

        val lines = text.lines().filter { it.isNotBlank() }
        val words = Regex("""[A-Za-z]{2,}""").findAll(text).count()
        val digits = text.count { it.isDigit() }
        val priceLines = lines.count { Regex("""[A-Za-z].*\s\d[\d,]*(\.\d{1,2})?\s*$""").containsMatchIn(it) }

        // Layout cues.
        if (priceLines >= 3) scores["receipt"] = (scores["receipt"] ?: 0.0) + priceLines * 0.6
        if (lines.size <= 5 && words <= 12 && Regex("""\d{4,7}""").containsMatchIn(text)) scores["meter"] = (scores["meter"] ?: 0.0) + 2.5
        if (lines.size <= 6 && words in 1..15 && Regex("""(RS\.?|PKR)\s*\d""").containsMatchIn(upper)) scores["price_tag"] = (scores["price_tag"] ?: 0.0) + 2.0
        if (words >= 60 && digits < words) scores["document"] = (scores["document"] ?: 0.0) + 2.0 + words / 100.0
        if (scores.values.all { it < 1.0 } && generalBill >= 2.0) scores["other_bill"] = generalBill

        val ranked = scores.filterValues { it > 0 }.toList().sortedByDescending { it.second }
        if (ranked.isEmpty()) {
            return ClassifyResult(if (words >= 15) "document" else "unknown", listOf("document", "receipt", "meter"), 0.3)
        }
        val (best, bestScore) = ranked.first()
        val second = ranked.getOrNull(1)?.second ?: 0.0
        val confidence = (0.4 + 0.5 * (bestScore - second) / (bestScore + 1.0)).coerceIn(0.2, 0.95)
        val alternatives = (ranked.drop(1).map { it.first } + listOf("document", "receipt", "meter"))
            .filter { it != best }.distinct().take(3)
        return ClassifyResult(best, alternatives, confidence)
    }
}
