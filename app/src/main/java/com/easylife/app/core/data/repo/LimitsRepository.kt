package com.easylife.app.core.data.repo

import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.data.db.ApplianceDao
import com.easylife.app.core.data.db.ApplianceEntity
import com.easylife.app.core.data.db.CustomLimitDao
import com.easylife.app.core.data.db.CustomLimitEntity
import com.easylife.app.core.data.db.CustomLimitEntryEntity
import com.easylife.app.core.data.db.MeterDao
import com.easylife.app.core.data.db.MeterEntity
import com.easylife.app.core.data.db.MeterReadingEntity
import com.easylife.app.core.data.db.UsageMonthEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.limits.BillingMode
import com.easylife.app.core.limits.ConsumerCategory
import com.easylife.app.core.limits.LimitEngine
import com.easylife.app.core.limits.LimitInput
import com.easylife.app.core.limits.LimitMode
import com.easylife.app.core.limits.LimitStatus
import com.easylife.app.core.limits.MeterDashboard
import com.easylife.app.core.limits.MeterDashboardCalculator
import com.easylife.app.core.limits.MonthUsage
import com.easylife.app.core.limits.Observation
import com.easylife.app.core.limits.TariffCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class MeterBundle(
    val meter: MeterEntity,
    val readings: List<MeterReadingEntity>,
    val usage: List<UsageMonthEntity>,
    val dashboard: MeterDashboard,
)

data class CustomLimitBundle(val limit: CustomLimitEntity, val entries: List<CustomLimitEntryEntity>, val status: LimitStatus)

fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
fun LocalDateTime.toInstantLocal(): Instant = atZone(ZoneId.systemDefault()).toInstant()

@Singleton
class LimitsRepository @Inject constructor(
    private val meterDao: MeterDao,
    private val customDao: CustomLimitDao,
    private val applianceDao: ApplianceDao,
    private val config: RemoteConfigRepository,
    private val prefs: PrefsRepository,
) {
    val meters: Flow<List<MeterEntity>> = meterDao.observeMeters()

    fun tariff(): TariffCalculator = TariffCalculator(config.current.tariffs)

    /** The meter shown on the dashboard: the user's selection, else the first one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMeter: Flow<MeterBundle?> = combine(meters, prefs.prefs.map { it.selectedMeterId }) { list, selected ->
        list.firstOrNull { it.id == selected } ?: list.firstOrNull()
    }.flatMapLatest { meter -> if (meter == null) flowOf(null) else observeBundle(meter.id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBundle(meterId: Long): Flow<MeterBundle?> =
        combine(meterDao.observeMeter(meterId), meterDao.observeReadings(meterId), meterDao.observeUsage(meterId), config.config) { m, r, u, cfg ->
            m?.let { MeterBundle(it, r, u, dashboard(it, r, u, TariffCalculator(cfg.tariffs))) }
        }

    fun dashboard(meter: MeterEntity, readings: List<MeterReadingEntity>, usage: List<UsageMonthEntity>, tariff: TariffCalculator, now: LocalDateTime = LocalDateTime.now()): MeterDashboard =
        MeterDashboardCalculator.compute(
            MeterDashboardCalculator.Input(
                meterId = meter.id,
                limitUnits = meter.limitUnits,
                safetyBuffer = meter.safetyBuffer,
                cycleStartDate = meter.cycleStartDate,
                cycleStartReading = meter.cycleStartReading,
                expectedReadingDate = meter.expectedReadingDate,
                categoryOverride = ConsumerCategory.entries.firstOrNull { it.name == meter.categoryOverride },
                billingModeOverride = BillingMode.entries.firstOrNull { it.name == meter.billingModeOverride },
                calibrationMultiplier = meter.calibrationMultiplier,
                meterDigits = meter.meterDigits,
                readings = readings.map { MeterDashboardCalculator.Reading(it.takenAt.toLocal(), it.reading, it.id) },
                history = usage.map { MonthUsage(YearMonth.parse(it.month), it.units) },
                now = now,
            ),
            tariff,
        )

    suspend fun allBundles(): List<MeterBundle> {
        val tariff = tariff()
        return meterDao.getMeters().map { m ->
            val r = meterDao.getReadings(m.id)
            val u = meterDao.getUsage(m.id)
            MeterBundle(m, r, u, dashboard(m, r, u, tariff))
        }
    }

    suspend fun meter(id: Long): MeterEntity? = meterDao.getMeter(id)

    suspend fun saveMeter(meter: MeterEntity): Long =
        if (meter.id == 0L) meterDao.insertMeter(meter).also { prefs.setSelectedMeter(it) } else meter.id.also { meterDao.updateMeter(meter) }

    suspend fun deleteMeter(meter: MeterEntity) = meterDao.deleteMeter(meter)

    suspend fun addReading(meterId: Long, value: Double, at: Instant, source: String, confidence: Double?): Long =
        meterDao.insertReading(MeterReadingEntity(meterId = meterId, takenAt = at, reading = value, source = source, confidence = confidence))

    suspend fun deleteReading(id: Long) = meterDao.deleteReading(id)

    suspend fun latestReading(meterId: Long): MeterReadingEntity? = meterDao.latestReading(meterId)

    suspend fun setCalibration(meterId: Long, multiplier: Double?) {
        meterDao.getMeter(meterId)?.let { meterDao.updateMeter(it.copy(calibrationMultiplier = multiplier)) }
    }

    /**
     * An electricity bill starts a new cycle: last official reading + date, expected next reading ~1 month later,
     * and the bill's 12-month history. Creates the meter if this is the first bill.
     */
    suspend fun applyElectricityBill(
        provider: String?,
        referenceNo: String?,
        readingDate: LocalDate?,
        presentReading: Double?,
        billingMonth: YearMonth?,
        unitsConsumed: Int?,
        amount: Double?,
        history: List<Triple<YearMonth, Int, Double?>>,
        source: String,
        isDemo: Boolean = false,
    ): Long {
        // Demo data never touches a real meter, and real bills never attach to a demo meter.
        val sameKind = meterDao.getMeters().filter { it.isDemo == isDemo }
        val existing = referenceNo?.let { meterDao.findByReference(it) }?.takeIf { it.isDemo == isDemo } ?: sameKind.firstOrNull()
        val base = existing ?: MeterEntity(name = "Home", provider = provider ?: "LESCO", referenceNo = referenceNo, isDemo = isDemo)
        val newer = readingDate != null && (base.cycleStartDate == null || !readingDate.isBefore(base.cycleStartDate))
        val updated = base.copy(
            provider = provider ?: base.provider,
            referenceNo = referenceNo ?: base.referenceNo,
            cycleStartDate = if (newer) readingDate else base.cycleStartDate,
            cycleStartReading = if (newer) presentReading ?: base.cycleStartReading else base.cycleStartReading,
            expectedReadingDate = if (newer) readingDate?.plusMonths(1) else base.expectedReadingDate,
        )
        val id = saveMeter(updated)
        val months = history.map { (m, units, amt) -> UsageMonthEntity(id, m.toString(), units, amt, source, isDemo) }.toMutableList()
        if (billingMonth != null && unitsConsumed != null) months += UsageMonthEntity(id, billingMonth.toString(), unitsConsumed, amount, source, isDemo)
        if (months.isNotEmpty()) meterDao.upsertUsage(months)
        return id
    }

    // ---- custom limits

    val customLimits: Flow<List<CustomLimitEntity>> = customDao.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCustom(id: Long): Flow<CustomLimitBundle?> = combine(customDao.observe(id), customDao.observeEntries(id)) { l, e ->
        l?.let { CustomLimitBundle(it, e, customStatus(it, e)) }
    }

    fun customStatus(limit: CustomLimitEntity, entries: List<CustomLimitEntryEntity>, now: LocalDateTime = LocalDateTime.now()): LimitStatus {
        // Rolling periods: the current period is the one containing today.
        var start = limit.periodStart
        while (!start.plusDays(limit.periodDays.toLong()).isAfter(now.toLocalDate())) start = start.plusDays(limit.periodDays.toLong())
        val mode = LimitMode.valueOf(limit.mode)
        return LimitEngine.evaluate(
            LimitInput(
                limit = limit.limitValue,
                buffer = limit.buffer,
                periodStart = start,
                periodEnd = start.plusDays(limit.periodDays.toLong() - 1),
                mode = mode,
                baseline = if (mode == LimitMode.CUMULATIVE_READING) entries.filter { it.at.toLocal().toLocalDate() < start }.maxByOrNull { it.at }?.value ?: limit.baseline else null,
                observations = entries.map { Observation(it.at.toLocal(), it.value, it.id) },
                now = now,
            ),
        )
    }

    suspend fun allCustomStatuses(): List<CustomLimitBundle> = customDao.getAll().map { l ->
        val e = customDao.getEntries(l.id)
        CustomLimitBundle(l, e, customStatus(l, e))
    }

    suspend fun saveCustom(limit: CustomLimitEntity): Long = if (limit.id == 0L) customDao.insert(limit) else limit.id.also { customDao.update(limit) }

    suspend fun deleteCustom(id: Long) = customDao.delete(id)

    suspend fun addCustomEntry(limitId: Long, value: Double, note: String?) =
        customDao.insertEntry(CustomLimitEntryEntity(limitId = limitId, at = Instant.now(), value = value, note = note))

    suspend fun deleteCustomEntry(id: Long) = customDao.deleteEntry(id)

    // ---- appliances

    val appliances: Flow<List<ApplianceEntity>> = applianceDao.observeAll()

    suspend fun saveAppliance(a: ApplianceEntity) = applianceDao.upsert(a)

    suspend fun deleteAppliance(id: Long) = applianceDao.delete(id)

    suspend fun firstMeterId(): Long? = meters.first().firstOrNull()?.id
}
