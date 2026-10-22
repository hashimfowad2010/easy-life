package com.easylife.app.core.shopping

import java.time.Instant
import kotlin.math.abs

/** Units are compared within one dimension: mass (kg), volume (litre), count (piece) or packets. */
enum class Dimension { MASS, VOLUME, COUNT, PACK }

data class Quantity(val amount: Double, val dimension: Dimension) {
    companion object {
        /** "2 kg" -> 2 kg; "500 g" -> 0.5 kg; "1 dozen" -> 12 pieces; "3 packet" -> 3 packs. Null unit = packs. */
        fun of(qty: Double, unit: String?): Quantity = when (unit?.lowercase()) {
            "kg" -> Quantity(qty, Dimension.MASS)
            "g" -> Quantity(qty / 1000.0, Dimension.MASS)
            "l" -> Quantity(qty, Dimension.VOLUME)
            "ml" -> Quantity(qty / 1000.0, Dimension.VOLUME)
            "dozen" -> Quantity(qty * 12, Dimension.COUNT)
            "piece" -> Quantity(qty, Dimension.COUNT)
            else -> Quantity(qty, Dimension.PACK)
        }
    }
}

/** One observed price (community report, own receipt, or a price typed from a retailer's site). */
data class PricePoint(
    val storeId: String,
    val itemKey: String,
    val pricePkr: Double,
    val packSize: Double,
    val unit: String,
    val reportedAt: Instant,
)

/** Price per base unit (Rs/kg, Rs/litre, Rs/piece or Rs/pack). */
fun PricePoint.unitPrice(): Pair<Double, Dimension>? {
    val q = Quantity.of(packSize, unit)
    if (q.amount <= 0 || pricePkr <= 0) return null
    return pricePkr / q.amount to q.dimension
}

object RobustStats {
    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /**
     * Drops outliers using the median absolute deviation: keeps values within [k] scaled MADs of the median.
     * With fewer than 3 values nothing is dropped (not enough evidence).
     */
    fun withoutOutliers(values: List<Double>, k: Double = 3.0): List<Double> {
        if (values.size < 3) return values
        val med = median(values)
        val mad = median(values.map { abs(it - med) }) * 1.4826
        if (mad == 0.0) return values.filter { abs(it - med) <= med * 0.25 }.ifEmpty { values }
        return values.filter { abs(it - med) <= k * mad }
    }
}

/** A store's robust price for one item, per base unit. */
data class StorePrice(
    val storeId: String,
    val itemKey: String,
    val unitPrice: Double,
    val dimension: Dimension,
    val reports: Int,
    val latest: Instant,
)

data class ListNeed(val itemKey: String, val quantity: Quantity)

data class ItemOption(val itemKey: String, val storeId: String, val cost: Double, val price: StorePrice)

data class StorePlan(val storeIds: List<String>, val total: Double, val covered: Int, val choices: Map<String, ItemOption>)

data class BasketComparison(
    /** Cheapest known option per item. */
    val cheapestPerItem: Map<String, ItemOption>,
    val bestSingleStore: StorePlan?,
    /** Cheapest way using at most two stores. */
    val bestSplit: StorePlan?,
    val itemsWithoutPrices: List<String>,
    /** Totals over items that have prices at 2+ places. */
    val savingsVsMostExpensive: Double,
    val savingsVsAverage: Double,
)

object BasketOptimizer {

    fun storePrices(points: List<PricePoint>): List<StorePrice> =
        points.groupBy { it.storeId to it.itemKey }.mapNotNull { (key, pts) ->
            val unitPrices = pts.mapNotNull { p -> p.unitPrice()?.let { it to p } }
            if (unitPrices.isEmpty()) return@mapNotNull null
            // Use the most common dimension (a store may report "1 kg" and "1 packet" for the same item).
            val dim = unitPrices.groupBy { it.first.second }.maxBy { it.value.size }.key
            val values = unitPrices.filter { it.first.second == dim }.map { it.first.first }
            val clean = RobustStats.withoutOutliers(values)
            StorePrice(key.first, key.second, RobustStats.median(clean), dim, clean.size, pts.maxOf { it.reportedAt })
        }

    /** Cost of the needed quantity at a store, or null if units can't be compared. */
    fun cost(need: ListNeed, price: StorePrice): Double? = when {
        need.quantity.dimension == price.dimension -> need.quantity.amount * price.unitPrice
        // "2 packets" of something priced per kg: assume 1 kg packs are not knowable, so not comparable.
        else -> null
    }

    fun compare(needs: List<ListNeed>, points: List<PricePoint>): BasketComparison {
        val prices = storePrices(points)
        val options: Map<String, List<ItemOption>> = needs.associate { n ->
            n.itemKey to prices.filter { it.itemKey == n.itemKey }.mapNotNull { p -> cost(n, p)?.let { ItemOption(n.itemKey, p.storeId, it, p) } }
        }
        val cheapest = options.mapNotNull { (k, opts) -> opts.minByOrNull { it.cost }?.let { k to it } }.toMap()
        val missing = needs.map { it.itemKey }.filter { options[it].isNullOrEmpty() }

        val stores = prices.map { it.storeId }.distinct()
        fun plan(storeSet: List<String>): StorePlan {
            val choices = options.mapNotNull { (k, opts) -> opts.filter { it.storeId in storeSet }.minByOrNull { it.cost }?.let { k to it } }.toMap()
            return StorePlan(storeSet, choices.values.sumOf { it.cost }, choices.size, choices)
        }
        val better = compareByDescending<StorePlan> { it.covered }.thenBy { it.total }
        val single = stores.map { plan(listOf(it)) }.filter { it.covered > 0 }.sortedWith(better).firstOrNull()
        val pairs = stores.flatMapIndexed { i, a -> stores.drop(i + 1).map { b -> plan(listOf(a, b)) } }
        val split = (pairs + listOfNotNull(single)).filter { it.covered > 0 }.sortedWith(better).firstOrNull()

        val comparable = options.filter { it.value.map { o -> o.storeId }.distinct().size >= 2 }
        val savingsMax = comparable.values.sumOf { opts -> opts.maxOf { it.cost } - opts.minOf { it.cost } }
        val savingsAvg = comparable.values.sumOf { opts -> opts.map { it.cost }.average() - opts.minOf { it.cost } }

        return BasketComparison(cheapest, single, split, missing, savingsMax, savingsAvg)
    }
}
