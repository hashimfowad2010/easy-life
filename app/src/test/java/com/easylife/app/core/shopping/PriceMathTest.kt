package com.easylife.app.core.shopping

import com.easylife.app.core.parse.ShoppingListParser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PriceMathTest {
    private val t = Instant.parse("2026-10-20T10:00:00Z")

    private fun p(store: String, item: String, price: Double, pack: Double, unit: String) = PricePoint(store, item, price, pack, unit, t)

    @Test
    fun unitPrices() {
        assertEquals(165.0 to Dimension.MASS, p("a", "sugar", 165.0, 1.0, "kg").unitPrice())
        assertEquals(400.0 to Dimension.MASS, p("a", "tea", 200.0, 500.0, "g").unitPrice())
        assertEquals(590.0 to Dimension.VOLUME, p("a", "cooking_oil", 2950.0, 5.0, "l").unitPrice())
        assertEquals(32.5 to Dimension.COUNT, p("a", "eggs", 390.0, 1.0, "dozen").unitPrice())
    }

    @Test
    fun outliersAreDropped() {
        val clean = RobustStats.withoutOutliers(listOf(160.0, 165.0, 162.0, 168.0, 400.0))
        assertTrue(400.0 !in clean)
        assertEquals(4, clean.size)
        assertEquals(listOf(10.0, 900.0), RobustStats.withoutOutliers(listOf(10.0, 900.0)), "too few reports to judge")
    }

    @Test
    fun spec65AcceptanceListComparesWithUnitPriceMath() {
        // The 5-item Roman-Urdu list from the spec, normalised on the phone.
        val list = ShoppingListParser.parse("2 kg cheeni, 1 dozen anday, Dalda 5 litre, aadha kilo dahi, 3 packet doodh").items
        val needs = list.map { ListNeed(it.itemKey, Quantity.of(it.qty, it.unit)) }
        // Fictional community prices at three stores.
        val points = listOf(
            p("alfatah", "sugar", 165.0, 1.0, "kg"), p("alfatah", "eggs", 390.0, 1.0, "dozen"), p("alfatah", "cooking_oil", 2950.0, 5.0, "l"),
            p("alfatah", "yogurt", 220.0, 1.0, "kg"), p("alfatah", "milk", 230.0, 1.0, "packet"),
            p("imtiaz", "sugar", 158.0, 1.0, "kg"), p("imtiaz", "eggs", 32.0, 1.0, "piece"), p("imtiaz", "cooking_oil", 610.0, 1.0, "l"),
            p("imtiaz", "milk", 225.0, 1.0, "packet"),
            p("karyana", "sugar", 170.0, 1.0, "kg"), p("karyana", "eggs", 360.0, 1.0, "dozen"), p("karyana", "yogurt", 200.0, 1.0, "kg"),
        )
        val c = BasketOptimizer.compare(needs, points)
        assertTrue(c.itemsWithoutPrices.isEmpty())
        // Sugar: 2 kg x 158 at Imtiaz.
        assertEquals("imtiaz", c.cheapestPerItem.getValue("sugar").storeId)
        assertEquals(316.0, c.cheapestPerItem.getValue("sugar").cost, 1e-9)
        // Eggs: karyana 360/dozen = 30/piece beats Imtiaz 32/piece.
        assertEquals("karyana", c.cheapestPerItem.getValue("eggs").storeId)
        // Oil: 5 l at Al-Fatah 590/l = 2950 beats Imtiaz 610/l = 3050.
        assertEquals(2950.0, c.cheapestPerItem.getValue("cooking_oil").cost, 1e-9)
        // Yogurt: 0.5 kg at karyana = 100.
        assertEquals(100.0, c.cheapestPerItem.getValue("yogurt").cost, 1e-9)
        // Single store: only Al-Fatah has all 5 items.
        val single = assertNotNull(c.bestSingleStore)
        assertEquals(listOf("alfatah"), single.storeIds)
        assertEquals(5, single.covered)
        assertEquals(330.0 + 390.0 + 2950.0 + 110.0 + 690.0, single.total, 1e-9)
        // Two-store split covers everything for less.
        val split = assertNotNull(c.bestSplit)
        assertEquals(5, split.covered)
        assertTrue(split.total < single.total)
        assertTrue(split.storeIds.size <= 2)
        assertTrue(c.savingsVsMostExpensive > 0)
        assertTrue(c.savingsVsAverage in 0.0..c.savingsVsMostExpensive)
    }

    @Test
    fun incomparableUnitsAreSkipped() {
        val c = BasketOptimizer.compare(listOf(ListNeed("milk", Quantity.of(2.0, "packet"))), listOf(p("a", "milk", 240.0, 1.0, "l")))
        assertEquals(listOf("milk"), c.itemsWithoutPrices)
    }

    @Test
    fun lineCostScalesThePricedPackToTheNeed() {
        assertEquals(1720.0, BasketOptimizer.lineCost(900.0, "g", 860.0, 450.0, "g"), 0.001)
        assertEquals(1180.0, BasketOptimizer.lineCost(2.0, "l", 2950.0, 5.0, "l"), 0.001)
        assertEquals(390.0, BasketOptimizer.lineCost(1.0, "dozen", 32.5, 1.0, "piece"), 0.001)
        // Packets vs litres: each packet counts as one priced pack.
        assertEquals(720.0, BasketOptimizer.lineCost(3.0, "packet", 240.0, 1.0, "l"), 0.001)
        assertEquals(240.0, BasketOptimizer.lineCost(2.0, "kg", 240.0, 1.0, "packet"), 0.001)
    }
}
