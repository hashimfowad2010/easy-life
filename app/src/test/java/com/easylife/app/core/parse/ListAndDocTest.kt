package com.easylife.app.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingListParserTest {

    @Test
    fun fiveItemRomanUrduListNormalisesOnDevice() {
        // Spec 6.5 acceptance: a 5-item Roman-Urdu list normalises correctly on-device.
        val r = ShoppingListParser.parse("2 kg cheeni, 1 dozen anday, Dalda 5 litre, aadha kilo dahi, 3 packet doodh")
        assertEquals(5, r.items.size)
        val (sugar, eggs, oil, yogurt, milk) = r.items
        assertEquals(listOf("sugar", 2.0, "kg"), listOf(sugar.itemKey, sugar.qty, sugar.unit))
        assertEquals("چینی", sugar.nameUr)
        assertEquals(listOf("eggs", 1.0, "dozen"), listOf(eggs.itemKey, eggs.qty, eggs.unit))
        assertEquals(listOf("cooking_oil", 5.0, "l", "Dalda"), listOf(oil.itemKey, oil.qty, oil.unit, oil.brandPref))
        assertEquals(listOf("yogurt", 0.5, "kg"), listOf(yogurt.itemKey, yogurt.qty, yogurt.unit))
        assertEquals(listOf("milk", 3.0, "packet"), listOf(milk.itemKey, milk.qty, milk.unit))
        assertTrue(r.items.all { it.recognized })
        assertTrue(r.confidence > 0.9)
    }

    @Test
    fun urduScriptAndNumberWords() {
        val r = ShoppingListParser.parse("دو کلو چینی\nایک درجن انڈے\npao adrak")
        assertEquals("sugar", r.items[0].itemKey)
        assertEquals(2.0, r.items[0].qty)
        assertEquals("kg", r.items[0].unit)
        assertEquals("eggs", r.items[1].itemKey)
        assertEquals("dozen", r.items[1].unit)
        assertEquals("ginger", r.items[2].itemKey)
        assertEquals(0.25, r.items[2].qty)
        assertEquals("kg", r.items[2].unit)
    }

    @Test
    fun bareNumberUsesCountUnitAndTyposAreForgiven() {
        val r = ShoppingListParser.parse("anday 12 aur cheni 1kg; tamater")
        assertEquals(listOf("eggs", "sugar", "tomato"), r.items.map { it.itemKey })
        assertEquals("piece", r.items[0].unit)
    }

    @Test
    fun unknownItemKeptForEditing() {
        val r = ShoppingListParser.parse("2 purple crayons")
        assertEquals(1, r.items.size)
        assertTrue(!r.items.single().recognized)
        assertEquals(2.0, r.items.single().qty)
    }

    @Test
    fun masoorDaalBeatsPlainDaal() {
        assertEquals("lentils_masoor", ShoppingListParser.parse("1 kg masoor daal").items.single().itemKey)
    }
}

class DocInsightsTest {

    @Test
    fun circularDatesAmountsAndActions() {
        val r = DocInsights.explain(fixture("docs/school_circular.txt"))
        assertEquals("school_circular", r.docType)
        val dates = r.keyDetails.filter { it.kind in setOf("deadline", "event", "date") }.map { it.value }.toSet()
        assertTrue(dates.containsAll(setOf("2026-11-05", "2026-10-28", "2026-10-31")), "dates: $dates")
        assertTrue(r.keyDetails.any { it.kind == "deadline" && it.value == "2026-10-28" })
        assertTrue(r.keyDetails.any { it.kind == "amount" && it.value == "1500.0" })
        assertTrue(r.keyDetails.any { it.kind == "amount" && it.value == "500.0" })
        assertTrue(r.keyDetails.any { it.kind == "phone" })
        assertTrue(r.actions.any { it.dueDate == "2026-10-28" && it.textUr.startsWith("جمع") })
        assertTrue(r.actions.any { it.dueDate == "2026-10-31" && it.textUr.startsWith("ادائیگی") })
        assertEquals("Annual Sports Day and Fee Reminder", r.summaryEn.first())
    }

    @Test
    fun medicalDocumentsGetTheSafetyLine() {
        val r = DocInsights.explain("Prescription\nPatient: sample\nTablet Panadol 500 mg twice daily\nDr. Sample, Sample Clinic")
        assertEquals("medical", r.docType)
        assertTrue(r.summaryEn.first().contains("not advice"))
        assertTrue(r.summaryUr.first().contains("مشورہ نہیں"))
    }
}
