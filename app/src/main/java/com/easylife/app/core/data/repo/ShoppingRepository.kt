package com.easylife.app.core.data.repo

import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.schema.ListItemResult
import com.easylife.app.core.cloud.FirebaseGateway
import com.easylife.app.core.data.db.PriceReportEntity
import com.easylife.app.core.data.db.ShoppingDao
import com.easylife.app.core.data.db.ShoppingItemEntity
import com.easylife.app.core.data.db.ShoppingListEntity
import com.easylife.app.core.data.db.StoreEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SShop
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.parse.ShoppingListParser
import com.easylife.app.core.shopping.PricePoint
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A receipt or shelf-tag line turned into a price observation. */
data class ObservedPrice(val itemKey: String, val itemName: String, val pricePkr: Double, val packSize: Double, val unit: String)

@Singleton
class ShoppingRepository @Inject constructor(
    private val dao: ShoppingDao,
    private val router: AiRouter,
    private val prefs: PrefsRepository,
    private val firebase: FirebaseGateway,
    private val errorLog: ErrorLog,
    private val impact: ImpactTracker,
) {
    val lists: Flow<List<ShoppingListEntity>> = dao.observeLists()
    fun list(id: Long): Flow<ShoppingListEntity?> = dao.observeList(id)
    fun items(listId: Long): Flow<List<ShoppingItemEntity>> = dao.observeItems(listId)
    val stores: Flow<List<StoreEntity>> = dao.observeStores()
    fun prices(keys: List<String>): Flow<List<PriceReportEntity>> = dao.observePrices(keys.ifEmpty { listOf("-") })

    /** Turns "2 kg cheeni, 1 dozen anday" into list items (on-device dictionary; Smart AI only if enabled). */
    suspend fun normalize(text: String): List<ListItemResult> =
        router.run(AiTask.ListNormalize, AiInput(text = text)).value?.items ?: ShoppingListParser.parse(text).items

    suspend fun createList(name: String, text: String): Long {
        val id = dao.insertList(ShoppingListEntity(name = name))
        if (text.isNotBlank()) addItems(id, text)
        prefs.setActiveShoppingList(id)
        return id
    }

    suspend fun addItems(listId: Long, text: String) {
        val existing = dao.getItems(listId).size
        val items = normalize(text).mapIndexed { i, r ->
            ShoppingItemEntity(
                listId = listId, itemKey = r.itemKey, nameEn = r.nameEn, nameUr = r.nameUr, rawText = r.rawText, qty = r.qty, unit = r.unit,
                brandPref = r.brandPref, category = r.category, sortOrder = existing + i,
            )
        }
        dao.insertItems(items)
        dao.getLists().firstOrNull { it.id == listId }?.let { dao.updateList(it.copy(updatedAt = Instant.now())) }
    }

    suspend fun updateItem(item: ShoppingItemEntity) = dao.updateItem(item)
    suspend fun deleteItem(id: Long) = dao.deleteItem(id)
    suspend fun deleteList(id: Long) = dao.deleteList(id)

    /** "I found a price" from a retailer's site: kept as a manual price on the item (and a local price point). */
    suspend fun setManualPrice(item: ShoppingItemEntity, price: Double, retailer: String, packSize: Double, unit: String) {
        dao.updateItem(item.copy(manualPrice = price, manualPriceSource = retailer, manualPriceAt = Instant.now()))
        val key = item.itemKey ?: return
        val storeId = "online:$retailer"
        dao.upsertStores(listOf(StoreEntity(id = storeId, name = retailer, area = "online", city = "online")))
        dao.upsertPrices(listOf(PriceReportEntity(UUID.randomUUID().toString(), storeId, key, item.nameEn, price, packSize, unit, Instant.now(), isMine = true, uploaded = true)))
    }

    fun pricePoints(reports: List<PriceReportEntity>): List<PricePoint> =
        reports.map { PricePoint(it.storeId, it.itemKey, it.pricePkr, it.packSize, it.unit, it.reportedAt) }

    /** List text for WhatsApp in Urdu or English. */
    fun shareText(items: List<ShoppingItemEntity>, lang: AppLanguage): String = buildString {
        appendLine(SShop.shareHeader.get(lang))
        items.filter { !it.checked }.forEach { i ->
            val name = if (lang == AppLanguage.UR) i.nameUr else i.nameEn
            val qty = Fmt.smart(i.qty) + (i.unit?.let { " $it" } ?: "")
            appendLine("• $name — $qty" + (i.brandPref?.let { " ($it)" } ?: ""))
        }
    }

    // ---------------------------------------------------------------- community prices

    /** Saves prices from a receipt on the phone; shares them anonymously only with consent and Firebase connected. */
    suspend fun recordPrices(storeName: String, area: String?, prices: List<ObservedPrice>, share: Boolean): Int {
        val city = prefs.current().city
        val storeId = "local:" + (storeName.trim().lowercase() + ":" + (area ?: "").trim().lowercase()).hashCode().toString(16)
        dao.upsertStores(listOf(StoreEntity(id = storeId, name = storeName.trim().take(60), area = area?.trim()?.take(60), city = city)))
        val reports = prices.map { p ->
            PriceReportEntity(UUID.randomUUID().toString(), storeId, p.itemKey, p.itemName.take(60), p.pricePkr, p.packSize, p.unit, Instant.now(), isMine = true, uploaded = false)
        }
        dao.upsertPrices(reports)
        if (share) uploadPending()
        return reports.size
    }

    suspend fun uploadPending(): Int {
        if (prefs.current().communityPricesConsent != true || !firebase.isConfigured) return 0
        val uid = firebase.ensureSignedIn() ?: return 0
        val pending = dao.pendingUploads().filter { !it.isDemo }
        if (pending.isEmpty()) return 0
        return runCatching {
            val db = Firebase.firestore
            val stores = dao.getStores().associateBy { it.id }
            val remoteStoreIds = mutableMapOf<String, String>()
            for (localId in pending.map { it.storeId }.distinct()) {
                val s = stores[localId] ?: continue
                val ref = db.collection("stores").document()
                ref.set(mapOf("name" to s.name, "area" to (s.area ?: ""), "city" to s.city, "createdAt" to FieldValue.serverTimestamp())).await()
                remoteStoreIds[localId] = ref.id
            }
            val batch = db.batch()
            pending.forEach { r ->
                val storeId = remoteStoreIds[r.storeId] ?: return@forEach
                batch.set(
                    db.collection("priceReports").document(),
                    mapOf(
                        "storeId" to storeId, "itemKey" to r.itemKey, "itemName" to r.itemName, "pricePkr" to r.pricePkr,
                        "packSize" to r.packSize, "unit" to r.unit, "reportedAt" to FieldValue.serverTimestamp(), "reporterAnonId" to uid,
                    ),
                )
            }
            batch.commit().await()
            dao.upsertPrices(pending.map { it.copy(uploaded = true) })
            pending.size
        }.onFailure { errorLog.record("price-upload", it) }.getOrDefault(0)
    }

    /** Pull recent community reports for these items (Firestore `in` queries take up to 30 keys). */
    suspend fun syncCommunity(keys: List<String>): Boolean {
        if (!firebase.isConfigured || keys.isEmpty()) return false
        firebase.ensureSignedIn() ?: return false
        return runCatching {
            val db = Firebase.firestore
            val reports = mutableListOf<PriceReportEntity>()
            for (chunk in keys.distinct().chunked(30)) {
                val snap = db.collection("priceReports").whereIn("itemKey", chunk)
                    .orderBy("reportedAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(300).get().await()
                snap.documents.forEach { d ->
                    val at = d.getTimestamp("reportedAt")?.toDate()?.toInstant() ?: return@forEach
                    reports += PriceReportEntity(
                        id = "c:" + d.id, storeId = "c:" + (d.getString("storeId") ?: return@forEach), itemKey = d.getString("itemKey") ?: return@forEach,
                        itemName = d.getString("itemName") ?: "", pricePkr = d.getDouble("pricePkr") ?: return@forEach,
                        packSize = d.getDouble("packSize") ?: 1.0, unit = d.getString("unit") ?: "packet", reportedAt = at, uploaded = true,
                    )
                }
            }
            val storeIds = reports.map { it.storeId.removePrefix("c:") }.distinct()
            val stores = storeIds.mapNotNull { id ->
                val d = db.collection("stores").document(id).get().await()
                if (!d.exists()) null else StoreEntity("c:$id", d.getString("name") ?: "?", d.getString("area"), d.getString("city") ?: "", isLocalOnly = false)
            }
            dao.upsertStores(stores)
            dao.upsertPrices(reports)
            true
        }.onFailure { errorLog.record("price-sync", it) }.getOrDefault(false)
    }

    fun logComparison(savings: Double) {
        impact.log(ImpactEvents.SHOPPING_COMPARED, "shopping")
        if (savings > 0) impact.log(ImpactEvents.SHOPPING_SAVINGS, "shopping", savings)
    }
}
