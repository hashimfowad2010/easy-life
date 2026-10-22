package com.easylife.app.core.parse

import com.easylife.app.core.ocr.OcrBox
import com.easylife.app.core.ocr.OcrDocument
import com.easylife.app.core.ocr.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun fixture(path: String): String =
    requireNotNull(BillParserTest::class.java.getResource("/fixtures/$path")) { "missing fixture $path" }.readText()

class BillParserTest {

    @Test
    fun cleanLescoBillAllFields() {
        val r = BillParser.parse(OcrDocument.fromPlainText(fixture("bills/lesco_clean.txt")))
        assertEquals("electricity", r.billType)
        assertEquals("LESCO", r.provider)
        assertEquals("99 11234 0001234 U", r.referenceNo)
        assertEquals("2026-10", r.billingMonth)
        assertEquals("2026-10-09", r.readingDate)
        assertEquals("2026-10-11", r.issueDate)
        assertEquals("2026-10-23", r.dueDate)
        assertEquals(6407.0, r.amountWithinDue)
        assertEquals(6945.0, r.amountAfterDue)
        assertEquals(186, r.unitsConsumed)
        assertEquals(10425.0, r.previousReading)
        assertEquals(10611.0, r.presentReading)
        assertEquals("non_protected", r.consumerStatusPrinted)
        assertEquals(12, r.history.size)
        assertEquals("2025-10", r.history.first().month)
        assertEquals(201, r.history.first { it.month == "2026-07" }.units)
        assertEquals(2185.0, r.history.first().amount)
        assertTrue(r.uncertainFields.isEmpty(), "uncertain: ${r.uncertainFields}")
        assertTrue(r.confidence >= 0.85, "confidence ${r.confidence}")
    }

    @Test
    fun scrambledNoisyLescoStillReadsAndFlagsGuesses() {
        val r = BillParser.parse(OcrDocument.fromPlainText(fixture("bills/lesco_scrambled_noisy.txt")))
        assertEquals("LESCO", r.provider)
        assertEquals("2026-10-09", r.readingDate)
        assertEquals("2026-10-11", r.issueDate)
        assertEquals("2026-10-23", r.dueDate)
        assertEquals("2026-10", r.billingMonth)
        assertEquals(6407.0, r.amountWithinDue, "OCR '6,4O7' must be fixed to 6407")
        assertEquals(6945.0, r.amountAfterDue)
        assertEquals(186, r.unitsConsumed)
        assertEquals(10611.0, r.presentReading, "OCR '1O611' must be fixed to 10611")
        assertEquals(10425.0, r.previousReading)
        assertEquals("99 11234 0001234 U", r.referenceNo)
        // Dates came from the chronological fallback, so the user is asked to check them.
        assertTrue("dueDate" in r.uncertainFields)
        assertTrue(r.confidence < 0.85)
    }

    @Test
    fun headerRowRebuiltFromBoxes() {
        // Recogniser returned each label/value as its own line; positions put them in two table rows.
        val doc = OcrDocument(
            lines = listOf(
                OcrLine("LESCO", OcrBox(10, 10, 120, 40)),
                OcrLine("READING DATE", OcrBox(10, 100, 150, 120)),
                OcrLine("ISSUE DATE", OcrBox(200, 102, 330, 122)),
                OcrLine("DUE DATE", OcrBox(400, 98, 500, 118)),
                OcrLine("09-OCT-26", OcrBox(12, 140, 140, 160)),
                OcrLine("11-OCT-26", OcrBox(202, 141, 330, 161)),
                OcrLine("23-OCT-26", OcrBox(402, 139, 520, 159)),
            ),
        )
        assertEquals(listOf("LESCO", "READING DATE   ISSUE DATE   DUE DATE", "09-OCT-26   11-OCT-26   23-OCT-26"), doc.rows())
        val r = BillParser.parse(doc)
        assertEquals("2026-10-09", r.readingDate)
        assertEquals("2026-10-11", r.issueDate)
        assertEquals("2026-10-23", r.dueDate)
        assertTrue("dueDate" !in r.uncertainFields)
    }

    @Test
    fun schoolFeeVoucher() {
        val r = BillParser.parse(OcrDocument.fromPlainText(fixture("bills/school_fee_voucher.txt")))
        assertEquals("school_fee", r.billType)
        assertEquals("SAMPLE GRAMMAR SCHOOL (FICTIONAL)", r.provider)
        assertEquals("2026-10", r.billingMonth)
        assertEquals("2026-10-01", r.issueDate)
        assertEquals("2026-10-10", r.dueDate)
        assertEquals(13500.0, r.amountWithinDue)
        assertEquals(14000.0, r.amountAfterDue)
        assertNull(r.unitsConsumed)
    }

    @Test
    fun ptclInternetBill() {
        val r = BillParser.parse(OcrDocument.fromPlainText(fixture("bills/ptcl_internet.txt")))
        assertEquals("internet", r.billType)
        assertEquals("PTCL", r.provider)
        assertEquals("2026-09", r.billingMonth)
        assertEquals("2026-10-05", r.issueDate)
        assertEquals("2026-10-20", r.dueDate)
        assertEquals(3499.0, r.amountWithinDue)
        assertEquals(3649.0, r.amountAfterDue)
    }

    @Test
    fun emptyTextGivesLowConfidenceNotCrash() {
        val r = BillParser.parse(OcrDocument.fromPlainText(""))
        assertEquals("other", r.billType)
        assertTrue(r.confidence <= 0.2)
    }
}

class TextToolsTest {
    @Test
    fun ocrDigitFixes() {
        assertEquals("2,412", Num.fixDigits("2,4l2"))
        assertEquals("10425", Num.fixDigits("1O425"))
        assertEquals("OCT25", Num.fixDigits("OCT25"))
        assertEquals("L000123", Num.fixDigits("L000123"))
        assertEquals("A-1(01)T", Num.fixDigits("A-1(01)T"))
    }

    @Test
    fun amounts() {
        assertEquals(1250.0, Num.parseAmount("Rs. 1,250"))
        assertEquals(1250.5, Num.parseAmount("PKR 1,250.50"))
        assertEquals(12500.0, Num.parseAmount("12,500/-"))
        // Dates are not amounts.
        assertEquals(listOf(6407.0), Num.amounts("due 23-10-2026 pay 6,407").map { it.value })
    }

    @Test
    fun pakistaniDates() {
        assertEquals("2026-10-23", Dates.parse("23-OCT-26").toString())
        assertEquals("2026-10-23", Dates.parse("23-0CT-26").toString())
        assertEquals("2026-10-23", Dates.parse("23/10/2026").toString())
        assertEquals("2026-10-23", Dates.parse("2026-10-23").toString())
        assertEquals("2026-10-23", Dates.parse("Oct 23, 2026").toString())
        assertEquals("2026-11-05", Dates.parse("5th November 2026").toString())
        // Day-first unless impossible.
        assertEquals("2026-01-12", Dates.parse("12/01/2026").toString())
        assertEquals("2026-12-01", Dates.parse("01/12/2026").toString())
        assertEquals("2026-10-25", Dates.parse("10/25/2026").toString())
        assertNull(Dates.parse("Voucher 2026-10-00123"))
        assertEquals("2026-10", Dates.findMonths("BILL MONTH OCT 26").single().value.toString())
        assertNull(Dates.monthOf("MARKET"))
    }

    @Test
    fun fuzzyLabels() {
        assertTrue(Labels.contains("DUE DAIE   23-OCT-26", "DUE DATE"))
        assertTrue(Labels.contains("PAYABLE WlTHIN DUE DATE", "PAYABLE WITHIN DUE DATE"))
        assertTrue(!Labels.contains("ISSUE DATE", "DUE DATE"))
        assertTrue(!Labels.contains("CONNECTED LOAD", "CONNECTION DATE"))
    }
}
