package com.easylife.app.feature.shopping

import com.easylife.app.core.data.db.PriceReportEntity
import com.easylife.app.core.data.db.ShoppingDao
import com.easylife.app.core.data.db.StoreEntity
import com.easylife.app.core.data.repo.DemoContributor
import com.easylife.app.core.data.repo.ShoppingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Demo mode: FICTIONAL shops and prices (labelled "Demo data") and a saved Roman-Urdu list. */
class ShoppingDemo @Inject constructor(private val dao: ShoppingDao, private val repo: ShoppingRepository) : DemoContributor {

    private data class P(val store: String, val item: String, val name: String, val price: Double, val pack: Double, val unit: String, val daysAgo: Long)

    override suspend fun load(today: LocalDate) {
        val stores = listOf(
            StoreEntity("demo:superstore", "Sample Superstore", "Model Town", "Lahore", isDemo = true),
            StoreEntity("demo:mart", "Sample Mart", "Wapda Town", "Lahore", isDemo = true),
            StoreEntity("demo:karyana", "Sample Karyana", "Johar Town", "Lahore", isDemo = true),
        )
        dao.upsertStores(stores)
        val prices = listOf(
            P("demo:superstore", "sugar", "Sugar 1kg", 165.0, 1.0, "kg", 2), P("demo:superstore", "sugar", "Sugar 1kg", 168.0, 1.0, "kg", 6),
            P("demo:mart", "sugar", "Sugar 1kg", 158.0, 1.0, "kg", 1), P("demo:mart", "sugar", "Sugar 1kg", 160.0, 1.0, "kg", 4), P("demo:mart", "sugar", "Sugar", 159.0, 1.0, "kg", 9),
            P("demo:karyana", "sugar", "Cheeni", 170.0, 1.0, "kg", 3),
            P("demo:superstore", "eggs", "Eggs 1 dozen", 390.0, 1.0, "dozen", 2), P("demo:mart", "eggs", "Eggs", 32.0, 1.0, "piece", 3),
            P("demo:karyana", "eggs", "Anday 1 darjan", 360.0, 1.0, "dozen", 1), P("demo:karyana", "eggs", "Anday", 365.0, 1.0, "dozen", 5),
            P("demo:superstore", "cooking_oil", "Dalda Cooking Oil 5L", 2950.0, 5.0, "l", 4), P("demo:mart", "cooking_oil", "Cooking oil 1L", 610.0, 1.0, "l", 2),
            P("demo:superstore", "yogurt", "Dahi 1kg", 220.0, 1.0, "kg", 2), P("demo:karyana", "yogurt", "Dahi", 200.0, 1.0, "kg", 1),
            P("demo:superstore", "milk", "Milk 1L pack", 230.0, 1.0, "packet", 1), P("demo:mart", "milk", "Milk pack", 225.0, 1.0, "packet", 3),
            P("demo:superstore", "flour", "Atta 5kg", 640.0, 5.0, "kg", 5), P("demo:karyana", "flour", "Chakki atta 10kg", 1240.0, 10.0, "kg", 2),
            P("demo:mart", "rice", "Basmati 5kg", 1850.0, 5.0, "kg", 6), P("demo:superstore", "rice", "Basmati rice 1kg", 395.0, 1.0, "kg", 3),
            P("demo:mart", "tea", "Tea 900g", 1650.0, 900.0, "g", 8), P("demo:superstore", "tea", "Tea 450g", 860.0, 450.0, "g", 4),
            P("demo:karyana", "lentils_masoor", "Masoor daal 1kg", 330.0, 1.0, "kg", 2), P("demo:superstore", "lentils_masoor", "Masoor 1kg", 345.0, 1.0, "kg", 5),
            P("demo:karyana", "onion", "Piyaz 1kg", 120.0, 1.0, "kg", 1), P("demo:karyana", "tomato", "Tamatar 1kg", 160.0, 1.0, "kg", 1),
            // One silly report (typo) that the outlier filter removes.
            P("demo:mart", "sugar", "Sugar", 1600.0, 1.0, "kg", 2),
        )
        val now = Instant.now()
        dao.upsertPrices(prices.mapIndexed { i, p ->
            PriceReportEntity("demo:$i", p.store, p.item, p.name, p.price, p.pack, p.unit, now.minus(p.daysAgo, ChronoUnit.DAYS), uploaded = true, isDemo = true)
        })
        val listId = repo.createList("Weekly groceries", "2 kg cheeni, 1 dozen anday, Dalda 5 litre, aadha kilo dahi, 3 packet doodh, 5 kg aata, chips")
        dao.getLists().firstOrNull { it.id == listId }?.let { dao.updateList(it.copy(isDemo = true)) }
        // "Chips" can be skipped if the budget is tight.
        dao.getItems(listId).firstOrNull { it.itemKey == "chips" }?.let { dao.updateItem(it.copy(priority = 2)) }
    }

    override suspend fun clear() {
        dao.deleteDemoLists()
        dao.deleteDemoPrices()
        dao.deleteDemoStores()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingModule {
    @Binds @IntoSet abstract fun demo(impl: ShoppingDemo): DemoContributor
}
