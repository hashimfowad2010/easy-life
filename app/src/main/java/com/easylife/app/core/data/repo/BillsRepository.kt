package com.easylife.app.core.data.repo

import com.easylife.app.core.ai.Masker
import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.data.db.BillDao
import com.easylife.app.core.data.db.BillEntity
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

enum class BillState { UPCOMING, DUE_SOON, OVERDUE, PAID }

fun BillEntity.state(today: LocalDate = LocalDate.now()): BillState = when {
    status == "PAID" -> BillState.PAID
    dueDate == null -> BillState.UPCOMING
    dueDate.isBefore(today) -> BillState.OVERDUE
    !dueDate.isAfter(today.plusDays(5)) -> BillState.DUE_SOON
    else -> BillState.UPCOMING
}

/** Late fee = amount after due date - amount within due date (when both are known). */
val BillEntity.lateFee: Double?
    get() = if (amountWithinDue != null && amountAfterDue != null && amountAfterDue > amountWithinDue) amountAfterDue - amountWithinDue else null

@Singleton
class BillsRepository @Inject constructor(
    private val dao: BillDao,
    private val limits: LimitsRepository,
    private val impact: ImpactTracker,
) {
    val bills: Flow<List<BillEntity>> = dao.observeAll()
    val unpaid: Flow<List<BillEntity>> = dao.observeUnpaid()

    fun observe(id: Long): Flow<BillEntity?> = dao.observe(id)

    suspend fun get(id: Long): BillEntity? = dao.get(id)

    suspend fun unpaidNow(): List<BillEntity> = dao.getUnpaid()

    suspend fun all(): List<BillEntity> = dao.getAll()

    /**
     * Save a confirmed bill. Electricity bills also update the meter in Limits (new cycle + history).
     * Returns the bill id. If the same provider+month already exists it is updated instead of duplicated.
     */
    suspend fun saveConfirmed(extract: BillExtractResult, source: String, ocrText: String?, isDemo: Boolean = false): Long {
        val month = extract.billingMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        var meterId: Long? = null
        if (extract.billType == "electricity") {
            meterId = limits.applyElectricityBill(
                provider = extract.provider,
                referenceNo = extract.referenceNo,
                readingDate = extract.readingDate?.let(LocalDate::parse),
                presentReading = extract.presentReading,
                billingMonth = month,
                unitsConsumed = extract.unitsConsumed,
                amount = extract.amountWithinDue,
                history = extract.history.mapNotNull { h ->
                    val ym = runCatching { YearMonth.parse(h.month) }.getOrNull() ?: return@mapNotNull null
                    h.units?.let { Triple(ym, it, h.amount) }
                },
                source = source,
                isDemo = isDemo,
            )
        }
        val entity = BillEntity(
            billType = extract.billType,
            provider = extract.provider,
            referenceNo = extract.referenceNo,
            billingMonth = extract.billingMonth,
            issueDate = extract.issueDate?.let(LocalDate::parse),
            dueDate = extract.dueDate?.let(LocalDate::parse),
            readingDate = extract.readingDate?.let(LocalDate::parse),
            amountWithinDue = extract.amountWithinDue,
            amountAfterDue = extract.amountAfterDue,
            unitsConsumed = extract.unitsConsumed,
            previousReading = extract.previousReading,
            presentReading = extract.presentReading,
            consumerStatusPrinted = extract.consumerStatusPrinted,
            source = source,
            confidence = extract.confidence,
            uncertainFields = extract.uncertainFields,
            ocrText = ocrText?.let { Masker.forSharing(it).text },
            meterId = meterId,
            isDemo = isDemo,
        )
        val existing = dao.findSame(entity.billType, entity.provider, entity.billingMonth)?.takeIf { it.isDemo == isDemo }
        return if (existing != null) {
            dao.update(entity.copy(id = existing.id, status = existing.status, paidAt = existing.paidAt, paidVia = existing.paidVia, createdAt = existing.createdAt))
            existing.id
        } else {
            dao.insert(entity)
        }
    }

    suspend fun update(bill: BillEntity) = dao.update(bill)

    suspend fun markPaid(id: Long, via: String = "MANUAL", transactionId: Long? = null) {
        val bill = dao.get(id) ?: return
        val now = Instant.now()
        dao.update(bill.copy(status = "PAID", paidAt = now, paidVia = via, paidTransactionId = transactionId))
        val due = bill.dueDate
        if (due != null && !LocalDate.now().isAfter(due)) {
            impact.log(ImpactEvents.BILL_PAID_BEFORE_DUE, "bills")
            bill.lateFee?.let { impact.log(ImpactEvents.LATE_FEE_AVOIDED, "bills", it) }
        }
    }

    suspend fun markUnpaid(id: Long) {
        val bill = dao.get(id) ?: return
        dao.update(bill.copy(status = "UNPAID", paidAt = null, paidVia = null, paidTransactionId = null))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
