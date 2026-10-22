package com.easylife.app.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopSearchParseTest {

    @Test
    fun readsValidResultsAndDropsMalformedOnes() {
        val reply = mapOf(
            "results" to listOf(
                mapOf(
                    "item" to "sugar", "retailer" to "Al-Fatah", "productTitle" to "Sugar 1kg", "packSize" to 1, "unit" to "kg",
                    "pricePkr" to 165, "unitPrice" to 165.0, "url" to "https://alfatah.pk/products/sugar", "seenAt" to "2026-10-23",
                ),
                // No price: dropped.
                mapOf("item" to "eggs", "retailer" to "Naheed", "url" to "https://naheed.pk/eggs"),
                // Not a web link: dropped.
                mapOf("item" to "milk", "retailer" to "Naheed", "pricePkr" to 230, "url" to "javascript:alert(1)"),
                "not a map",
            ),
            "notFound" to listOf("eggs", 5),
            "checkedAt" to "2026-10-23T09:00:00Z",
        )
        val p = FunctionsShopSearchClient.parse(reply)
        assertEquals(1, p.results.size)
        val r = p.results.single()
        assertEquals(165.0, r.pricePkr)
        assertEquals(1.0, r.packSize)
        assertEquals("kg", r.unit)
        assertEquals(listOf("eggs"), p.notFound)
        assertEquals("2026-10-23T09:00:00Z", p.checkedAt)
    }

    @Test
    fun emptyReplyIsEmpty() {
        val p = FunctionsShopSearchClient.parse(emptyMap<String, Any>())
        assertEquals(0, p.results.size)
        assertEquals("", p.checkedAt)
    }
}
