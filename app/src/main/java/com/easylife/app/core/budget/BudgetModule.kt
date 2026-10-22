package com.easylife.app.core.budget

import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.data.db.BudgetDao
import com.easylife.app.core.data.db.ExclusionRuleEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.DemoContributor
import com.easylife.app.core.data.repo.toInstantLocal
import com.easylife.app.core.data.repo.toTx
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SNotify
import com.easylife.app.core.limits.Risk
import com.easylife.app.core.notify.BudgetAlert
import com.easylife.app.core.notify.BudgetAlertSource
import dagger.Binds
import dagger.Module as DaggerModule
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

/** Budget warnings for the background checks: "Groceries budget 82% used, 9 days left". */
class BudgetAlerts @Inject constructor(
    private val dao: BudgetDao,
    private val repo: BudgetRepository,
    private val prefs: PrefsRepository,
) : BudgetAlertSource {
    override suspend fun alerts(lang: AppLanguage, today: LocalDate): List<BudgetAlert> {
        if (Module.BUDGET !in prefs.current().enabledModules) return emptyList()
        val month = YearMonth.from(today)
        val txs = repo.monthNow(month).map { it.toTx() }
        return dao.getCategories().filter { (it.monthlyBudget ?: 0.0) > 0 }.mapNotNull { c ->
            val s = BudgetInsights.budgetStatus(c.monthlyBudget!!, c.key, month, txs, LocalDateTime.now())
            if (s.risk == Risk.SAFE) return@mapNotNull null
            val name = if (lang == AppLanguage.UR) c.nameUr else c.nameEn
            val pct = Fmt.percent(s.fractionUsed, lang)
            val daysLeft = s.daysRemaining.roundToInt().coerceAtLeast(0)
            BudgetAlert(
                key = "budget:$month:${c.key}:${s.risk}",
                title = SNotify.budgetTitle.format(lang, name, pct),
                body = SNotify.budgetBody.format(lang, Fmt.rupees(maxOf(0.0, s.remaining), lang), daysLeft, Fmt.rupees(s.dailyAllowance ?: 0.0, lang)),
            )
        }
    }
}

/** Demo mode: a month of fictional transactions (one excluded), two accounts, one rule and budgets. */
class BudgetDemo @Inject constructor(private val dao: BudgetDao, private val repo: BudgetRepository) : DemoContributor {
    override suspend fun load(today: LocalDate) {
        val hbl = dao.insertAccount(com.easylife.app.core.data.db.AccountEntity(bankId = "HBL", nickname = "Salary account", last4 = "1234", isDemo = true))
        val jazz = dao.insertAccount(com.easylife.app.core.data.db.AccountEntity(bankId = "JAZZCASH", nickname = "JazzCash", isDemo = true))
        val start = today.withDayOfMonth(1)
        fun at(day: Int, h: Int, m: Int = 0) = start.plusDays((day - 1).toLong().coerceAtMost((today.dayOfMonth - 1).toLong())).atTime(h, m).toInstantLocal()
        val rows = listOf(
            Tx(0, at(1, 10), 85_000.0, false, "SAMPLE COMPANY", null, "ibft", hbl, "income", false),
            Tx(0, at(2, 19), 4_250.0, true, null, "AL-FATAH", "card", hbl, "groceries", false),
            Tx(0, at(3, 13), 1_150.0, true, null, "FOODPANDA", "card", hbl, "food", false),
            Tx(0, at(5, 9), 15_000.0, true, "AMMI (MOTHER)", null, "ibft", hbl, "family", false),
            Tx(0, at(6, 18), 2_600.0, true, null, "NAHEED", "card", hbl, "groceries", false),
            Tx(0, at(8, 8), 3_000.0, true, null, "PSO", "card", hbl, "transport", false),
            Tx(0, at(9, 20), 999.0, true, null, "JAZZ PACKAGE", "wallet", jazz, "mobile", false),
            Tx(0, at(10, 9, 0), 10_000.0, true, "OWN JAZZCASH", null, "ibft", hbl, "transfers", false),
            Tx(0, at(10, 9, 3), 10_000.0, false, "HBL", null, "wallet", jazz, "transfers", false),
            Tx(0, at(12, 17), 5_600.0, true, null, "IMTIAZ", "card", hbl, "groceries", false),
            Tx(0, at(14, 21), 850.0, true, null, "CHEEZIOUS", "card", hbl, "food", false),
            Tx(0, at(15, 11), 12_000.0, true, null, "SAMPLE SCHOOL", "bill", hbl, "education", false),
            Tx(0, at(17, 12), 1_840.0, true, null, "SNGPL", "bill", jazz, "bills", false),
            Tx(0, at(18, 19), 3_900.0, true, null, "AL-FATAH", "card", hbl, "groceries", false),
            Tx(0, at(20, 16), 5_000.0, true, null, null, "atm", hbl, "cash", false),
        )
        rows.forEach { repo.saveTransaction(it, origin = "DEMO", source = Provenance.DEMO.name, isDemo = true, category = it.category) }
        // The money sent to Mother is family support, not household spending: excluded with a rule.
        dao.insertExclusionRule(ExclusionRuleEntity(field = "COUNTERPARTY", value = "AMMI (MOTHER)", isDemo = true))
        dao.getAllTransactions().filter { it.isDemo && it.counterparty == "AMMI (MOTHER)" }.forEach {
            dao.updateTransaction(it.copy(excluded = true, excludeReason = "RULE"))
        }
        dao.setBudget("groceries", 18_000.0)
        dao.setBudget("food", 4_000.0)
        dao.setBudget("transport", 8_000.0)
    }

    override suspend fun clear() {
        dao.deleteDemoTransactions()
        dao.deleteDemoAccounts()
        dao.deleteDemoRaw()
        dao.deleteDemoRules()
    }
}

@DaggerModule
@InstallIn(SingletonComponent::class)
abstract class BudgetBindings {
    @Binds abstract fun alerts(impl: BudgetAlerts): BudgetAlertSource

    @Binds @IntoSet abstract fun demo(impl: BudgetDemo): DemoContributor
}
