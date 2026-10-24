package com.easylife.app.core.data.repo

import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.budget.BankCatalog
import com.easylife.app.core.budget.BillMatcher
import com.easylife.app.core.budget.Categorizer
import com.easylife.app.core.budget.CsvStatement
import com.easylife.app.core.budget.Dedupe
import com.easylife.app.core.budget.ExclusionRule
import com.easylife.app.core.budget.Exclusions
import com.easylife.app.core.budget.OpenBill
import com.easylife.app.core.budget.Pairing
import com.easylife.app.core.budget.PairSuggestion
import com.easylife.app.core.budget.PastedSms
import com.easylife.app.core.budget.RuleField
import com.easylife.app.core.budget.Tx
import com.easylife.app.core.data.db.AccountEntity
import com.easylife.app.core.data.db.BillDao
import com.easylife.app.core.data.db.BudgetDao
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.db.CategoryRuleEntity
import com.easylife.app.core.data.db.ExclusionRuleEntity
import com.easylife.app.core.data.db.RawMessageEntity
import com.easylife.app.core.data.db.ReviewDao
import com.easylife.app.core.data.db.ReviewItemEntity
import com.easylife.app.core.data.db.SmsSenderEntity
import com.easylife.app.core.data.db.TransactionEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.parse.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

fun TransactionEntity.toTx(): Tx = Tx(
    id = id, at = timestamp, amount = amount, debit = direction == "DEBIT", counterparty = counterparty, merchant = merchant,
    channel = channel.lowercase(), accountId = accountId, category = category, excluded = excluded, isRefund = isRefund, referenceId = referenceId,
)

fun ExclusionRuleEntity.toRule(): ExclusionRule = ExclusionRule(id, RuleField.valueOf(field), value, paused)

fun YearMonth.startInstant(): Instant = atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
fun YearMonth.endInstant(): Instant = plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

enum class IngestResult { DISCARDED_SECURITY, NOT_ALLOWED, PROMO, SAVED, DUPLICATE, REVIEW }

/**
 * Budget data and the SMS pipeline. Everything stays on the phone.
 * Pipeline: OTP/security filter (discard, nothing stored) -> sender allow-list -> parse (on-device templates; Claude
 * only if enabled and consented, masked) -> de-duplicate -> category + exclusion rules -> save -> suggestions.
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val reviewDao: ReviewDao,
    private val billDao: BillDao,
    private val bills: BillsRepository,
    private val router: AiRouter,
    private val prefs: PrefsRepository,
    private val impact: ImpactTracker,
) {
    private val mutex = Mutex()

    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val senders: Flow<List<SmsSenderEntity>> = dao.observeSenders()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val exclusionRules: Flow<List<ExclusionRuleEntity>> = dao.observeExclusionRules()
    val categoryRules: Flow<List<CategoryRuleEntity>> = dao.observeCategoryRules()
    val recent: Flow<List<TransactionEntity>> = dao.observeRecent()
    val reviews: Flow<List<ReviewItemEntity>> = reviewDao.observeOpen()

    fun month(m: YearMonth): Flow<List<TransactionEntity>> = dao.observeBetween(m.startInstant(), m.endInstant())
    suspend fun monthNow(m: YearMonth): List<TransactionEntity> = dao.getBetween(m.startInstant(), m.endInstant())
    fun transaction(id: Long): Flow<TransactionEntity?> = dao.observeTransaction(id)

    // ---------------------------------------------------------------- accounts & senders

    suspend fun addAccount(bankId: String, nickname: String, last4: String?): Long =
        dao.insertAccount(AccountEntity(bankId = bankId, nickname = nickname.ifBlank { BankCatalog.byId(bankId).nameEn }, last4 = last4?.filter(Char::isDigit)?.takeLast(4)?.ifBlank { null }))

    suspend fun deleteAccount(id: Long) = dao.deleteAccount(id)

    suspend fun allowSenders(list: List<Pair<String, String>>) =
        dao.upsertSenders(list.map { (sender, bank) -> SmsSenderEntity(sender = sender, bankId = bank, allowed = true) })

    /** A sender is processed only if the user allowed it, or it clearly belongs to a bank the user added. */
    suspend fun isAllowed(sender: String): Boolean {
        if (dao.getSenders().any { it.sender.equals(sender, true) && it.allowed }) return true
        val bank = BankCatalog.bankForSender(sender) ?: return false
        return dao.getAccounts().any { it.bankId == bank.id }
    }

    private suspend fun accountFor(sender: String): AccountEntity? {
        val explicit = dao.getSenders().firstOrNull { it.sender.equals(sender, true) }
        val accounts = dao.getAccounts()
        explicit?.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { return it } }
        val bankId = explicit?.bankId ?: BankCatalog.bankForSender(sender)?.id ?: return null
        return accounts.firstOrNull { it.bankId == bankId }
    }

    // ---------------------------------------------------------------- ingest

    /**
     * @param userChosen the user pasted or shared this very message, so the sender allow-list does not apply.
     */
    suspend fun ingest(
        sender: String,
        body: String,
        receivedAt: Instant,
        channel: String = "SMS",
        isDemo: Boolean = false,
        userChosen: Boolean = false,
    ): IngestResult = mutex.withLock {
        // 1. OTP / security messages: discarded before anything else, never stored.
        if (SmsParser.isSecurityMessage(body)) return IngestResult.DISCARDED_SECURITY
        // 2. Only allow-listed senders (or a message the user chose to add).
        if (!isDemo && !userChosen && !isAllowed(sender)) return IngestResult.NOT_ALLOWED
        // Same message delivered twice (e.g. re-import).
        if (dao.countSameRaw(sender, body, receivedAt) > 0) return IngestResult.DUPLICATE

        val keepRaw = prefs.current().keepRawSms
        val local = LocalDateTime.ofInstant(receivedAt, ZoneId.systemDefault())
        // 3. Parse (on-device first; a masked cloud fallback only when enabled + consented, never free tier).
        val r = router.run(AiTask.SmsParse, AiInput(text = body, params = mapOf("sender" to sender, "receivedAt" to local.toString())))
        val parsed = r.value
        val rawId = if (keepRaw) dao.insertRaw(RawMessageEntity(sender = sender, body = body, receivedAt = receivedAt, channel = channel, status = "PENDING", isDemo = isDemo)) else null

        if (parsed == null || !parsed.isTransaction) {
            val status = if (parsed?.kind == "promo") "PROMO" else "REVIEW"
            rawId?.let { id -> dao.getRaw(id)?.let { dao.updateRaw(it.copy(status = status)) } }
            if (status == "REVIEW") {
                reviewDao.insert(ReviewItemEntity(kind = "SMS", refId = rawId, title = sender, payload = if (keepRaw) body.take(300) else null, reason = "unknown_format", isDemo = isDemo))
                return IngestResult.REVIEW
            }
            return IngestResult.PROMO
        }
        val amount = parsed.amount ?: return IngestResult.REVIEW
        val at = parsed.timestamp?.let { runCatching { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull() } ?: receivedAt
        val debit = parsed.direction != "credit"
        val account = accountFor(sender)

        // 4. De-duplicate (SMS + notification copies, re-imports).
        val key = Dedupe.key(amount, debit, at, parsed.referenceId)
        val similar = dao.findSimilar(amount, if (debit) "DEBIT" else "CREDIT", at.minusSeconds(600), at.plusSeconds(600))
        val candidate = Tx(0, at, amount, debit, parsed.counterparty, parsed.merchant, parsed.channel ?: "other", account?.id, "other", false, parsed.isRefund == true, parsed.referenceId)
        if (dao.findByDedupeKey(key) != null || similar.any { Dedupe.isDuplicate(it.toTx(), candidate) }) {
            rawId?.let { id -> dao.getRaw(id)?.let { dao.updateRaw(it.copy(status = "DUPLICATE")) } }
            return IngestResult.DUPLICATE
        }

        val txId = saveTransaction(
            candidate,
            origin = channel,
            source = r.provenance.name,
            accountLabel = account?.let { a -> a.nickname + (a.last4?.let { " ··$it" } ?: "") },
            rawRef = rawId,
            dedupeKey = key,
            balanceAfter = parsed.balanceAfter,
            isDemo = isDemo,
        )
        rawId?.let { id -> dao.getRaw(id)?.let { dao.updateRaw(it.copy(status = "PARSED", transactionId = txId, templateId = parsed.templateId)) } }
        if (r.confidence < 0.6 || parsed.uncertainFields.contains("direction")) {
            reviewDao.insert(ReviewItemEntity(kind = "TRANSACTION", refId = txId, title = sender, reason = "low_confidence", isDemo = isDemo))
            return IngestResult.REVIEW
        }
        return IngestResult.SAVED
    }

    data class PasteSummary(val added: Int, val toReview: Int, val securityDiscarded: Int, val duplicates: Int, val notTransactions: Int) {
        val total: Int get() = added + toReview + securityDiscarded + duplicates + notTransactions
    }

    /**
     * Bank or wallet messages the user pasted or shared (one or many, separated by a blank line). Same pipeline as
     * SMS: OTPs dropped first, parsed on the phone, de-duplicated, then saved or sent to the review inbox.
     */
    suspend fun ingestPasted(text: String, receivedAt: Instant = Instant.now()): PasteSummary {
        var added = 0
        var review = 0
        var security = 0
        var duplicates = 0
        var other = 0
        PastedSms.split(text).forEach { body ->
            when (ingest(PastedSms.senderFor(body), body, receivedAt, channel = "PASTE", userChosen = true)) {
                IngestResult.SAVED -> added++
                IngestResult.REVIEW -> review++
                IngestResult.DISCARDED_SECURITY -> security++
                IngestResult.DUPLICATE -> duplicates++
                IngestResult.PROMO, IngestResult.NOT_ALLOWED -> other++
            }
        }
        return PasteSummary(added, review, security, duplicates, other)
    }

    /** Categorise, apply exclusion rules, save, then look for bill payments / transfers / refunds. */
    suspend fun saveTransaction(
        tx: Tx,
        origin: String,
        source: String,
        accountLabel: String? = null,
        rawRef: Long? = null,
        dedupeKey: String? = null,
        balanceAfter: Double? = null,
        notes: String? = null,
        isDemo: Boolean = false,
        category: String? = null,
    ): Long {
        val catRules = dao.getCategoryRules().map { Categorizer.Rule(it.field, it.pattern, it.category) }
        val cat = category ?: Categorizer.categorize(tx, catRules)
        val rule = Exclusions.ruleFor(dao.getExclusionRules().map { it.toRule() }, tx.copy(category = cat))
        val entity = TransactionEntity(
            timestamp = tx.at,
            amount = tx.amount,
            direction = if (tx.debit) "DEBIT" else "CREDIT",
            counterparty = tx.counterparty,
            merchant = tx.merchant,
            channel = tx.channel.uppercase(),
            accountLabel = accountLabel,
            accountId = tx.accountId,
            category = cat,
            origin = origin,
            source = source,
            excluded = rule != null,
            excludeReason = rule?.let { "RULE" },
            ruleId = rule?.id,
            notes = notes,
            rawRef = rawRef,
            referenceId = tx.referenceId,
            balanceAfter = balanceAfter,
            isRefund = tx.isRefund,
            dedupeKey = dedupeKey,
            isDemo = isDemo,
        )
        val id = dao.insertTransaction(entity)
        suggestFor(id, isDemo)
        return id
    }

    private suspend fun suggestFor(txId: Long, isDemo: Boolean) {
        val tx = dao.getTransaction(txId)?.toTx() ?: return
        // Bill auto-match (asks once).
        val open = billDao.getUnpaid().map { OpenBill(it.id, it.provider, it.billType, it.issueDate, it.amountWithinDue, it.amountAfterDue) }
        BillMatcher.match(tx, open, LocalDate.now())?.let { billId ->
            if (reviewDao.countFor("BILL_PAYMENT_MATCH", txId, billId) == 0) {
                reviewDao.insert(ReviewItemEntity(kind = "BILL_PAYMENT_MATCH", refId = txId, refId2 = billId, title = "bill", reason = "amount_match", isDemo = isDemo))
            }
        }
        // Self-transfers and refunds around this transaction.
        val window = dao.getBetween(tx.at.minus(61, ChronoUnit.DAYS), tx.at.plus(1, ChronoUnit.DAYS)).map { it.toTx() }
        (Pairing.selfTransfers(window) + Pairing.refunds(window))
            .filter { it.debitId == txId || it.creditId == txId }
            .forEach { p ->
                val kind = if (p.kind == PairSuggestion.Kind.SELF_TRANSFER) "SELF_TRANSFER" else "REFUND"
                if (reviewDao.countFor(kind, p.debitId, p.creditId) == 0) {
                    reviewDao.insert(ReviewItemEntity(kind = kind, refId = p.debitId, refId2 = p.creditId, title = kind, reason = "pair", isDemo = isDemo))
                }
            }
    }

    // ---------------------------------------------------------------- user actions

    suspend fun setExcluded(ids: List<Long>, excluded: Boolean, reason: String = "USER") {
        dao.setExcluded(ids, excluded, if (excluded) reason else null, null)
        if (excluded) ids.forEach { _ -> impact.log(ImpactEvents.TX_EXCLUDED, "budget") }
    }

    /** "Exclude similar…": a rule for the future, optionally applied to past transactions. Returns how many changed. */
    suspend fun createExclusionRule(field: RuleField, value: String, applyToPast: Boolean): Int {
        val id = dao.insertExclusionRule(ExclusionRuleEntity(field = field.name, value = value.trim()))
        if (!applyToPast) return 0
        val rule = ExclusionRule(id, field, value)
        val matching = dao.getAllTransactions().filter { !it.excluded && Exclusions.matches(rule, it.toTx()) }
        if (matching.isNotEmpty()) dao.setExcluded(matching.map { it.id }, true, "RULE", id)
        return matching.size
    }

    suspend fun setRulePaused(rule: ExclusionRuleEntity, paused: Boolean) = dao.updateExclusionRule(rule.copy(paused = paused))

    /** Deleting a rule brings back the transactions it had excluded. */
    suspend fun deleteExclusionRule(id: Long) {
        dao.clearRule(id)
        dao.deleteExclusionRule(id)
    }

    suspend fun deleteCategoryRule(id: Long) = dao.deleteCategoryRule(id)

    /** Changing a category; with [always], it becomes a rule and re-sorts matching transactions. */
    suspend fun setCategory(tx: TransactionEntity, category: String, always: Boolean) {
        dao.updateTransaction(tx.copy(category = category, categoryConfirmed = true))
        if (!always) return
        val (field, pattern) = when {
            !tx.merchant.isNullOrBlank() -> "MERCHANT" to tx.merchant
            !tx.counterparty.isNullOrBlank() -> "COUNTERPARTY" to tx.counterparty
            else -> return
        }
        dao.insertCategoryRule(CategoryRuleEntity(field = field, pattern = pattern, category = category))
        val same = dao.getAllTransactions().filter { t ->
            !t.categoryConfirmed && (if (field == "MERCHANT") t.merchant else t.counterparty)?.equals(pattern, true) == true
        }
        if (same.isNotEmpty()) dao.updateTransactions(same.map { it.copy(category = category) })
    }

    suspend fun updateTransaction(tx: TransactionEntity) = dao.updateTransaction(tx)
    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)
    suspend fun setBudget(key: String, amount: Double?) = dao.setBudget(key, amount?.takeIf { it > 0 })

    // ---------------------------------------------------------------- review inbox

    suspend fun resolve(item: ReviewItemEntity, accept: Boolean) {
        if (accept) {
            when (item.kind) {
                "SELF_TRANSFER" -> setExcluded(listOfNotNull(item.refId, item.refId2), true, "SELF_TRANSFER")
                "REFUND" -> setExcluded(listOfNotNull(item.refId, item.refId2), true, "REFUND")
                "BILL_PAYMENT_MATCH" -> item.refId2?.let { bills.markPaid(it, via = "SMS", transactionId = item.refId) }
            }
        }
        reviewDao.resolve(item.id)
    }

    suspend fun reviewContext(item: ReviewItemEntity): Pair<TransactionEntity?, String?> {
        val tx = item.refId?.let { dao.getTransaction(it) }
        val billName = if (item.kind == "BILL_PAYMENT_MATCH") item.refId2?.let { billDao.get(it) }?.let { it.provider ?: it.billType } else null
        return tx to billName
    }

    // ---------------------------------------------------------------- imports

    suspend fun importCsv(text: String): Int {
        var n = 0
        for (row in CsvStatement.parse(text)) {
            val at = row.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
            val tx = Tx(0, at, row.amount, row.debit, row.description.take(40).ifBlank { null }, null, "other", null, "other", false)
            val key = Dedupe.key(row.amount, row.debit, at, row.description.take(30))
            if (dao.findByDedupeKey(key) != null) continue
            saveTransaction(tx, origin = "STATEMENT", source = Provenance.IMPORTED.name, dedupeKey = key)
            n++
        }
        return n
    }

    suspend fun purgeRawMessages() {
        dao.clearRawRefs()
        dao.purgeRaw()
    }

    suspend fun allTx(): List<Tx> = dao.getAllTransactions().map { it.toTx() }
}
