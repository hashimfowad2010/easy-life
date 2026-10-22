package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.SmsParseResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Bank / wallet SMS -> transaction, fully on the phone.
 *
 * Pipeline: OTP/security filter (discard, never stored) -> transaction templates -> promo filter -> unknown (Review inbox).
 * A promo check runs only when no transaction was found, so "cashback of PKR 50 credited" is still counted.
 * Formats are best guesses from typical Pakistani messages until real samples are added (see test-data.README.md).
 */
object SmsParser {

    enum class Kind { SECURITY, TRANSACTION, PROMO, UNKNOWN }

    data class Outcome(val kind: Kind, val result: SmsParseResult?, val templateId: String? = null)

    // ---------------------------------------------------------------- 1. security / OTP

    private val securityPatterns = listOf(
        Regex("""\b(?:otp|o\.t\.p|tpin|mpin|cvv|cvc)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpin\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:password|passcode|pass\s*code|passkey)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bone[\s-]?time\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:verification|verify|authentication|activation|security|confirmation|login|sign[\s-]?in)\s+(?:code|attempt|request|key)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:do\s*not|don'?t|never)\s+share\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcode\s+(?:is|:)\s*\d{4,8}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{4,8}\s+is\s+your\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:device|new device)\s+(?:registration|linked|login)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:او\s*ٹی\s*پی|پاس\s*ورڈ|خفیہ\s*کوڈ|کوڈ\s*کسی)"""),
    )

    /** True for OTPs, PINs, passwords and login alerts. Such messages are dropped before anything else. */
    fun isSecurityMessage(body: String): Boolean = securityPatterns.any { it.containsMatchIn(body) }

    // ---------------------------------------------------------------- 2. promo

    private val promoPatterns = listOf(
        Regex("""\b(?:offer|discount|win|winner|lucky\s+draw|apply\s+now|avail|limited\s+time|sale|promo|congratulations|pre-?approved|upgrade|download|install)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*%\s*off""", RegexOption.IGNORE_CASE),
        Regex("""(?:https?://|www\.)""", RegexOption.IGNORE_CASE),
        Regex("""\bdial\s*\*""", RegexOption.IGNORE_CASE),
    )

    fun isPromo(body: String): Boolean = promoPatterns.count { it.containsMatchIn(body) } >= 1

    // ---------------------------------------------------------------- 3. transactions

    private const val AMOUNT = """([\d,]+(?:\.\d{1,2})?)"""

    private val amountPatterns = listOf(
        Regex("""\b(PKR|Rs\.?|RS)\s?$AMOUNT""", RegexOption.IGNORE_CASE),
        Regex("""\b(USD|EUR|GBP|AED|SAR|US\$)\s?$AMOUNT""", RegexOption.IGNORE_CASE),
        Regex("""$AMOUNT\s?(PKR|Rs)\b""", RegexOption.IGNORE_CASE),
    )

    private val debitWords = Regex(
        """\b(?:debited|debit|deducted|withdrawn|withdrawal|spent|paid|payment\s+of|purchase|purchased|sent|charged|used\s+for|bill\s+paid)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditWords = Regex(
        """\b(?:credited|credit(?!\s*card)|received|deposited|deposit|refund(?:ed)?|reversal|reversed|cashback|salary|added\s+to\s+your)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Transfer wording without a clear in/out verb: money out unless the message says "from ... to your". */
    private val weakDebitWords = Regex("""\b(?:transferred\s+to|transfer\s+to|funds?\s+transfer(?:red)?|transfer\s+of)\b""", RegexOption.IGNORE_CASE)
    private val weakCreditWords = Regex("""\btransferred\s+from\b""", RegexOption.IGNORE_CASE)
    private val refundWords = Regex("""\b(?:refund(?:ed)?|reversal|reversed|chargeback)\b""", RegexOption.IGNORE_CASE)

    /** Amounts that are balances or limits, not the transaction itself. */
    private val balanceLike = Regex(
        """\b(?:bal(?:ance)?|limit|avl|avail(?:able)?)\b\W{0,3}(?:is\s*|:\s*)?(?:PKR|Rs\.?)\s?[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE,
    )

    private val namePart = """([A-Za-z0-9][A-Za-z0-9 .&'*/-]{1,40}?)"""
    private val stopAfterName =
        """(?=\s+(?:on|at|via|ref|tid|trx|from|to|for|in|using|with|dated|avl|avail|bal|is|has|was|your|a/c|acct|account|successfully)\b|\s*\(|[.,;!]|\s*$)"""
    private val toPattern = Regex("""\b(?:sent\s+to|transferred\s+to|transfer\s+to|paid\s+to|to)\s+$namePart$stopAfterName""", RegexOption.IGNORE_CASE)
    private val fromPattern = Regex("""\b(?:received\s+from|transferred\s+from|from)\s+$namePart$stopAfterName""", RegexOption.IGNORE_CASE)
    private val atPattern = Regex("""\b(?:at|purchase\s+at|payment\s+at|POS\s+at)\s+$namePart$stopAfterName""", RegexOption.IGNORE_CASE)
    private val refPattern = Regex("""\b(?:ref(?:erence)?|tid|trx\s*id|txn\s*id|tx\s*id|tran(?:saction)?\s*id|rrn|stan)[\s.:#-]*(?:no\.?)?[\s.:#-]*([A-Za-z0-9]{5,24})\b""", RegexOption.IGNORE_CASE)
    private val balancePattern = Regex(
        """\b(?:avl\.?|avail(?:able)?|current|new)?\s*(?:bal(?:ance)?)\.?\s*(?:is|:)?\s*(?:PKR|Rs\.?)\s?$AMOUNT""",
        RegexOption.IGNORE_CASE,
    )
    private val timePattern = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?\b""", RegexOption.IGNORE_CASE)

    private val billers = listOf("LESCO", "IESCO", "FESCO", "GEPCO", "MEPCO", "PESCO", "HESCO", "SEPCO", "QESCO", "K-ELECTRIC", "SNGPL", "SSGC", "PTCL", "WASA", "NAYATEL", "STORMFIBER")

    private val walletSenders = listOf("JAZZCASH", "EASYPAISA", "NAYAPAY", "SADAPAY", "UPAISA", "ZINDIGI")

    /**
     * @param sender SMS sender (alphanumeric id or short code)
     * @param body message text
     * @param receivedAt when the phone received it (used if the SMS has no date)
     */
    fun parse(sender: String, body: String, receivedAt: LocalDateTime? = null): Outcome {
        if (isSecurityMessage(body)) return Outcome(Kind.SECURITY, SmsParseResult(isTransaction = false, kind = "security", confidence = 1.0))

        val amount = findAmount(body)
        val debit = debitWords.containsMatchIn(body) || weakDebitWords.containsMatchIn(body)
        val credit = creditWords.containsMatchIn(body) || weakCreditWords.containsMatchIn(body)
        if (amount != null && (debit || credit)) {
            val tx = transaction(sender, body, amount, debit, credit, receivedAt)
            return Outcome(Kind.TRANSACTION, tx, tx.templateId)
        }
        if (isPromo(body)) return Outcome(Kind.PROMO, SmsParseResult(isTransaction = false, kind = "promo", confidence = 0.9))
        return Outcome(Kind.UNKNOWN, null)
    }

    private data class Money(val currency: String, val amount: Double, val range: IntRange)

    /** The first amount in the message that is not a balance or credit limit. */
    private fun findAmount(body: String): Money? {
        val skip = balanceLike.findAll(body).map { it.range }.toList()
        val candidates = amountPatterns.flatMapIndexed { index, p ->
            p.findAll(body).mapNotNull { m ->
                if (skip.any { it.first <= m.range.first && m.range.last <= it.last }) return@mapNotNull null
                val (cur, num) = if (index == 2) m.groupValues[2] to m.groupValues[1] else m.groupValues[1] to m.groupValues[2]
                val value = num.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val currency = when (cur.uppercase().trimEnd('.')) {
                    "PKR", "RS" -> "PKR"
                    "US$" -> "USD"
                    else -> cur.uppercase()
                }
                Money(currency, value, m.range)
            }.toList()
        }
        return candidates.minByOrNull { it.range.first }
    }

    private fun transaction(
        sender: String,
        body: String,
        money: Money,
        debitWord: Boolean,
        creditWord: Boolean,
        receivedAt: LocalDateTime?,
    ): SmsParseResult {
        val upper = body.uppercase()
        val senderUpper = sender.uppercase().replace(" ", "")
        val isRefund = refundWords.containsMatchIn(body)
        val uncertain = mutableListOf<String>()

        // Direction: refunds are money in. Clear verbs ("debited", "received") beat transfer wording.
        // If clear verbs of both kinds appear, the one nearest the amount wins and the user is asked to check.
        val strongDebit = debitWords.containsMatchIn(body)
        val strongCredit = creditWords.containsMatchIn(body)
        val direction = when {
            isRefund -> "credit"
            strongDebit && !strongCredit -> "debit"
            strongCredit && !strongDebit -> "credit"
            strongDebit && strongCredit -> {
                val d = debitWords.findAll(body).minOf { kotlin.math.abs(it.range.first - money.range.first) }
                val c = creditWords.findAll(body).minOf { kotlin.math.abs(it.range.first - money.range.first) }
                uncertain += "direction"
                if (d <= c) "debit" else "credit"
            }
            weakCreditWords.containsMatchIn(body) -> "credit"
            debitWord -> "debit"
            else -> if (creditWord) "credit" else "debit"
        }

        val isWallet = walletSenders.any { senderUpper.contains(it) } || Regex("""\b(?:wallet|mobile\s+account)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val biller = billers.firstOrNull { upper.contains(it) }
        val channel = when {
            Regex("""\bATM\b|cash\s+withdrawal""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "atm"
            Regex("""\braast\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "raast"
            Regex("""\bIBFT\b|inter\s*bank|funds?\s+transfer""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "ibft"
            biller != null || Regex("""\bbill\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "bill"
            Regex("""\bcheque\b|\bclearing\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "cheque"
            Regex("""\b(?:card|POS|purchase|e-?com|online\s+shopping)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "card"
            isWallet -> "wallet"
            Regex("""\btransfer""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "ibft"
            else -> "other"
        }

        val merchant = biller ?: atPattern.find(body)?.groupValues?.get(1)?.let(::cleanName)?.takeIf { channel == "card" || channel == "bill" || channel == "other" }
        val counterparty = when (direction) {
            "debit" -> toPattern.find(body)?.groupValues?.get(1)?.let(::cleanName)
            else -> fromPattern.find(body)?.groupValues?.get(1)?.let(::cleanName)
        }?.takeIf { it.isNotBlank() && !looksLikeAccountWord(it) }

        val reference = refPattern.findAll(body).map { it.groupValues[1] }.firstOrNull { r -> r.any(Char::isDigit) }
        val balance = balancePattern.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val timestamp = timestampIn(body, receivedAt)
        if (timestamp == null) uncertain += "timestamp"

        val templateId = buildString {
            append(if (isWallet) "wallet" else "bank")
            append('_')
            append(channel)
            append('_')
            append(direction)
        }
        var confidence = 0.7
        if (counterparty != null || merchant != null) confidence += 0.1
        if (reference != null) confidence += 0.05
        if (balance != null) confidence += 0.05
        if ("direction" in uncertain) confidence -= 0.25
        if (money.currency != "PKR") {
            uncertain += "currency"
            confidence -= 0.1
        }

        return SmsParseResult(
            isTransaction = true,
            kind = "transaction",
            amount = money.amount,
            currency = money.currency,
            direction = direction,
            channel = channel,
            counterparty = counterparty,
            merchant = merchant,
            referenceId = reference,
            balanceAfter = balance,
            timestamp = timestamp?.toString(),
            isRefund = isRefund,
            confidence = confidence.coerceIn(0.1, 0.95),
            uncertainFields = uncertain,
            templateId = templateId,
        )
    }

    private fun cleanName(raw: String): String = raw.trim().trimEnd('.', ',', '-', '/').replace(Regex("""\s+"""), " ").take(40)

    private fun looksLikeAccountWord(s: String): Boolean =
        Regex("""^(?:your|you|a/?c|acct|account|card|wallet|mobile\s+account|the)\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)

    private fun timestampIn(body: String, receivedAt: LocalDateTime?): LocalDateTime? {
        val date: LocalDate? = Dates.findAll(body).firstOrNull()?.value
        val time = timePattern.find(body)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            when (m.groupValues[3].uppercase()) {
                "PM" -> if (h < 12) h += 12
                "AM" -> if (h == 12) h = 0
            }
            if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
        }
        return when {
            date != null -> date.atTime(time ?: receivedAt?.toLocalTime() ?: LocalTime.NOON)
            receivedAt != null -> receivedAt
            else -> null
        }
    }
}
