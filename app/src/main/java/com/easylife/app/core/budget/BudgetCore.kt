package com.easylife.app.core.budget

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// Pure Budget logic (no Android): bank catalog, sender matching, categories, exclusions, pairing, bill matching.

data class Bank(val id: String, val nameEn: String, val nameUr: String, val senderKeys: List<String>, val isWallet: Boolean = false)

object BankCatalog {
    val all = listOf(
        Bank("HBL", "HBL", "ایچ بی ایل", listOf("HBL")),
        Bank("UBL", "UBL", "یو بی ایل", listOf("UBL")),
        Bank("MCB", "MCB", "ایم سی بی", listOf("MCB")),
        Bank("MEEZAN", "Meezan Bank", "میزان بینک", listOf("MEEZAN")),
        Bank("ALLIED", "Allied Bank", "الائیڈ بینک", listOf("ABL", "ALLIED")),
        Bank("ALFALAH", "Bank Alfalah", "بینک الفلاح", listOf("ALFALAH", "BAFL")),
        Bank("BOP", "Bank of Punjab", "بینک آف پنجاب", listOf("BOP", "BANKOFPUNJAB", "PUNJAB")),
        Bank("FAYSAL", "Faysal Bank", "فیصل بینک", listOf("FAYSAL", "FBL")),
        Bank("ASKARI", "Askari Bank", "عسکری بینک", listOf("ASKARI", "AKBL")),
        Bank("SCB", "Standard Chartered", "اسٹینڈرڈ چارٹرڈ", listOf("SCB", "STANCHART", "STANDARDCHARTERED")),
        Bank("JS", "JS Bank", "جے ایس بینک", listOf("JSBANK", "JSBL")),
        Bank("JAZZCASH", "JazzCash", "جاز کیش", listOf("JAZZCASH"), isWallet = true),
        Bank("EASYPAISA", "Easypaisa", "ایزی پیسہ", listOf("EASYPAISA"), isWallet = true),
        Bank("NAYAPAY", "NayaPay", "نیا پے", listOf("NAYAPAY"), isWallet = true),
        Bank("SADAPAY", "SadaPay", "صدا پے", listOf("SADAPAY"), isWallet = true),
        Bank("OTHER", "Other", "دیگر", emptyList()),
    )

    fun byId(id: String): Bank = all.firstOrNull { it.id == id } ?: all.last()

    /** Which bank an SMS sender id belongs to (alphanumeric ids like "HBL", "MeezanBank"). Numbers return null. */
    fun bankForSender(sender: String): Bank? {
        val s = sender.uppercase().filter { it.isLetterOrDigit() }
        return all.firstOrNull { b -> b.senderKeys.any { s.contains(it) } }
    }
}

/** A finished transaction as the pure logic sees it. */
data class Tx(
    val id: Long,
    val at: Instant,
    val amount: Double,
    val debit: Boolean,
    val counterparty: String?,
    val merchant: String?,
    val channel: String,
    val accountId: Long?,
    val category: String,
    val excluded: Boolean,
    val isRefund: Boolean = false,
    val referenceId: String? = null,
)

enum class RuleField { COUNTERPARTY, MERCHANT, CHANNEL, ACCOUNT }

data class ExclusionRule(val id: Long, val field: RuleField, val value: String, val paused: Boolean = false)

object Exclusions {
    fun matches(rule: ExclusionRule, tx: Tx): Boolean {
        if (rule.paused) return false
        val v = rule.value.trim().lowercase()
        return when (rule.field) {
            RuleField.COUNTERPARTY -> tx.counterparty?.trim()?.lowercase() == v
            RuleField.MERCHANT -> tx.merchant?.trim()?.lowercase() == v
            RuleField.CHANNEL -> tx.channel.lowercase() == v
            RuleField.ACCOUNT -> tx.accountId?.toString() == v
        }
    }

    /** First active rule that excludes [tx], if any. */
    fun ruleFor(rules: List<ExclusionRule>, tx: Tx): ExclusionRule? = rules.firstOrNull { matches(it, tx) }

    /** Totals never include excluded transactions (spec 6.3). */
    fun spent(txs: List<Tx>): Double = txs.filter { it.debit && !it.excluded }.sumOf { it.amount }
    fun received(txs: List<Tx>): Double = txs.filter { !it.debit && !it.excluded }.sumOf { it.amount }
}

data class PairSuggestion(val debitId: Long, val creditId: Long, val kind: Kind) {
    enum class Kind { SELF_TRANSFER, REFUND }
}

object Pairing {
    /**
     * Money moved between the user's own accounts: a debit and a credit of the same amount within [window] minutes,
     * on different accounts (or wallet <-> bank). Suggest only; the user decides.
     */
    fun selfTransfers(txs: List<Tx>, windowMinutes: Long = 10): List<PairSuggestion> {
        val out = mutableListOf<PairSuggestion>()
        val used = mutableSetOf<Long>()
        val debits = txs.filter { it.debit && !it.excluded }.sortedBy { it.at }
        val credits = txs.filter { !it.debit && !it.excluded && !it.isRefund }
        for (d in debits) {
            if (d.id in used) continue
            val c = credits.filter { it.id !in used && abs(it.amount - d.amount) < 0.01 && it.accountId != d.accountId }
                .filter { abs(ChronoUnit.MINUTES.between(d.at, it.at)) <= windowMinutes }
                .minByOrNull { abs(ChronoUnit.SECONDS.between(d.at, it.at)) } ?: continue
            out += PairSuggestion(d.id, c.id, PairSuggestion.Kind.SELF_TRANSFER)
            used += d.id
            used += c.id
        }
        return out
    }

    /** A refund/reversal credit and the earlier purchase of the same amount (within 60 days) cancel out. */
    fun refunds(txs: List<Tx>, windowDays: Long = 60): List<PairSuggestion> {
        val out = mutableListOf<PairSuggestion>()
        val used = mutableSetOf<Long>()
        for (r in txs.filter { !it.debit && it.isRefund && !it.excluded }) {
            val original = txs.filter { it.debit && !it.excluded && it.id !in used && abs(it.amount - r.amount) < 0.01 && !it.at.isAfter(r.at) }
                .filter { ChronoUnit.DAYS.between(it.at, r.at) <= windowDays }
                .filter { o -> r.merchant == null || o.merchant == null || o.merchant.equals(r.merchant, true) || o.counterparty.equals(r.counterparty, true) }
                .maxByOrNull { it.at } ?: continue
            out += PairSuggestion(original.id, r.id, PairSuggestion.Kind.REFUND)
            used += original.id
        }
        return out
    }
}

/** Default categories from merchant/counterparty keywords; user rules always win. */
object Categorizer {
    private val keywordRules: List<Pair<Regex, String>> = listOf(
        Regex("""FOODPANDA|KFC|MCDONALD|PIZZA|CAFE|RESTAURANT|BURGER|BAKERY|DHABA|CHEEZIOUS""") to "food",
        Regex("""LESCO|IESCO|FESCO|GEPCO|MEPCO|PESCO|HESCO|SEPCO|QESCO|K-?ELECTRIC|SNGPL|SSGC|WASA""") to "bills",
        Regex("""PTCL|NAYATEL|STORM|JAZZ\s?(PACKAGE|LOAD)|ZONG|TELENOR|UFONE|MOBILINK|INTERNET|BROADBAND""") to "mobile",
        Regex("""PSO|SHELL|TOTAL\s?PARCO|ATTOCK|HASCOL|FUEL|PETROL|CAREEM|UBER|INDRIVE|BYKEA|YANGO|METRO\s?BUS""") to "transport",
        Regex("""SCHOOL|COLLEGE|UNIVERSITY|ACADEMY|BEACONHOUSE|TUITION|BOOK""") to "education",
        Regex("""PHARMA|PHARMACY|HOSPITAL|CLINIC|MEDICAL|LAB\b|CHUGHTAI|SHAUKAT""") to "health",
        Regex("""AL-?FATAH|NAHEED|IMTIAZ|METRO|CARREFOUR|HYPERSTAR|GROCER|MART|SUPERSTORE|KARYANA|DALDA""") to "groceries",
        Regex("""DARAZ|KHAADI|SAPPHIRE|OUTFITTERS|BATA|SERVIS|GUL\s?AHMED|J\.\s?JUNAID""") to "shopping",
        Regex("""EDHI|SHAUKAT\s?KHANUM|ZAKAT|SADQA|CHARITY|AKHUWAT|MOSQUE|MASJID""") to "charity",
        Regex("""RENT|LANDLORD""") to "rent",
        Regex("""SALARY|PAYROLL""") to "income",
    )

    data class Rule(val field: String, val pattern: String, val category: String)

    fun categorize(tx: Tx, userRules: List<Rule>): String {
        userRules.firstOrNull { r ->
            val target = if (r.field == "MERCHANT") tx.merchant else tx.counterparty
            target != null && target.trim().equals(r.pattern.trim(), ignoreCase = true)
        }?.let { return it.category }
        if (tx.channel == "atm") return "cash"
        val text = listOfNotNull(tx.merchant, tx.counterparty).joinToString(" ").uppercase()
        keywordRules.firstOrNull { it.first.containsMatchIn(text) }?.let { return it.second }
        if (!tx.debit) return if (text.contains("SALARY")) "income" else "income"
        if (tx.channel == "ibft" || tx.channel == "raast" || tx.channel == "wallet") return "transfers"
        if (tx.channel == "bill") return "bills"
        return "other"
    }
}

data class OpenBill(val id: Long, val provider: String?, val billType: String, val issueDate: LocalDate?, val amountWithin: Double?, val amountAfter: Double?)

object BillMatcher {
    private val billerWords = mapOf(
        "electricity" to listOf("LESCO", "IESCO", "FESCO", "GEPCO", "MEPCO", "PESCO", "HESCO", "SEPCO", "QESCO", "K-ELECTRIC", "ELECTRIC"),
        "gas" to listOf("SNGPL", "SSGC", "SUI", "GAS"),
        "water" to listOf("WASA", "WATER"),
        "internet" to listOf("PTCL", "NAYATEL", "STORM", "INTERNET"),
        "mobile_postpaid" to listOf("JAZZ", "ZONG", "TELENOR", "UFONE"),
        "school_fee" to listOf("SCHOOL", "FEE", "TUITION", "ACADEMY", "COLLEGE"),
    )

    /**
     * A debit that pays an open bill: amount within ±1% of the within-due or after-due amount, on/after the issue date,
     * and the biller named in the message (or the provider name). Returns the bill id.
     */
    fun match(tx: Tx, bills: List<OpenBill>, today: LocalDate): Long? {
        if (!tx.debit || tx.excluded) return null
        val text = listOfNotNull(tx.merchant, tx.counterparty).joinToString(" ").uppercase()
        val txDate = tx.at.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return bills.firstOrNull { b ->
            val amountOk = listOfNotNull(b.amountWithin, b.amountAfter).any { a -> a > 0 && abs(tx.amount - a) <= a * 0.01 }
            val dateOk = b.issueDate == null || !txDate.isBefore(b.issueDate)
            val words = (billerWords[b.billType].orEmpty() + listOfNotNull(b.provider?.uppercase()))
            val billerOk = tx.channel == "bill" || words.any { w -> text.contains(w) }
            amountOk && dateOk && billerOk && !txDate.isAfter(today)
        }?.id
    }
}

object Dedupe {
    /**
     * Same transaction seen twice (SMS + app notification, or a re-import): same amount and direction, same reference
     * if both have one, otherwise within 5 minutes.
     */
    fun key(amount: Double, debit: Boolean, at: Instant, reference: String?): String =
        if (!reference.isNullOrBlank()) "ref:${reference.uppercase()}:${"%.2f".format(java.util.Locale.US, amount)}"
        else "t:${if (debit) "D" else "C"}:${"%.2f".format(java.util.Locale.US, amount)}:${at.epochSecond / 300}"

    fun isDuplicate(a: Tx, b: Tx): Boolean {
        if (abs(a.amount - b.amount) >= 0.01 || a.debit != b.debit) return false
        if (!a.referenceId.isNullOrBlank() && !b.referenceId.isNullOrBlank()) return a.referenceId.equals(b.referenceId, true)
        return abs(ChronoUnit.SECONDS.between(a.at, b.at)) <= 300
    }
}

/** Bank statement CSV (exported from internet banking) -> rows. Column names vary, so they are detected. */
object CsvStatement {
    data class Row(val date: LocalDate, val description: String, val amount: Double, val debit: Boolean)

    fun parse(csv: String): List<Row> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val headerIdx = lines.indexOfFirst { l -> val u = l.uppercase(); u.contains("DATE") && (u.contains("DEBIT") || u.contains("AMOUNT") || u.contains("WITHDRAW")) }
        if (headerIdx < 0) return emptyList()
        val header = split(lines[headerIdx]).map { it.uppercase().trim() }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h.contains(it) } }
        val dateCol = col("DATE")
        val descCol = col("DESCRIPTION", "NARRATION", "DETAIL", "PARTICULAR", "REMARK")
        val debitCol = col("DEBIT", "WITHDRAW")
        val creditCol = col("CREDIT", "DEPOSIT")
        val amountCol = col("AMOUNT")
        val out = mutableListOf<Row>()
        for (line in lines.drop(headerIdx + 1)) {
            val cells = split(line)
            val date = cells.getOrNull(dateCol)?.let { com.easylife.app.core.parse.Dates.parse(it) } ?: continue
            val desc = cells.getOrNull(descCol)?.trim().orEmpty()
            fun num(i: Int) = cells.getOrNull(i)?.replace(",", "")?.replace("PKR", "", true)?.trim()?.toDoubleOrNull()
            val debit = if (debitCol >= 0) num(debitCol) else null
            val credit = if (creditCol >= 0) num(creditCol) else null
            when {
                debit != null && debit > 0 -> out += Row(date, desc, debit, true)
                credit != null && credit > 0 -> out += Row(date, desc, credit, false)
                amountCol >= 0 -> num(amountCol)?.takeIf { it != 0.0 }?.let { out += Row(date, desc, abs(it), it < 0) }
            }
        }
        return out
    }

    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out += sb.toString()
        return out
    }
}
