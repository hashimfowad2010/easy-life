package com.easylife.app.core.notify

import com.easylife.app.core.data.db.DocumentDao
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.lateFee
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SNotify
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.limits.Risk
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Decides which warnings and reminders to show (runs in the background via WorkManager).
 * Every alert has a de-duplication key so the same warning is never repeated.
 */
@Singleton
class AlertEngine @Inject constructor(
    private val prefs: PrefsRepository,
    private val limits: LimitsRepository,
    private val bills: BillsRepository,
    private val documents: DocumentDao,
    private val notifier: Notifier,
    private val impact: ImpactTracker,
    private val budgetAlerts: BudgetAlertSource,
) {
    suspend fun run(now: LocalTime = LocalTime.now(), today: LocalDate = LocalDate.now()) {
        val p = prefs.current()
        if (!p.onboardingDone) return
        if (Notifier.inQuietHours(now, p.quietStartHour, p.quietEndHour)) return
        val lang = p.language ?: AppLanguage.EN

        if (p.notifyLimits || p.notifyMeterPhoto) checkMeters(lang, today, p.notifyLimits, p.notifyMeterPhoto)
        if (p.notifyBills) checkBills(lang, today)
        if (p.notifyBudget) budgetAlerts.alerts(lang, today).forEach { a ->
            if (prefs.markNotifiedOnce(a.key)) notifier.show(Channel.BUDGET, a.key.hashCode(), a.title, a.body, "budget")
        }
        checkReminders(lang, today)
    }

    private suspend fun checkMeters(lang: AppLanguage, today: LocalDate, warnings: Boolean, photo: Boolean) {
        for (bundle in limits.allBundles()) {
            val d = bundle.dashboard
            val s = d.status ?: continue
            val cycleKey = "${bundle.meter.id}:${d.cycleStart}"
            if (warnings && s.risk != Risk.SAFE) {
                val key = "limit:$cycleKey:${s.risk}"
                if (prefs.markNotifiedOnce(key)) {
                    val limit = Fmt.number(d.limit)
                    val allowance = Fmt.smart(s.dailyAllowance ?: 0.0)
                    val (title, body) = when (s.risk) {
                        Risk.WATCH -> SNotify.watchTitle.get(lang) to SNotify.watchBody.format(
                            lang, Fmt.number(d.projectedUnits ?: 0), Fmt.shortDate(d.cycleEnd ?: today, lang), allowance, limit,
                        )
                        Risk.DANGER -> SNotify.dangerTitle.format(lang, limit) to SNotify.dangerBody.format(
                            lang, limit, Fmt.shortDate(s.projectedCrossDate ?: d.cycleEnd ?: today, lang), allowance,
                        )
                        else -> SNotify.crossedTitle.format(lang, limit) to SNotify.crossedBody.get(lang)
                    }
                    if (notifier.show(Channel.LIMITS, key.hashCode(), title, body, "limits")) {
                        impact.log(ImpactEvents.LIMIT_WARNING_SHOWN, "limits", detail = s.risk.name)
                    }
                }
            }
            if (photo) {
                val since = d.daysSinceLastReading ?: d.cycleStart?.let { ChronoUnit.DAYS.between(it, today) } ?: continue
                val every = bundle.meter.photoReminderDays.coerceAtLeast(1)
                if (since >= every) {
                    // At most once per reminder interval.
                    val bucket = ChronoUnit.DAYS.between(LocalDate.of(2024, 1, 1), today) / every
                    val key = "meterphoto:${bundle.meter.id}:$bucket"
                    if (prefs.markNotifiedOnce(key)) {
                        notifier.show(
                            Channel.METER, key.hashCode(), SNotify.meterPhotoTitle.get(lang),
                            SNotify.meterPhotoBody.format(lang, since.toString()), "limits/reading",
                        )
                    }
                }
            }
        }
    }

    private suspend fun checkBills(lang: AppLanguage, today: LocalDate) {
        for (bill in bills.unpaidNow()) {
            val due = bill.dueDate ?: continue
            val daysLeft = ChronoUnit.DAYS.between(today, due)
            val stage = when {
                daysLeft < 0 -> "overdue"
                daysLeft == 0L -> "0"
                daysLeft <= 2 -> "2"
                daysLeft <= 5 -> "5"
                else -> continue
            }
            val key = "bill:${bill.id}:$stage"
            if (!prefs.markNotifiedOnce(key)) continue
            val provider = bill.provider ?: bill.billType
            val amount = bill.amountWithinDue?.let { Fmt.rupees(it, lang) } ?: ""
            val title = if (stage == "overdue") SNotify.billOverdueTitle.format(lang, provider)
            else SNotify.billDueTitle.format(lang, provider, Fmt.relativeDay(due, today, lang))
            val fee = bill.lateFee
            val body = if (fee != null) SNotify.billDueBody.format(lang, amount, Fmt.shortDate(due, lang), Fmt.rupees(fee, lang))
            else SNotify.billDueBodyNoFee.format(lang, amount, Fmt.shortDate(due, lang))
            if (notifier.show(Channel.BILLS, key.hashCode(), title, body, "bills/${bill.id}")) {
                impact.log(ImpactEvents.BILL_REMINDER_SHOWN, "bills")
            }
        }
    }

    private suspend fun checkReminders(lang: AppLanguage, today: LocalDate) {
        for (r in documents.getOpenReminders()) {
            if (r.date != today && r.date != today.plusDays(1)) continue
            val key = "reminder:${r.id}:${r.date}"
            if (prefs.markNotifiedOnce(key)) {
                notifier.show(Channel.BILLS, key.hashCode(), SNotify.reminderTitle.format(lang, r.title), SNotify.reminderToday.get(lang), "today")
            }
        }
    }

    companion object {
        fun allowanceCeil(v: Double): Int = ceil(v).toInt()
    }
}

/** Budget alerts come from the Budget module; this indirection keeps Phase 1 independent of it. */
data class BudgetAlert(val key: String, val title: String, val body: String)

fun interface BudgetAlertSource {
    suspend fun alerts(lang: AppLanguage, today: LocalDate): List<BudgetAlert>
}
