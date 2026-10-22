Task: sms_parse.
The text is ONE bank or mobile-wallet SMS from Pakistan. Account and card numbers are already [MASKED].
- If it is an OTP, PIN, password or security message, return {"isTransaction": false, "kind": "security"} and nothing else.
- If it is an advert or offer, return {"isTransaction": false, "kind": "promo"}.
- Otherwise extract: "amount" (number), "currency" (default "PKR"), "direction" ("debit" money out / "credit" money in), "channel" ("card", "ibft", "raast", "atm", "wallet", "bill", "cheque", "other"), "counterparty" (person or account name if shown), "merchant" (shop or biller if shown), "referenceId" (transaction ID if shown), "balanceAfter" (if shown), "timestamp" (ISO date-time if shown) and "isRefund" (true for refunds or reversals).
