package com.easylife.app.core.parse

import com.easylife.app.core.ocr.OcrBox
import com.easylife.app.core.ocr.OcrDocument
import com.easylife.app.core.ocr.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeterReadingParserTest {

    private fun line(text: String, height: Int, y: Int) = OcrLine(text, OcrBox(10, y, 10 + text.length * 10, y + height))

    private val meterPhoto = OcrDocument(
        listOf(
            line("Class 1 IEC 62053-21", 20, 10),
            line("230V 10(60)A 50Hz", 20, 40),
            line("1600 imp/kWh", 20, 70),
            line("10611 kWh", 80, 120),
            line("No. 00123456", 20, 220),
        ),
    )

    @Test
    fun picksTheBigDisplayNumber() {
        val r = MeterReadingParser.parse(meterPhoto, MeterReadingParser.Context(previousReading = 10425.0, daysSincePrevious = 15.0))
        assertEquals(10611.0, r.reading)
        assertEquals("10611", r.digitsSeen)
        assertTrue(r.confidence >= 0.8, "confidence ${r.confidence}")
        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun lowerThanPreviousForcesManualCheck() {
        val doc = OcrDocument(listOf(line("10380 kWh", 80, 100)))
        val r = MeterReadingParser.parse(doc, MeterReadingParser.Context(previousReading = 10425.0, daysSincePrevious = 5.0))
        assertEquals(10380.0, r.reading)
        assertTrue("lower_than_previous" in r.issues)
        assertTrue(r.confidence < 0.8)
        assertEquals(listOf("reading"), r.uncertainFields)
    }

    @Test
    fun drumDigitsWithSpacesAreJoined() {
        val doc = OcrDocument(listOf(line("0 1 0 6 1 1", 80, 100), line("kWh", 20, 190)))
        val r = MeterReadingParser.parse(doc, MeterReadingParser.Context(previousReading = 10425.0, daysSincePrevious = 15.0))
        assertEquals(10611.0, r.reading)
        assertEquals("010611", r.digitsSeen)
    }

    @Test
    fun trailingDecimalDrumIsDropped() {
        val doc = OcrDocument(listOf(line("106114", 80, 100)))
        val r = MeterReadingParser.parse(doc, MeterReadingParser.Context(previousReading = 10425.0, daysSincePrevious = 15.0))
        assertEquals(10611.0, r.reading)
        assertTrue("decimal_digit_ignored" in r.issues)
    }

    @Test
    fun noDigits() {
        val r = MeterReadingParser.parse(OcrDocument.fromPlainText("kWh"))
        assertNull(r.reading)
        assertEquals(0.0, r.confidence)
    }
}

class ReceiptParserTest {
    @Test
    fun groceryReceipt() {
        val r = ReceiptParser.parse(OcrDocument.fromPlainText(fixture("receipts/grocery_receipt.txt")))
        assertEquals("SAMPLE MART (FICTIONAL)", r.storeName)
        assertEquals("2026-10-20", r.date)
        assertEquals(5960.0, r.total)
        assertEquals(5, r.items.size, "items: ${r.items.map { it.name }}")
        val rice = r.items.first { it.name.startsWith("Basmati") }
        assertEquals(5.0, rice.packSize)
        assertEquals("kg", rice.unit)
        assertEquals(1850.0, rice.lineTotal)
        val sugar = r.items.first { it.name.startsWith("Sugar") }
        assertEquals(2.0, sugar.qty)
        assertEquals(165.0, sugar.unitPrice)
        val oil = r.items.first { it.name.startsWith("Dalda") }
        assertEquals(5.0, oil.packSize)
        assertEquals("l", oil.unit)
        assertEquals("dozen", r.items.first { it.name.startsWith("Eggs") }.unit)
        assertTrue(r.confidence >= 0.85)
    }

    @Test
    fun packSizes() {
        assertEquals(0.5 to "kg", ReceiptParser.packOf("Daal Masoor 0.5 kg"))
        assertEquals(500.0 to "g", ReceiptParser.packOf("Tea 500gm"))
        assertEquals(1.5 to "l", ReceiptParser.packOf("Cola 1.5 Ltr"))
        assertNull(ReceiptParser.packOf("Bread"))
    }
}

class ImageClassifierTest {
    @Test
    fun lescoBillIsElectricity() {
        assertEquals("electricity_bill", ImageClassifier.classify(fixture("bills/lesco_clean.txt")).kind)
    }

    @Test
    fun schoolVoucherIsSchoolFee() {
        assertEquals("school_fee", ImageClassifier.classify(fixture("bills/school_fee_voucher.txt")).kind)
    }

    @Test
    fun receiptIsReceipt() {
        assertEquals("receipt", ImageClassifier.classify(fixture("receipts/grocery_receipt.txt")).kind)
    }

    @Test
    fun circularIsDocument() {
        assertEquals("document", ImageClassifier.classify(fixture("docs/school_circular.txt")).kind)
    }

    @Test
    fun meterPhotoIsMeter() {
        val r = ImageClassifier.classify("1600 imp/kWh\n10611 kWh\n50Hz")
        assertEquals("meter", r.kind)
    }

    @Test
    fun emptyIsUnknown() {
        assertEquals("unknown", ImageClassifier.classify("").kind)
    }
}
