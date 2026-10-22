package com.easylife.app.core.budget

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetCoreTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant()

    private fun tx(
        id: Long, amount: Double, debit: Boolean = true, at: Instant = at(10, 12), cp: String? = null, merchant: String? = null,
        channel: String = "card", account: Long? = 1, excluded: Boolean = false, refund: Boolean = false, ref: String? = null,
    ) = Tx(id, at, amount, debit, cp, merchant, channel, account, "other", excluded, refund, ref)

    @Test
    fun excludedTransactionsNeverCount() {
        val txs = listOf(tx(1, 1000.0), tx(2, 2500.0, excluded = true), tx(3, 500.0), tx(4, 3000.0, debit = false))
        assertEquals(1500.0, Exclusions.spent(txs))
        assertEquals(3000.0, Exclusions.received(txs))
        // Undo: including it again brings it back into the total.
        assertEquals(4000.0, Exclusions.spent(txs.map { if (it.id == 2L) it.copy(excluded = false) else it }))
    }

    @Test
    fun rulesMatchCaseInsensitiveAndPausedRulesDoNothing() {
        val rule = ExclusionRule(1, RuleField.COUNTERPARTY, "Ali Raza")
        assertTrue(Exclusions.matches(rule, tx(1, 100.0, cp = "ALI RAZA ")))
        assertEquals(false, Exclusions.matches(rule.copy(paused = true), tx(1, 100.0, cp = "Ali Raza")))
        assertEquals(false, Exclusions.matches(rule, tx(1, 100.0, cp = "Sara Khan")))
        val byChannel = ExclusionRule(2, RuleField.CHANNEL, "atm")
        assertTrue(Exclusions.matches(byChannel, tx(1, 100.0, channel = "atm")))
        val byAccount = ExclusionRule(3, RuleField.ACCOUNT, "7")
        assertTrue(Exclusions.matches(byAccount, tx(1, 100.0, account = 7)))
    }

    @Test
    fun retroactiveRuleChangesPastTotals() {
        val past = listOf(tx(1, 5000.0, cp = "Mother"), tx(2, 5000.0, cp = "Mother"), tx(3, 800.0, merchant = "FOODPANDA"))
        val rule = ExclusionRule(9, RuleField.COUNTERPARTY, "mother")
        val applied = past.map { if (Exclusions.ruleFor(listOf(rule), it) != null) it.copy(excluded = true) else it }
        assertEquals(10800.0, Exclusions.spent(past))
        assertEquals(800.0, Exclusions.spent(applied))
    }

    @Test
    fun selfTransferPairWithinTenMinutes() {
        val txs = listOf(
            tx(1, 20000.0, debit = true, at = at(22, 9, 0), account = 1, channel = "ibft"),
            tx(2, 20000.0, debit = false, at = at(22, 9, 2), account = 2, channel = "ibft"),
            tx(3, 20000.0, debit = false, at = at(22, 11, 30), account = 2, channel = "ibft"),
            tx(4, 999.0, debit = true, at = at(22, 9, 1), account = 1),
        )
        val pairs = Pairing.selfTransfers(txs)
        assertEquals(1, pairs.size)
        assertEquals(1L to 2L, pairs.single().debitId to pairs.single().creditId)
        assertEquals(PairSuggestion.Kind.SELF_TRANSFER, pairs.single().kind)
    }

    @Test
    fun sameAccountIsNotASelfTransfer() {
        val txs = listOf(tx(1, 500.0, at = at(22, 9, 0), account = 1), tx(2, 500.0, debit = false, at = at(22, 9, 1), account = 1))
        assertTrue(Pairing.selfTransfers(txs).isEmpty())
    }

    @Test
    fun refundPairsWithEarlierPurchase() {
        val txs = listOf(
            tx(1, 1299.0, at = at(10, 12), merchant = "DARAZ"),
            tx(2, 1299.0, debit = false, at = at(17, 9), cp = "DARAZ", refund = true),
        )
        val p = Pairing.refunds(txs).single()
        assertEquals(1L, p.debitId)
        assertEquals(2L, p.creditId)
    }

    @Test
    fun categorizer() {
        assertEquals("food", Categorizer.categorize(tx(1, 1.0, merchant = "FOODPANDA"), emptyList()))
        assertEquals("cash", Categorizer.categorize(tx(1, 1.0, channel = "atm"), emptyList()))
        assertEquals("bills", Categorizer.categorize(tx(1, 1.0, merchant = "LESCO", channel = "bill"), emptyList()))
        assertEquals("transfers", Categorizer.categorize(tx(1, 1.0, cp = "Someone", channel = "ibft"), emptyList()))
        assertEquals("income", Categorizer.categorize(tx(1, 1.0, debit = false, cp = "SAMPLE CO"), emptyList()))
        // A user's correction becomes a rule and wins.
        val rules = listOf(Categorizer.Rule("MERCHANT", "foodpanda", "groceries"))
        assertEquals("groceries", Categorizer.categorize(tx(1, 1.0, merchant = "FOODPANDA"), rules))
    }

    @Test
    fun billPaymentMatchesWithinOnePercent() {
        val bills = listOf(OpenBill(5, "LESCO", "electricity", LocalDate.of(2026, 10, 11), 6407.0, 6945.0))
        val today = LocalDate.of(2026, 10, 22)
        assertEquals(5L, BillMatcher.match(tx(1, 6407.0, merchant = "LESCO", channel = "bill", at = at(20, 10)), bills, today))
        assertEquals(5L, BillMatcher.match(tx(1, 6440.0, merchant = "LESCO", channel = "bill", at = at(20, 10)), bills, today))
        assertNull(BillMatcher.match(tx(1, 6600.0, merchant = "LESCO", channel = "bill", at = at(20, 10)), bills, today), "3% off")
        assertNull(BillMatcher.match(tx(1, 6407.0, merchant = "LESCO", channel = "bill", at = at(5, 10)), bills, today), "before issue date")
        assertNull(BillMatcher.match(tx(1, 6407.0, merchant = "KFC", channel = "card", at = at(20, 10)), bills, today), "not the biller")
        assertNull(BillMatcher.match(tx(1, 6407.0, debit = false, merchant = "LESCO", channel = "bill", at = at(20, 10)), bills, today))
    }

    @Test
    fun dedupe() {
        val a = tx(1, 500.0, at = at(22, 9, 0), ref = "TID123")
        assertTrue(Dedupe.isDuplicate(a, tx(2, 500.0, at = at(22, 9, 30), ref = "tid123")))
        assertEquals(false, Dedupe.isDuplicate(a, tx(2, 500.0, at = at(22, 9, 1), ref = "TID999")))
        assertTrue(Dedupe.isDuplicate(tx(1, 500.0, at = at(22, 9, 0)), tx(2, 500.0, at = at(22, 9, 3))))
        assertEquals(false, Dedupe.isDuplicate(tx(1, 500.0, at = at(22, 9, 0)), tx(2, 500.0, at = at(22, 9, 10))))
        assertEquals(Dedupe.key(500.0, true, at(22, 9, 0), "TID1"), Dedupe.key(500.0, true, at(22, 9, 4), "tid1"))
    }

    @Test
    fun senderToBank() {
        assertEquals("MEEZAN", BankCatalog.bankForSender("MeezanBank")?.id)
        assertEquals("JAZZCASH", BankCatalog.bankForSender("JazzCash")?.id)
        assertNull(BankCatalog.bankForSender("8079"))
    }

    @Test
    fun csvStatementWithDebitCreditColumns() {
        val csv = """
            Account statement (fictional)
            Date,Description,Debit,Credit,Balance
            01/10/2026,"SALARY SAMPLE CO",,85000.00,90000.00
            03/10/2026,POS FOODPANDA,"1,250.00",,88750.00
            05/10/2026,ATM CASH,5000,,83750
        """.trimIndent()
        val rows = CsvStatement.parse(csv)
        assertEquals(3, rows.size)
        assertEquals(false, rows[0].debit)
        assertEquals(85000.0, rows[0].amount)
        assertEquals(1250.0, rows[1].amount)
        assertTrue(rows[1].debit)
        assertEquals(LocalDate.of(2026, 10, 3), rows[1].date)
    }
}
