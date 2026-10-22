package com.easylife.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MeterEntity::class,
        MeterReadingEntity::class,
        UsageMonthEntity::class,
        CustomLimitEntity::class,
        CustomLimitEntryEntity::class,
        ApplianceEntity::class,
        BillEntity::class,
        AccountEntity::class,
        SmsSenderEntity::class,
        TransactionEntity::class,
        RawMessageEntity::class,
        ExclusionRuleEntity::class,
        CategoryRuleEntity::class,
        CategoryEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        StoreEntity::class,
        PriceReportEntity::class,
        SavedDocumentEntity::class,
        ReminderEntity::class,
        ReviewItemEntity::class,
        ImpactEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao
    abstract fun customLimitDao(): CustomLimitDao
    abstract fun applianceDao(): ApplianceDao
    abstract fun billDao(): BillDao
    abstract fun budgetDao(): BudgetDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun documentDao(): DocumentDao
    abstract fun reviewDao(): ReviewDao
    abstract fun impactDao(): ImpactDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "easylife.db"
    }
}
