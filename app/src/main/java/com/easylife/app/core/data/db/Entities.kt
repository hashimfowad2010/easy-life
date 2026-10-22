package com.easylife.app.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

// All financial data lives only in this encrypted database (SQLCipher) and is never synced.
// `isDemo` rows are created by Demo mode and removed when it is switched off.
// `source` columns hold a Provenance name (ON_DEVICE, GEMINI_FREE, CLAUDE, DEMO, MANUAL, ...).

// ---------------------------------------------------------------- Limits (Bijli Guard)

@Entity(tableName = "meters")
data class MeterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val provider: String = "LESCO",
    /** Stored on this phone only. */
    val referenceNo: String? = null,
    val limitUnits: Int = 200,
    val safetyBuffer: Int = 5,
    /** Date of the last official reading (from the last bill). The current cycle starts here. */
    val cycleStartDate: LocalDate? = null,
    /** "Present reading" printed on the last bill. */
    val cycleStartReading: Double? = null,
    /** When the meter reader is expected next. Editable; pre-filled from the last bill. */
    val expectedReadingDate: LocalDate? = null,
    /** AUTO (from bill history), LIFELINE, PROTECTED or NON_PROTECTED (user override). */
    val categoryOverride: String = "AUTO",
    /** INCREMENTAL / SINGLE_SLAB, or null to follow the tariff config. */
    val billingModeOverride: String? = null,
    val calibrationMultiplier: Double? = null,
    val meterDigits: Int = 5,
    val photoReminderDays: Int = 3,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "meter_readings",
    indices = [Index("meterId"), Index("takenAt")],
    foreignKeys = [ForeignKey(MeterEntity::class, ["id"], ["meterId"], onDelete = ForeignKey.CASCADE)],
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterId: Long,
    val takenAt: Instant,
    val reading: Double,
    val source: String,
    val confidence: Double? = null,
    val note: String? = null,
    val isDemo: Boolean = false,
)

/** Monthly units per meter, mostly from the bill's 12-month history table. */
@Entity(
    tableName = "usage_months",
    primaryKeys = ["meterId", "month"],
    foreignKeys = [ForeignKey(MeterEntity::class, ["id"], ["meterId"], onDelete = ForeignKey.CASCADE)],
)
data class UsageMonthEntity(
    val meterId: Long,
    /** YYYY-MM */
    val month: String,
    val units: Int,
    val amount: Double? = null,
    val source: String,
    val isDemo: Boolean = false,
)

@Entity(tableName = "custom_limits")
data class CustomLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val limitValue: Double,
    val buffer: Double = 0.0,
    /** CUMULATIVE_READING or ADDITIVE_AMOUNT */
    val mode: String,
    val periodStart: LocalDate,
    val periodDays: Int = 30,
    val baseline: Double? = null,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "custom_limit_entries",
    indices = [Index("limitId")],
    foreignKeys = [ForeignKey(CustomLimitEntity::class, ["id"], ["limitId"], onDelete = ForeignKey.CASCADE)],
)
data class CustomLimitEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val limitId: Long,
    val at: Instant,
    val value: Double,
    val note: String? = null,
)

@Entity(tableName = "appliances")
data class ApplianceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val label: String,
    val watts: Int,
    val quantity: Int,
    val hoursPerDay: Double,
    val isDemo: Boolean = false,
)

// ---------------------------------------------------------------- Bills

@Entity(tableName = "bills", indices = [Index("dueDate"), Index("meterId")])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** electricity, gas, water, internet, mobile_postpaid, school_fee, rent, other */
    val billType: String,
    val provider: String? = null,
    val referenceNo: String? = null,
    /** YYYY-MM */
    val billingMonth: String? = null,
    val issueDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val readingDate: LocalDate? = null,
    val amountWithinDue: Double? = null,
    val amountAfterDue: Double? = null,
    val unitsConsumed: Int? = null,
    val previousReading: Double? = null,
    val presentReading: Double? = null,
    val consumerStatusPrinted: String? = null,
    /** UNPAID or PAID */
    val status: String = "UNPAID",
    val paidAt: Instant? = null,
    /** MANUAL or SMS */
    val paidVia: String? = null,
    val paidTransactionId: Long? = null,
    val source: String,
    val confidence: Double = 1.0,
    val uncertainFields: List<String> = emptyList(),
    /** Masked OCR text kept on the phone for "Show in Urdu". */
    val ocrText: String? = null,
    val meterId: Long? = null,
    val notes: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

// ---------------------------------------------------------------- Budget

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Bank/wallet id from BankCatalog (HBL, UBL, JAZZCASH, ...). */
    val bankId: String,
    val nickname: String,
    /** Optional last 4 digits only. Never a full account number. */
    val last4: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

/** Only SMS from allow-listed senders are ever processed. */
@Entity(tableName = "sms_senders")
data class SmsSenderEntity(
    @PrimaryKey val sender: String,
    val bankId: String,
    val accountId: Long? = null,
    val allowed: Boolean = true,
)

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("category"), Index("dedupeKey"), Index("accountId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val amount: Double,
    val currency: String = "PKR",
    /** DEBIT (money out) or CREDIT (money in) */
    val direction: String,
    val counterparty: String? = null,
    val merchant: String? = null,
    /** CARD, IBFT, RAAST, ATM, WALLET, BILL, CHEQUE, CASH, OTHER */
    val channel: String = "OTHER",
    val accountLabel: String? = null,
    val accountId: Long? = null,
    val category: String = "other",
    /** SMS, NOTIFICATION, STATEMENT, RECEIPT, MANUAL, DEMO */
    val origin: String,
    /** Provenance of the parse (ON_DEVICE, CLAUDE, ...). */
    val source: String,
    val excluded: Boolean = false,
    /** USER, RULE, SELF_TRANSFER, REFUND */
    val excludeReason: String? = null,
    val ruleId: Long? = null,
    val notes: String? = null,
    val rawRef: Long? = null,
    val referenceId: String? = null,
    val balanceAfter: Double? = null,
    val isRefund: Boolean = false,
    val dedupeKey: String? = null,
    val categoryConfirmed: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

/** Raw bank/wallet messages, encrypted at rest with the DB. OTP/security SMS are never stored. */
@Entity(tableName = "raw_messages", indices = [Index("receivedAt")])
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    /** SMS or NOTIFICATION */
    val channel: String,
    /** PARSED, REVIEW, PROMO, DUPLICATE, IGNORED */
    val status: String,
    val transactionId: Long? = null,
    val templateId: String? = null,
    val isDemo: Boolean = false,
)

@Entity(tableName = "exclusion_rules")
data class ExclusionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** COUNTERPARTY, MERCHANT, CHANNEL, ACCOUNT */
    val field: String,
    val value: String,
    val paused: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MERCHANT or COUNTERPARTY */
    val field: String,
    val pattern: String,
    val category: String,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,
    val nameEn: String,
    val nameUr: String,
    val icon: String,
    val color: Long,
    val monthlyBudget: Double? = null,
    val sortOrder: Int = 0,
    val isCustom: Boolean = false,
)

// ---------------------------------------------------------------- Shopping

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

@Entity(
    tableName = "shopping_items",
    indices = [Index("listId")],
    foreignKeys = [ForeignKey(ShoppingListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE)],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val itemKey: String? = null,
    val nameEn: String,
    val nameUr: String,
    val rawText: String? = null,
    val qty: Double = 1.0,
    val unit: String? = null,
    val brandPref: String? = null,
    val category: String? = null,
    /** 1 = must have, 2 = can skip */
    val priority: Int = 1,
    val checked: Boolean = false,
    val manualPrice: Double? = null,
    val manualPriceSource: String? = null,
    val manualPriceAt: Instant? = null,
    val sortOrder: Int = 0,
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val area: String? = null,
    val city: String,
    /** Rounded coordinates only (never an exact location). */
    val latRound: Double? = null,
    val lngRound: Double? = null,
    val isLocalOnly: Boolean = true,
    val isDemo: Boolean = false,
)

@Entity(tableName = "price_reports", indices = [Index("itemKey"), Index("storeId")])
data class PriceReportEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val itemKey: String,
    val itemName: String,
    val pricePkr: Double,
    val packSize: Double,
    val unit: String,
    val reportedAt: Instant,
    val isMine: Boolean = false,
    val uploaded: Boolean = false,
    val isDemo: Boolean = false,
)

// ---------------------------------------------------------------- Urdu Helper and shared

@Entity(tableName = "saved_documents")
data class SavedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val docType: String,
    val sourceText: String,
    val translatedText: String? = null,
    /** JSON of extracted key details/actions. */
    val insightsJson: String? = null,
    val source: String,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "reminders", indices = [Index("date")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val sourceDocId: Long? = null,
    val done: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

/** Things the app was not sure about, waiting for the user (low-confidence results, unknown SMS). */
@Entity(tableName = "review_items")
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SMS, BILL, METER, RECEIPT, BILL_PAYMENT_MATCH, SELF_TRANSFER, REFUND */
    val kind: String,
    val refId: Long? = null,
    val refId2: Long? = null,
    val title: String,
    val payload: String? = null,
    val reason: String,
    val resolved: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

/** Local log of anonymous impact events (counts/rounded values). Uploaded only with opt-in. */
@Entity(tableName = "impact_events", indices = [Index("event")])
data class ImpactEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val event: String,
    val module: String,
    val value: Double? = null,
    val detail: String? = null,
    val createdAt: Instant = Instant.now(),
    val uploaded: Boolean = false,
)
