package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.ai.schema.ReceiptItem
import com.easylife.app.core.ocr.OcrDocument
import kotlin.math.abs

/** Receipt and shelf-tag parser (on-device): "item name ... price" lines, pack sizes, total. */
object ReceiptParser {

    private val skipWords = listOf(
        "TOTAL", "SUBTOTAL", "SUB TOTAL", "CASH", "CHANGE", "TAX", "GST", "SALES TAX", "DISCOUNT", "BALANCE", "TEL", "PHONE",
        "NTN", "STRN", "INVOICE", "RECEIPT", "DATE", "TIME", "THANK", "VISA", "MASTER", "CARD", "POS", "CASHIER", "COUNTER",
        "ITEMS", "QTY", "PRICE", "AMOUNT", "TENDERED", "PAID", "FBR", "SERVICE", "ROUND", "BILL NO", "WWW", "HTTP",
    )

    private val skipPatterns = skipWords.map { Regex("""(?<![A-Z])${Regex.escape(it)}(?![A-Z])""") }

    /** Header lines that are not the store name ("Metro Cash & Carry" is still a valid name). */
    private val headerNoise = listOf("RECEIPT", "INVOICE", "TAX", "NTN", "STRN", "TEL", "PHONE", "DATE", "TIME", "WWW")
        .map { Regex("""(?<![A-Z])$it(?![A-Z])""") }

    // name  qty x unitPrice  lineTotal
    private val qtyTimes = Regex("""^(.*?[A-Za-z].*?)\s+(\d+(?:\.\d+)?)\s*[xX@*]\s*([\d,]+(?:\.\d{1,2})?)\s+([\d,]+(?:\.\d{1,2})?)\s*$""")

    // qty  name  lineTotal
    private val qtyFirst = Regex("""^(\d{1,3}(?:\.\d+)?)\s+(.*?[A-Za-z].*?)\s+([\d,]+(?:\.\d{1,2})?)\s*$""")

    // name  qty  unitPrice  lineTotal (three numbers at the end)
    private val threeNumbers = Regex("""^(.*?[A-Za-z].*?)\s+(\d{1,3}(?:\.\d+)?)\s+([\d,]+(?:\.\d{1,2})?)\s+([\d,]+(?:\.\d{1,2})?)\s*$""")

    // name  lineTotal
    private val nameTotal = Regex("""^(.*?[A-Za-z].*?)\s+(?:RS\.?\s*)?([\d,]+(?:\.\d{1,2})?)(?:\s*/-)?\s*$""", RegexOption.IGNORE_CASE)

    private val packRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(KG|KGS|G|GM|GMS|GRAM|GRAMS|LTR|LTRS|LITRE|LITRES|LITER|L|ML|DOZEN|DZN|DZ|PCS|PC|PACK|PKT)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun packOf(name: String): Pair<Double, String>? {
        val m = packRegex.find(name) ?: return null
        val size = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2].uppercase()) {
            "KG", "KGS" -> "kg"
            "G", "GM", "GMS", "GRAM", "GRAMS" -> "g"
            "LTR", "LTRS", "LITRE", "LITRES", "LITER", "L" -> "l"
            "ML" -> "ml"
            "DOZEN", "DZN", "DZ" -> "dozen"
            "PCS", "PC" -> "piece"
            else -> "packet"
        }
        return size to unit
    }

    fun parse(doc: OcrDocument): ReceiptExtractResult {
        val rows = doc.rows().map { Num.fixDigitsInText(it).replace(Regex("""\s{2,}"""), "  ").trim() }.filter { it.isNotBlank() }
        val uncertain = mutableListOf<String>()

        val storeName = rows.take(4).firstOrNull { r ->
            val letters = r.count { it.isLetter() }
            letters >= 3 && letters >= r.length * 0.6 && headerNoise.none { it.containsMatchIn(r.uppercase()) }
        }?.take(40)

        val date = rows.firstNotNullOfOrNull { Dates.findAll(it).firstOrNull()?.value }?.toString()

        val items = mutableListOf<ReceiptItem>()
        for (row in rows) {
            // Exact whole words only: fuzzy matching would drop real items ("RICE" ~ "PRICE", "CURD" ~ "CARD").
            if (skipPatterns.any { it.containsMatchIn(row.uppercase()) }) continue
            if (Dates.findAll(row).isNotEmpty()) continue
            val item = matchItem(row) ?: continue
            if (item.name.length < 2 || (item.lineTotal ?: 0.0) <= 0) continue
            items += item
        }

        val total = totalOf(rows)
        val sum = items.sumOf { it.lineTotal ?: 0.0 }
        var confidence = when {
            items.isEmpty() -> 0.1
            total == null -> 0.5
            abs(sum - total) <= total * 0.02 + 1 -> 0.9
            abs(sum - total) <= total * 0.15 -> 0.7
            else -> 0.45
        }
        if (total == null) uncertain += "total"
        if (total != null && items.isNotEmpty() && abs(sum - total) > total * 0.15) uncertain += "items"
        if (storeName == null) {
            uncertain += "storeName"
            confidence -= 0.05
        }
        return ReceiptExtractResult(
            storeName = storeName,
            storeArea = null,
            date = date,
            items = items,
            total = total,
            confidence = confidence.coerceIn(0.05, 0.95),
            uncertainFields = uncertain,
        )
    }

    private fun matchItem(row: String): ReceiptItem? {
        qtyTimes.find(row)?.let { m ->
            return build(m.groupValues[1], m.groupValues[2].toDoubleOrNull(), Num.parseAmount(m.groupValues[3]), Num.parseAmount(m.groupValues[4]))
        }
        threeNumbers.find(row)?.let { m ->
            val qty = m.groupValues[2].toDoubleOrNull()
            val unit = Num.parseAmount(m.groupValues[3])
            val line = Num.parseAmount(m.groupValues[4])
            if (qty != null && unit != null && line != null && abs(qty * unit - line) <= 1.0) return build(m.groupValues[1], qty, unit, line)
        }
        qtyFirst.find(row)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull()
            if (qty != null && qty in 1.0..99.0 && packRegex.find(m.groupValues[1] + " " + m.groupValues[2].take(3)) == null) {
                return build(m.groupValues[2], qty, null, Num.parseAmount(m.groupValues[3]))
            }
        }
        nameTotal.find(row)?.let { m ->
            return build(m.groupValues[1], 1.0, null, Num.parseAmount(m.groupValues[2]))
        }
        return null
    }

    private fun build(rawName: String, qty: Double?, unitPrice: Double?, lineTotal: Double?): ReceiptItem {
        val name = rawName.trim().trim('-', '.', ':', '*').replace(Regex("""\s+"""), " ")
        val pack = packOf(name)
        return ReceiptItem(
            name = name,
            qty = qty ?: 1.0,
            packSize = pack?.first,
            unit = pack?.second,
            lineTotal = lineTotal,
            unitPrice = unitPrice ?: if (qty != null && qty > 0 && lineTotal != null) lineTotal / qty else null,
        )
    }

    private fun totalOf(rows: List<String>): Double? {
        val labels = listOf("GRAND TOTAL", "NET TOTAL", "TOTAL AMOUNT", "AMOUNT DUE", "NET PAYABLE", "TOTAL")
        for (label in labels) {
            for ((i, row) in rows.withIndex()) {
                val r = Labels.find(row, label) ?: continue
                if (label == "TOTAL" && Labels.contains(row, "SUB TOTAL", "SUBTOTAL", "TOTAL ITEMS", "TOTAL QTY")) continue
                Num.amounts(Labels.after(row, r)).lastOrNull()?.let { return it.value }
                rows.getOrNull(i + 1)?.let { n -> Num.amounts(n).lastOrNull()?.let { return it.value } }
            }
        }
        return null
    }
}
