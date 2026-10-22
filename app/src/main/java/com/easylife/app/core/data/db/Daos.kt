package com.easylife.app.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface MeterDao {
    @Query("SELECT * FROM meters ORDER BY id")
    fun observeMeters(): Flow<List<MeterEntity>>

    @Query("SELECT * FROM meters WHERE id = :id")
    fun observeMeter(id: Long): Flow<MeterEntity?>

    @Query("SELECT * FROM meters WHERE id = :id")
    suspend fun getMeter(id: Long): MeterEntity?

    @Query("SELECT * FROM meters ORDER BY id")
    suspend fun getMeters(): List<MeterEntity>

    @Query("SELECT * FROM meters WHERE referenceNo = :ref LIMIT 1")
    suspend fun findByReference(ref: String): MeterEntity?

    @Insert
    suspend fun insertMeter(meter: MeterEntity): Long

    @Update
    suspend fun updateMeter(meter: MeterEntity)

    @Delete
    suspend fun deleteMeter(meter: MeterEntity)

    @Query("SELECT * FROM meter_readings WHERE meterId = :meterId ORDER BY takenAt")
    fun observeReadings(meterId: Long): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE meterId = :meterId ORDER BY takenAt")
    suspend fun getReadings(meterId: Long): List<MeterReadingEntity>

    @Query("SELECT * FROM meter_readings WHERE meterId = :meterId ORDER BY takenAt DESC LIMIT 1")
    suspend fun latestReading(meterId: Long): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings ORDER BY takenAt DESC LIMIT 1")
    suspend fun latestReadingAnyMeter(): MeterReadingEntity?

    @Insert
    suspend fun insertReading(reading: MeterReadingEntity): Long

    @Update
    suspend fun updateReading(reading: MeterReadingEntity)

    @Query("DELETE FROM meter_readings WHERE id = :id")
    suspend fun deleteReading(id: Long)

    @Query("SELECT * FROM usage_months WHERE meterId = :meterId ORDER BY month")
    fun observeUsage(meterId: Long): Flow<List<UsageMonthEntity>>

    @Query("SELECT * FROM usage_months WHERE meterId = :meterId ORDER BY month")
    suspend fun getUsage(meterId: Long): List<UsageMonthEntity>

    @Upsert
    suspend fun upsertUsage(months: List<UsageMonthEntity>)

    @Query("DELETE FROM meters WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface CustomLimitDao {
    @Query("SELECT * FROM custom_limits ORDER BY id")
    fun observeAll(): Flow<List<CustomLimitEntity>>

    @Query("SELECT * FROM custom_limits WHERE id = :id")
    fun observe(id: Long): Flow<CustomLimitEntity?>

    @Query("SELECT * FROM custom_limits")
    suspend fun getAll(): List<CustomLimitEntity>

    @Insert
    suspend fun insert(limit: CustomLimitEntity): Long

    @Update
    suspend fun update(limit: CustomLimitEntity)

    @Query("DELETE FROM custom_limits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM custom_limit_entries WHERE limitId = :limitId ORDER BY at")
    fun observeEntries(limitId: Long): Flow<List<CustomLimitEntryEntity>>

    @Query("SELECT * FROM custom_limit_entries WHERE limitId = :limitId ORDER BY at")
    suspend fun getEntries(limitId: Long): List<CustomLimitEntryEntity>

    @Insert
    suspend fun insertEntry(entry: CustomLimitEntryEntity): Long

    @Query("DELETE FROM custom_limit_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM custom_limits WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface ApplianceDao {
    @Query("SELECT * FROM appliances ORDER BY id")
    fun observeAll(): Flow<List<ApplianceEntity>>

    @Upsert
    suspend fun upsert(appliance: ApplianceEntity): Long

    @Query("DELETE FROM appliances WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM appliances WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY CASE WHEN status = 'UNPAID' THEN 0 ELSE 1 END, dueDate DESC, id DESC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = 'UNPAID' ORDER BY dueDate")
    fun observeUnpaid(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = 'UNPAID' ORDER BY dueDate")
    suspend fun getUnpaid(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id")
    fun observe(id: Long): Flow<BillEntity?>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): BillEntity?

    @Query("SELECT * FROM bills ORDER BY id")
    suspend fun getAll(): List<BillEntity>

    @Query(
        "SELECT * FROM bills WHERE billType = :type AND provider IS :provider AND billingMonth IS :month " +
            "AND billingMonth IS NOT NULL LIMIT 1",
    )
    suspend fun findSame(type: String, provider: String?, month: String?): BillEntity?

    @Insert
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bills WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface BudgetDao {
    // Accounts and senders
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAccounts(): List<AccountEntity>

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("SELECT * FROM sms_senders ORDER BY sender")
    fun observeSenders(): Flow<List<SmsSenderEntity>>

    @Query("SELECT * FROM sms_senders")
    suspend fun getSenders(): List<SmsSenderEntity>

    @Upsert
    suspend fun upsertSenders(senders: List<SmsSenderEntity>)

    @Query("DELETE FROM sms_senders WHERE sender = :sender")
    suspend fun deleteSender(sender: String)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Instant, to: Instant): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun getBetween(from: Instant, to: Instant): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransaction(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dedupeKey = :key LIMIT 1")
    suspend fun findByDedupeKey(key: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE amount = :amount AND direction = :direction " +
            "AND timestamp >= :from AND timestamp <= :to",
    )
    suspend fun findSimilar(amount: Double, direction: String, from: Instant, to: Instant): List<TransactionEntity>

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(tx: TransactionEntity)

    @Update
    suspend fun updateTransactions(txs: List<TransactionEntity>)

    @Query("UPDATE transactions SET excluded = :excluded, excludeReason = :reason, ruleId = :ruleId WHERE id IN (:ids)")
    suspend fun setExcluded(ids: List<Long>, excluded: Boolean, reason: String?, ruleId: Long?)

    @Query("UPDATE transactions SET excluded = 0, excludeReason = NULL, ruleId = NULL WHERE ruleId = :ruleId")
    suspend fun clearRule(ruleId: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    // Raw messages
    @Insert
    suspend fun insertRaw(msg: RawMessageEntity): Long

    @Update
    suspend fun updateRaw(msg: RawMessageEntity)

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun getRaw(id: Long): RawMessageEntity?

    @Query("SELECT * FROM raw_messages ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRaw(limit: Int = 200): Flow<List<RawMessageEntity>>

    @Query("SELECT COUNT(*) FROM raw_messages WHERE sender = :sender AND body = :body AND receivedAt = :at")
    suspend fun countSameRaw(sender: String, body: String, at: Instant): Int

    @Query("DELETE FROM raw_messages")
    suspend fun purgeRaw()

    @Query("UPDATE transactions SET rawRef = NULL")
    suspend fun clearRawRefs()

    // Rules
    @Query("SELECT * FROM exclusion_rules ORDER BY id DESC")
    fun observeExclusionRules(): Flow<List<ExclusionRuleEntity>>

    @Query("SELECT * FROM exclusion_rules")
    suspend fun getExclusionRules(): List<ExclusionRuleEntity>

    @Insert
    suspend fun insertExclusionRule(rule: ExclusionRuleEntity): Long

    @Update
    suspend fun updateExclusionRule(rule: ExclusionRuleEntity)

    @Query("DELETE FROM exclusion_rules WHERE id = :id")
    suspend fun deleteExclusionRule(id: Long)

    @Query("SELECT * FROM category_rules ORDER BY id DESC")
    fun observeCategoryRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getCategoryRules(): List<CategoryRuleEntity>

    @Insert
    suspend fun insertCategoryRule(rule: CategoryRuleEntity): Long

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteCategoryRule(id: Long)

    // Categories and budgets
    @Query("SELECT * FROM categories ORDER BY sortOrder, key")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, key")
    suspend fun getCategories(): List<CategoryEntity>

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET monthlyBudget = :budget WHERE key = :key")
    suspend fun setBudget(key: String, budget: Double?)

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    suspend fun deleteDemoTransactions()

    @Query("DELETE FROM accounts WHERE isDemo = 1")
    suspend fun deleteDemoAccounts()

    @Query("DELETE FROM raw_messages WHERE isDemo = 1")
    suspend fun deleteDemoRaw()

    @Query("DELETE FROM exclusion_rules WHERE isDemo = 1")
    suspend fun deleteDemoRules()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY updatedAt DESC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeList(id: Long): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists ORDER BY updatedAt DESC")
    suspend fun getLists(): List<ShoppingListEntity>

    @Insert
    suspend fun insertList(list: ShoppingListEntity): Long

    @Update
    suspend fun updateList(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY sortOrder, id")
    fun observeItems(listId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY sortOrder, id")
    suspend fun getItems(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItems(): List<ShoppingItemEntity>

    @Insert
    suspend fun insertItems(items: List<ShoppingItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM stores")
    fun observeStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores")
    suspend fun getStores(): List<StoreEntity>

    @Upsert
    suspend fun upsertStores(stores: List<StoreEntity>)

    @Query("SELECT * FROM price_reports WHERE itemKey IN (:keys)")
    fun observePrices(keys: List<String>): Flow<List<PriceReportEntity>>

    @Query("SELECT * FROM price_reports WHERE itemKey IN (:keys)")
    suspend fun getPrices(keys: List<String>): List<PriceReportEntity>

    @Query("SELECT * FROM price_reports")
    suspend fun getAllPrices(): List<PriceReportEntity>

    @Query("SELECT * FROM price_reports WHERE isMine = 1 AND uploaded = 0")
    suspend fun pendingUploads(): List<PriceReportEntity>

    @Upsert
    suspend fun upsertPrices(prices: List<PriceReportEntity>)

    @Query("DELETE FROM shopping_lists WHERE isDemo = 1")
    suspend fun deleteDemoLists()

    @Query("DELETE FROM price_reports WHERE isDemo = 1")
    suspend fun deleteDemoPrices()

    @Query("DELETE FROM stores WHERE isDemo = 1")
    suspend fun deleteDemoStores()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM saved_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedDocumentEntity>>

    @Query("SELECT * FROM saved_documents WHERE id = :id")
    fun observe(id: Long): Flow<SavedDocumentEntity?>

    @Query("SELECT * FROM saved_documents WHERE id = :id")
    suspend fun get(id: Long): SavedDocumentEntity?

    @Query("SELECT * FROM saved_documents")
    suspend fun getAll(): List<SavedDocumentEntity>

    @Insert
    suspend fun insert(doc: SavedDocumentEntity): Long

    @Update
    suspend fun update(doc: SavedDocumentEntity)

    @Query("DELETE FROM saved_documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY date")
    fun observeOpenReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY date")
    suspend fun getOpenReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET done = :done WHERE id = :id")
    suspend fun setReminderDone(id: Long, done: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM saved_documents WHERE isDemo = 1")
    suspend fun deleteDemoDocs()

    @Query("DELETE FROM reminders WHERE isDemo = 1")
    suspend fun deleteDemoReminders()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_items WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items WHERE resolved = 0 ORDER BY createdAt DESC")
    suspend fun getOpen(): List<ReviewItemEntity>

    @Query("SELECT * FROM review_items WHERE id = :id")
    suspend fun get(id: Long): ReviewItemEntity?

    @Query("SELECT COUNT(*) FROM review_items WHERE kind = :kind AND refId IS :refId AND refId2 IS :refId2")
    suspend fun countFor(kind: String, refId: Long?, refId2: Long?): Int

    @Insert
    suspend fun insert(item: ReviewItemEntity): Long

    @Query("UPDATE review_items SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long)

    @Query("DELETE FROM review_items WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface ImpactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ImpactEventEntity): Long

    @Query("SELECT * FROM impact_events ORDER BY createdAt")
    suspend fun getAll(): List<ImpactEventEntity>

    @Query("SELECT * FROM impact_events WHERE uploaded = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int = 200): List<ImpactEventEntity>

    @Query("UPDATE impact_events SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM impact_events WHERE event = :event AND createdAt >= :since")
    suspend fun countSince(event: String, since: Instant): Int

    @Query("SELECT COUNT(*) FROM impact_events WHERE event = :event AND detail = :detail")
    suspend fun countWithDetail(event: String, detail: String): Int
}

/** Used by "delete all my data" alongside deleting the database file. */
@Dao
interface MaintenanceDao {
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("SELECT MIN(dueDate) FROM bills WHERE status = 'UNPAID' AND dueDate >= :today")
    suspend fun nextDueDate(today: LocalDate): LocalDate?
}
