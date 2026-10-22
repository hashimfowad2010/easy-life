package com.easylife.app.core.data.repo

import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.ProviderId
import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.data.db.AppDatabase
import com.easylife.app.core.data.db.ApplianceEntity
import com.easylife.app.core.data.db.BillEntity
import com.easylife.app.core.data.db.CustomLimitEntity
import com.easylife.app.core.data.db.MeterReadingEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.limits.ApplianceType
import com.easylife.app.core.limits.LimitMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo mode: loads a FICTIONAL family's data (all rows flagged isDemo and labelled "Demo data" in the UI) so the
 * app can be shown without anyone's real bills. Dates are relative to today so the demo always looks current.
 * Extra modules add their own demo rows through [DemoContributor].
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: AppDatabase,
    private val router: AiRouter,
    private val bills: BillsRepository,
    private val prefs: PrefsRepository,
    private val contributors: Set<@JvmSuppressWildcards DemoContributor>,
) {
    suspend fun load(today: LocalDate = LocalDate.now()) {
        clear()
        // The demo bill comes from the Mock provider so its provenance is "Demo data".
        val canned = router.run(AiTask.BillExtract, AiInput(), force = ProviderId.MOCK).value ?: return
        val month = YearMonth.from(today)
        val readingDate = today.minusDays(13)
        val shiftedHistory = canned.history.mapIndexed { i, h -> h.copy(month = month.minusMonths((12 - i).toLong()).toString()) }
        val bill: BillExtractResult = canned.copy(
            billingMonth = month.toString(),
            readingDate = readingDate.toString(),
            issueDate = readingDate.plusDays(2).toString(),
            dueDate = today.plusDays(2).toString(),
            history = shiftedHistory,
        )
        val billId = bills.saveConfirmed(bill, Provenance.DEMO.name, null, isDemo = true)
        val meterId = bills.get(billId)?.meterId ?: return

        // This cycle: about 8 units a day, heading past 200 before the next reading.
        val base = bill.presentReading ?: 10_611.0
        listOf(3 to 21.0, 6 to 44.0, 9 to 69.0, 12 to 93.0).forEach { (d, plus) ->
            db.meterDao().insertReading(
                MeterReadingEntity(
                    meterId = meterId,
                    takenAt = readingDate.plusDays(d.toLong()).atTime(20, 0).toInstantLocal(),
                    reading = base + plus,
                    source = Provenance.DEMO.name,
                    confidence = 0.95,
                    isDemo = true,
                ),
            )
        }

        val appliances = listOf(
            ApplianceEntity(type = ApplianceType.AC_1_5_TON.name, label = "AC", watts = ApplianceType.AC_1_5_TON.defaultWatts, quantity = 1, hoursPerDay = 5.0, isDemo = true),
            ApplianceEntity(type = ApplianceType.FRIDGE.name, label = "Fridge", watts = ApplianceType.FRIDGE.defaultWatts, quantity = 1, hoursPerDay = 24.0, isDemo = true),
            ApplianceEntity(type = ApplianceType.CEILING_FAN.name, label = "Fans", watts = ApplianceType.CEILING_FAN.defaultWatts, quantity = 4, hoursPerDay = 10.0, isDemo = true),
            ApplianceEntity(type = ApplianceType.LED_LIGHT.name, label = "Lights", watts = ApplianceType.LED_LIGHT.defaultWatts, quantity = 6, hoursPerDay = 6.0, isDemo = true),
            ApplianceEntity(type = ApplianceType.WATER_MOTOR.name, label = "Motor", watts = ApplianceType.WATER_MOTOR.defaultWatts, quantity = 1, hoursPerDay = 1.0, isDemo = true),
            ApplianceEntity(type = ApplianceType.IRON.name, label = "Iron", watts = ApplianceType.IRON.defaultWatts, quantity = 1, hoursPerDay = 0.5, isDemo = true),
        )
        appliances.forEach { db.applianceDao().upsert(it) }

        // Other bills: a gas bill due next week and a school fee already paid.
        db.billDao().insert(
            BillEntity(
                billType = "gas", provider = "SNGPL", billingMonth = month.toString(), issueDate = today.minusDays(3),
                dueDate = today.plusDays(8), amountWithinDue = 1_840.0, amountAfterDue = 2_020.0, source = Provenance.DEMO.name,
                confidence = 0.9, isDemo = true,
            ),
        )
        db.billDao().insert(
            BillEntity(
                billType = "school_fee", provider = "SAMPLE GRAMMAR SCHOOL", billingMonth = month.toString(), issueDate = today.minusDays(21),
                dueDate = today.minusDays(12), amountWithinDue = 13_500.0, amountAfterDue = 14_000.0, status = "PAID",
                paidAt = today.minusDays(14).atTime(11, 0).toInstantLocal(), paidVia = "MANUAL", source = Provenance.DEMO.name,
                confidence = 0.9, isDemo = true,
            ),
        )

        db.customLimitDao().insert(
            CustomLimitEntity(
                name = "Mobile data", unit = "GB", limitValue = 30.0, buffer = 2.0, mode = LimitMode.ADDITIVE_AMOUNT.name,
                periodStart = today.withDayOfMonth(1), periodDays = month.lengthOfMonth(), isDemo = true,
            ),
        ).let { id ->
            listOf(2 to 3.5, 6 to 4.0, 10 to 6.5, 15 to 5.0).filter { it.first < today.dayOfMonth }.forEach { (day, gb) ->
                db.customLimitDao().insertEntry(
                    com.easylife.app.core.data.db.CustomLimitEntryEntity(
                        limitId = id, at = today.withDayOfMonth(day).atTime(21, 0).toInstantLocal(), value = gb,
                    ),
                )
            }
        }

        contributors.forEach { it.load(today) }
        prefs.setSelectedMeter(meterId)
        prefs.setDemoMode(true)
    }

    /** Removes every demo row (real data is untouched). */
    suspend fun clear() {
        contributors.forEach { it.clear() }
        db.billDao().deleteDemo()
        db.meterDao().deleteDemo()
        db.applianceDao().deleteDemo()
        db.customLimitDao().deleteDemo()
        db.reviewDao().deleteDemo()
        db.documentDao().deleteDemoDocs()
        db.documentDao().deleteDemoReminders()
        prefs.setDemoMode(false)
        prefs.setSelectedMeter(null)
    }
}

/** Lets Budget, Urdu Helper and Shopping add their own demo rows. */
interface DemoContributor {
    suspend fun load(today: LocalDate)
    suspend fun clear()
}
