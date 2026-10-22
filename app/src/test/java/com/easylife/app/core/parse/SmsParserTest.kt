package com.easylife.app.core.parse

import com.easylife.app.core.parse.SmsParser.Kind
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 33 FICTIONAL bank/wallet SMS (formats are best guesses until real samples are added in test-data/).
 * Every name, account and reference is made up.
 */
class SmsParserTest {

    private data class Case(
        val name: String,
        val sender: String,
        val body: String,
        val kind: Kind,
        val amount: Double? = null,
        val direction: String? = null,
        val channel: String? = null,
        val counterparty: String? = null,
        val merchant: String? = null,
        val currency: String? = null,
        val refund: Boolean? = null,
        val balance: Double? = null,
        val timestamp: String? = null,
    )

    private val cases = listOf(
        Case("hbl card purchase", "HBL", "Dear Customer, PKR 2,450.00 has been debited from your A/C ****1234 for purchase at SAMPLE MART LAHORE on 21-10-2026 14:32. Avl Bal PKR 45,210.55",
            Kind.TRANSACTION, 2450.0, "debit", "card", merchant = "SAMPLE MART LAHORE", balance = 45210.55, timestamp = "2026-10-21T14:32"),
        Case("ubl atm", "UBL", "UBL: Cash withdrawal of Rs.10,000 from ATM on 20-Oct-26 at 18:05 from A/C XX5678. Avail Bal Rs.23,400",
            Kind.TRANSACTION, 10000.0, "debit", "atm", timestamp = "2026-10-20T18:05"),
        Case("mcb ibft out", "MCB", "MCB: Funds Transfer of PKR 15,000.00 to ALI RAZA (Meezan Bank) from your A/C *9012 on 19/10/2026 10:15 AM. Ref# 4839201746",
            Kind.TRANSACTION, 15000.0, "debit", "ibft", counterparty = "ALI RAZA", timestamp = "2026-10-19T10:15"),
        Case("meezan raast in", "MEEZANBANK", "Meezan Bank: You have received PKR 3,500.00 via Raast from SARA KHAN in your account ending 3344 on 18-10-2026. TID: RST882716",
            Kind.TRANSACTION, 3500.0, "credit", "raast", counterparty = "SARA KHAN"),
        Case("jazzcash sent", "JazzCash", "You have sent Rs. 1,000.00 to 0300XXXXXXX AHMED. TID: 1234567890. Fee: Rs. 0.00. Balance: Rs. 2,345.67",
            Kind.TRANSACTION, 1000.0, "debit", "wallet", counterparty = "0300XXXXXXX AHMED", balance = 2345.67),
        Case("easypaisa received", "easypaisa", "Rs 750.00 received from 0345XXXXXXX BILAL in your Easypaisa account. Trx ID 99887766.",
            Kind.TRANSACTION, 750.0, "credit", "wallet", counterparty = "0345XXXXXXX BILAL"),
        Case("lesco bill via bank", "HBL", "Dear Customer, your LESCO bill of PKR 6,407.00 for Ref No. 99XXXXXXXX has been paid from A/C ****1234 on 20-10-2026. TID 5566778899",
            Kind.TRANSACTION, 6407.0, "debit", "bill", merchant = "LESCO"),
        Case("refund", "HBL", "HBL: PKR 1,299.00 refund credited to your card ending 4321 from DARAZ on 17-10-2026.",
            Kind.TRANSACTION, 1299.0, "credit", "card", counterparty = "DARAZ", refund = true),
        Case("reversal", "UBL", "UBL: Transaction of Rs.2,000 reversed to your A/C XX5678 on 16-10-2026.",
            Kind.TRANSACTION, 2000.0, "credit", refund = true),
        Case("foreign currency", "ALFALAH", "Alfalah: USD 12.99 charged on your card **7788 at NETFLIX.COM on 15-10-2026. Avl limit PKR 80,000",
            Kind.TRANSACTION, 12.99, "debit", "card", merchant = "NETFLIX", currency = "USD"),
        Case("salary", "MCB", "MCB: Your A/C *9012 has been credited with PKR 85,000.00 SALARY from SAMPLE COMPANY PVT LTD on 01-10-2026.",
            Kind.TRANSACTION, 85000.0, "credit", counterparty = "SAMPLE COMPANY PVT LTD"),
        Case("cheque deposit", "ABL", "Allied Bank: Cheque of Rs. 25,000 deposited in A/C ...3322 on 12-10-2026 for clearing.",
            Kind.TRANSACTION, 25000.0, "credit", "cheque"),
        Case("bop pos", "BOP", "BOP: Rs 3,150 spent on Debit Card ending 1122 at ALFATAH MODEL TOWN on 22-10-26 at 7:45 PM",
            Kind.TRANSACTION, 3150.0, "debit", "card", merchant = "ALFATAH MODEL TOWN", timestamp = "2026-10-22T19:45"),
        Case("askari online", "ASKARI", "Askari Bank: PKR 4,999 debited for online purchase at FOODPANDA on 21/10/2026",
            Kind.TRANSACTION, 4999.0, "debit", "card", merchant = "FOODPANDA"),
        Case("nayapay raast in", "NayaPay", "You received Rs. 2,000 from Usman via Raast. Tx ID: NP12345678",
            Kind.TRANSACTION, 2000.0, "credit", "raast", counterparty = "Usman"),
        Case("sadapay card", "SadaPay", "You paid Rs 450 to CAFE SAMPLE using your SadaPay card.",
            Kind.TRANSACTION, 450.0, "debit", "card", counterparty = "CAFE SAMPLE"),
        Case("js ibft in", "JSBANK", "JS Bank: IBFT of PKR 12,000 received from HAMZA ALI A/C ***4455 on 20-10-2026",
            Kind.TRANSACTION, 12000.0, "credit", "ibft", counterparty = "HAMZA ALI"),
        Case("credit card used", "SCB", "SC: Your credit card ending 9988 was used for PKR 7,500.00 at SAMPLE PHARMACY on 19-10-2026.",
            Kind.TRANSACTION, 7500.0, "debit", "card", merchant = "SAMPLE PHARMACY"),
        Case("faysal atm", "FAYSALBANK", "Faysal Bank: ATM Cash Withdrawal PKR 5,000 on 18-10-2026 13:00 A/C xx7766",
            Kind.TRANSACTION, 5000.0, "debit", "atm"),
        Case("jazzcash bill", "JazzCash", "Your PTCL bill of Rs. 3,499 has been paid successfully. TID: 7766554433",
            Kind.TRANSACTION, 3499.0, "debit", "bill", merchant = "PTCL"),
        Case("self transfer out", "HBL", "HBL: PKR 20,000.00 transferred to your own A/C ****8899 (Meezan) on 22-10-2026 09:00",
            Kind.TRANSACTION, 20000.0, "debit", "ibft"),
        Case("self transfer in", "MEEZANBANK", "Meezan Bank: PKR 20,000.00 received from HBL A/C ****1234 on 22-10-2026 09:02",
            Kind.TRANSACTION, 20000.0, "credit", counterparty = "HBL"),
        Case("cashback is a credit not a promo", "JazzCash", "JazzCash: Cashback of Rs. 50 has been credited to your account. Enjoy!",
            Kind.TRANSACTION, 50.0, "credit"),
        Case("promo without amount", "HBL", "HBL: Get 20% OFF at partner restaurants with your HBL card! Offer valid till 31 Oct. T&Cs apply.", Kind.PROMO),
        Case("loan promo with amount", "UBL", "Apply now for a personal loan up to PKR 500,000! Visit www.sample.pk", Kind.PROMO),
        Case("otp english", "HBL", "123456 is your OTP for HBL Mobile. Do not share it with anyone.", Kind.SECURITY),
        Case("otp urdu", "8079", "آپ کا او ٹی پی 4567 ہے۔ کسی کو مت بتائیں", Kind.SECURITY),
        Case("login alert", "UBL", "UBL Digital: New device login attempt on your account at 10:30. If not you, call 111-825-888", Kind.SECURITY),
        Case("pin changed", "MCB", "Your ATM PIN has been changed successfully.", Kind.SECURITY),
        Case("verification code", "NayaPay", "Your verification code is 889911", Kind.SECURITY),
        Case("otp with amount is still discarded", "DARAZ", "Use OTP 4321 to confirm payment of PKR 2,500 at DARAZ", Kind.SECURITY),
        Case("service message", "HBL", "Dear customer, your cheque book request has been received.", Kind.UNKNOWN),
        Case("balance inquiry", "HBL", "Your A/C ****1234 balance is PKR 45,000.00 as of 22-10-2026", Kind.UNKNOWN),
    )

    @Test
    fun atLeastThirtyFixtures() {
        assertTrue(cases.size >= 30)
        assertTrue(cases.count { it.kind == Kind.SECURITY } >= 5)
    }

    @Test
    fun everyFixture() {
        val received = LocalDateTime.of(2026, 10, 22, 12, 0)
        val failures = mutableListOf<String>()
        for (c in cases) {
            val o = SmsParser.parse(c.sender, c.body, received)
            fun check(field: String, expected: Any?, actual: Any?) {
                if (expected != null && expected != actual) failures += "${c.name}: $field expected <$expected> but was <$actual>"
            }
            check("kind", c.kind, o.kind)
            val r = o.result
            if (c.kind == Kind.TRANSACTION) {
                if (r == null) {
                    failures += "${c.name}: no result"
                    continue
                }
                check("amount", c.amount, r.amount)
                check("direction", c.direction, r.direction)
                check("channel", c.channel, r.channel)
                check("counterparty", c.counterparty, r.counterparty)
                check("merchant", c.merchant, r.merchant)
                check("currency", c.currency ?: "PKR", r.currency)
                check("refund", c.refund, r.isRefund)
                check("balance", c.balance, r.balanceAfter)
                check("timestamp", c.timestamp, r.timestamp)
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun otpIsDetectedBeforeAnythingElse() {
        assertTrue(SmsParser.isSecurityMessage("Your One-Time Password is 1234"))
        assertTrue(SmsParser.isSecurityMessage("Never share your PIN"))
        assertTrue(!SmsParser.isSecurityMessage("PKR 500 debited from your account"))
    }

    @Test
    fun noDateUsesReceivedTime() {
        val at = LocalDateTime.of(2026, 10, 22, 8, 30)
        val r = SmsParser.parse("UBL", "Rs 500 debited from A/C XX1111", at).result!!
        assertEquals("2026-10-22T08:30", r.timestamp)
        assertNull(r.counterparty)
    }
}
