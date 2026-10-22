package com.easylife.app.core.i18n

object SBudget {
    val title = Txt("Budget", "بجٹ")
    val thisMonth = Txt("This month", "اس مہینے")
    val spent = Txt("Spent", "خرچ")
    val received = Txt("Received", "آمدنی")
    val vsLastMonth = Txt("%1\$s vs last month", "پچھلے مہینے کے مقابلے %1\$s")
    val setupTitle = Txt("Track spending automatically", "خرچ خودکار طریقے سے لکھیں")
    val setupBody = Txt(
        "Easy Life reads your bank and wallet SMS alerts on this phone. No bank passwords, no online banking login. OTP messages are thrown away immediately.",
        "آسان زندگی اس فون پر بینک اور والٹ کے SMS پیغامات پڑھتی ہے۔ نہ بینک پاس ورڈ، نہ آن لائن بینکنگ لاگ ان۔ OTP پیغامات فوراً ضائع کر دیے جاتے ہیں۔",
    )
    val addBank = Txt("Add my bank or wallet", "اپنا بینک یا والٹ شامل کریں")
    val addManually = Txt("Add spending by hand", "خرچ خود لکھیں")
    val scanReceipt = Txt("Scan a receipt", "رسید اسکین کریں")
    val importCsv = Txt("Import bank statement (CSV)", "بینک اسٹیٹمنٹ (CSV) درآمد کریں")
    val imported = Txt("%1\$s transactions imported", "%1\$s لین دین درآمد ہو گئے")
    val transactions = Txt("Transactions", "لین دین")
    val all = Txt("All", "سب")
    val excludedFilter = Txt("Excluded", "شامل نہیں")
    val exclude = Txt("Exclude", "شامل نہ کریں")
    val include = Txt("Include", "شامل کریں")
    val excludedNote = Txt("Not counted in totals or budgets", "کل اور بجٹ میں شمار نہیں")
    val excludedSnack = Txt("Excluded from totals", "کل سے نکال دیا گیا")
    val includedSnack = Txt("Counted again", "دوبارہ شامل کر دیا گیا")
    val excludeSimilar = Txt("Exclude similar…", "اس جیسے سب نکالیں…")
    val similarTitle = Txt("Exclude all transactions with the same…", "ایک جیسے تمام لین دین نکالیں…")
    val byCounterparty = Txt("Person / account: %1\$s", "شخص / اکاؤنٹ: %1\$s")
    val byMerchant = Txt("Shop: %1\$s", "دکان: %1\$s")
    val byChannel = Txt("Type: %1\$s", "قسم: %1\$s")
    val byAccount = Txt("My account: %1\$s", "میرا اکاؤنٹ: %1\$s")
    val applyPast = Txt("Apply to past transactions too?", "پچھلے لین دین پر بھی لاگو کریں؟")
    val applyPastYes = Txt("Yes, past and future", "ہاں، پچھلے اور آئندہ")
    val applyFutureOnly = Txt("Only new ones", "صرف نئے")
    val rules = Txt("Rules", "اصول")
    val rulesIntro = Txt("Exclusion rules hide matching transactions from totals. Category rules sort them automatically.", "اخراج کے اصول ملتے جلتے لین دین کو کل سے نکالتے ہیں۔ زمرے کے اصول خود ترتیب دیتے ہیں۔")
    val pause = Txt("Pause", "روکیں")
    val resume = Txt("Resume", "دوبارہ چلائیں")
    val noRules = Txt("No rules yet", "ابھی کوئی اصول نہیں")
    val selected = Txt("%1\$s selected", "%1\$s منتخب")
    val excludeSelected = Txt("Exclude selected", "منتخب نکالیں")
    val includeSelected = Txt("Include selected", "منتخب شامل کریں")
    val category = Txt("Category", "زمرہ")
    val alwaysCategory = Txt("Always put %1\$s in %2\$s?", "%1\$s کو ہمیشہ %2\$s میں رکھیں؟")
    val always = Txt("Yes, always", "ہاں، ہمیشہ")
    val justThisOne = Txt("Just this one", "صرف یہ")
    val budgets = Txt("Monthly budgets", "ماہانہ بجٹ")
    val setBudget = Txt("Set budget", "بجٹ مقرر کریں")
    val budgetFor = Txt("Monthly budget for %1\$s (Rs)", "%1\$s کا ماہانہ بجٹ (Rs)")
    val budgetLine = Txt("%1\$s of %2\$s", "%2\$s میں سے %1\$s")
    val budgetHeadline = Txt(
        "At this pace %1\$s runs out on %2\$s. Keep to %3\$s a day.",
        "اسی رفتار سے %1\$s کا بجٹ %2\$s کو ختم ہو جائے گا۔ روزانہ %3\$s تک رکھیں۔",
    )
    val budgetOk = Txt("%1\$s left · %2\$s a day for %3\$s days", "%1\$s باقی · %3\$s دن کے لیے روزانہ %2\$s")
    val budgetOver = Txt("Over budget by %1\$s", "بجٹ سے %1\$s زیادہ")
    val noBudgets = Txt("Set a monthly budget for a category to get early warnings.", "کسی زمرے کا ماہانہ بجٹ مقرر کریں تاکہ پہلے خبردار ہو سکیں۔")
    val topMerchants = Txt("Where it went", "پیسے کہاں گئے")
    val byCategory = Txt("By category", "زمرہ وار")
    val review = Txt("To check", "جانچ کے لیے")
    val reviewInbox = Txt("Review inbox", "جانچ کی فہرست")
    val reviewEmpty = Txt("Nothing to check.", "جانچنے کو کچھ نہیں۔")
    val reviewUnknownSms = Txt("A bank message the phone couldn't read", "بینک پیغام جو فون نہیں پڑھ سکا")
    val reviewLowConfidence = Txt("Please check this transaction", "یہ لین دین چیک کریں")
    val reviewSelfTransfer = Txt("Money moved between your own accounts?", "کیا یہ اپنے ہی اکاؤنٹس کے درمیان رقم تھی؟")
    val reviewSelfTransferBody = Txt("%1\$s out and back in within minutes. Exclude both from spending?", "%1\$s چند منٹ میں نکلے اور واپس آئے۔ دونوں کو خرچ سے نکال دیں؟")
    val reviewRefund = Txt("Refund found", "رقم واپسی ملی")
    val reviewRefundBody = Txt("%1\$s came back. Exclude the purchase and the refund?", "%1\$s واپس آئے۔ خریداری اور واپسی دونوں نکال دیں؟")
    val reviewBillPaid = Txt("Bill payment found", "بل کی ادائیگی ملی")
    val reviewBillPaidBody = Txt("Paid %1\$s on %2\$s. Mark the %3\$s bill as paid?", "%2\$s کو %1\$s ادا کیے۔ %3\$s کا بل ادا شدہ کر دیں؟")
    val yesExcludeBoth = Txt("Yes, exclude both", "ہاں، دونوں نکالیں")
    val yesMarkPaid = Txt("Yes, mark paid", "ہاں، ادا شدہ")
    val dismiss = Txt("No", "نہیں")
    val addAsTransaction = Txt("Add as a transaction", "لین دین کے طور پر شامل کریں")
    val banksTitle = Txt("Banks and wallets", "بینک اور والٹ")
    val pickBank = Txt("Choose your bank or wallet", "اپنا بینک یا والٹ چنیں")
    val nickname = Txt("Nickname (e.g. Salary account)", "نام (مثلاً تنخواہ والا اکاؤنٹ)")
    val last4 = Txt("Last 4 digits (optional)", "آخری 4 ہندسے (اختیاری)")
    val last4Note = Txt("Never your full account number, PIN or password.", "پورا اکاؤنٹ نمبر، پن یا پاس ورڈ کبھی نہیں۔")
    val smsPermissionTitle = Txt("Read bank SMS on this phone", "اس فون پر بینک SMS پڑھیں")
    val smsPermissionBody = Txt(
        "Easy Life needs permission to read SMS. It only reads messages from the banks and wallets you choose. OTPs are discarded at once. Nothing leaves your phone.",
        "آسان زندگی کو SMS پڑھنے کی اجازت چاہیے۔ صرف آپ کے چنے ہوئے بینکوں اور والٹس کے پیغامات پڑھے جائیں گے۔ OTP فوراً ضائع ہوں گے۔ کچھ بھی فون سے باہر نہیں جاتا۔",
    )
    val allowSms = Txt("Allow reading SMS", "SMS پڑھنے کی اجازت دیں")
    val smsDenied = Txt(
        "Without SMS access you can still add spending by hand, scan receipts or import a statement.",
        "SMS کی اجازت کے بغیر بھی آپ خرچ خود لکھ سکتے ہیں، رسید اسکین کر سکتے ہیں یا اسٹیٹمنٹ درآمد کر سکتے ہیں۔",
    )
    val scan90 = Txt("Look at the last 90 days of SMS", "پچھلے 90 دن کے SMS دیکھیں")
    val scanning = Txt("Looking for bank messages on your phone…", "فون پر بینک پیغامات ڈھونڈے جا رہے ہیں…")
    val suggestedSenders = Txt("These senders look like your bank. Tick the ones that are yours:", "یہ بھیجنے والے آپ کے بینک لگتے ہیں۔ جو آپ کے ہیں ان پر نشان لگائیں:")
    val messagesCount = Txt("%1\$s transaction messages", "%1\$s لین دین کے پیغامات")
    val importSelected = Txt("Import from these", "ان سے درآمد کریں")
    val noSenders = Txt("No bank messages found in the last 90 days.", "پچھلے 90 دن میں کوئی بینک پیغام نہیں ملا۔")
    val otpDiscarded = Txt("%1\$s OTP/security messages were ignored and not saved.", "%1\$s OTP/سکیورٹی پیغامات نظر انداز کیے گئے اور محفوظ نہیں ہوئے۔")
    val notificationAccess = Txt("Also read wallet app notifications (optional)", "والٹ ایپ کی اطلاعات بھی پڑھیں (اختیاری)")
    val notificationAccessNote = Txt(
        "For wallets that send app notifications instead of SMS. You can turn it off any time in Android settings.",
        "ان والٹس کے لیے جو SMS کی بجائے ایپ اطلاعات بھیجتے ہیں۔ اسے کبھی بھی اینڈرائیڈ سیٹنگز میں بند کر سکتے ہیں۔",
    )
    val keepRaw = Txt("Keep bank message text (encrypted, on this phone)", "بینک پیغام کا متن رکھیں (خفیہ، اسی فون پر)")
    val keepRawNote = Txt("Useful for checking a transaction later. Turn off to keep only the amounts.", "بعد میں لین دین جانچنے کے لیے مفید۔ بند کریں تو صرف رقم محفوظ ہوگی۔")
    val purgeRaw = Txt("Delete stored bank messages", "محفوظ بینک پیغامات حذف کریں")
    val purgeRawNote = Txt("Keeps your transactions, removes the original SMS text.", "لین دین رہیں گے، اصل SMS متن مٹ جائے گا۔")
    val purged = Txt("Bank messages deleted", "بینک پیغامات حذف ہو گئے")
    val locked = Txt("Budget is locked", "بجٹ لاک ہے")
    val unlock = Txt("Unlock", "کھولیں")
    val unlockReason = Txt("Unlock your Budget", "اپنا بجٹ کھولیں")
    val money = Txt("Amount (Rs)", "رقم (Rs)")
    val balance = Txt("Balance after", "بعد میں بیلنس")
    val moneyOut = Txt("Money out", "رقم نکلی")
    val moneyIn = Txt("Money in", "رقم آئی")
    val payee = Txt("Shop or person", "دکان یا شخص")
    val notes = Txt("Notes", "نوٹ")
    val saved = Txt("Saved", "محفوظ ہو گیا")
    val receiptTitle = Txt("Save receipt", "رسید محفوظ کریں")
    val receiptAsTx = Txt("Add %1\$s to Budget as one purchase", "%1\$s کو بجٹ میں ایک خریداری کے طور پر شامل کریں")
    val shareReceiptPrices = Txt("Share item prices with the community (anonymous)", "اشیاء کی قیمتیں کمیونٹی کے ساتھ شیئر کریں (گمنام)")
    val shareReceiptPricesNote = Txt(
        "Shares item, price, pack size, the store name and its area — never your exact location or anything about you.",
        "صرف چیز، قیمت، پیک کا سائز، دکان کا نام اور علاقہ — آپ کی درست جگہ یا آپ کے بارے میں کچھ نہیں۔",
    )
    val storeName = Txt("Store name", "دکان کا نام")
    val storeArea = Txt("Area (e.g. Model Town)", "علاقہ (مثلاً ماڈل ٹاؤن)")
    val channelNames: Map<String, Txt> = mapOf(
        "card" to Txt("Card", "کارڈ"), "ibft" to Txt("Bank transfer", "بینک ٹرانسفر"), "raast" to Txt("Raast", "راست"),
        "atm" to Txt("ATM", "اے ٹی ایم"), "wallet" to Txt("Wallet", "والٹ"), "bill" to Txt("Bill payment", "بل کی ادائیگی"),
        "cheque" to Txt("Cheque", "چیک"), "cash" to Txt("Cash", "نقد"), "other" to Txt("Other", "دیگر"),
    )
    fun channel(key: String): Txt = channelNames[key.lowercase()] ?: channelNames.getValue("other")
}
