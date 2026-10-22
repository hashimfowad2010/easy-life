package com.easylife.app.core.i18n

object SImpact {
    val reportTitle = Txt("ILAP impact report", "ILAP اثر رپورٹ")
    val reportIntro = Txt(
        "What Easy Life did on this phone. Counts only: no bills, messages or names.",
        "اس فون پر آسان زندگی نے کیا کیا۔ صرف گنتی: کوئی بل، پیغام یا نام نہیں۔",
    )
    val since = Txt("Since %1\$s", "%1\$s سے")
    val pilot = Txt("Pilot code: %1\$s", "پائلٹ کوڈ: %1\$s")
    val noPilot = Txt("No pilot code (add one in Settings → City)", "کوئی پائلٹ کوڈ نہیں (سیٹنگز ← شہر میں لکھیں)")
    val nothingYet = Txt("Nothing recorded yet. Use the app for a few days and come back.", "ابھی کچھ درج نہیں ہوا۔ چند دن ایپ استعمال کریں پھر دیکھیں۔")
    val headline = Txt("Highlights", "اہم باتیں")
    val bySource = Txt("Who read your scans and messages", "اسکین اور پیغامات کس نے پڑھے")
    val bySourceNote = Txt(
        "\"Corrected\" means you changed at least one value the AI read. Fewer corrections is the case for Smart AI.",
        "\"درستی\" کا مطلب ہے آپ نے AI کی پڑھی ہوئی کم از کم ایک چیز بدلی۔ کم درستیاں سمارٹ AI کی دلیل ہیں۔",
    )
    val results = Txt("%1\$d results", "%1\$d نتائج")
    val corrected = Txt("%1\$d%% corrected", "%1\$d%% درست کیے")
    val perModule = Txt("Activity by module", "حصوں کے لحاظ سے سرگرمی")
    val saveCsv = Txt("Save as CSV", "CSV محفوظ کریں")
    val savePdf = Txt("Save one-page PDF", "ایک صفحے کی PDF محفوظ کریں")
    val saved = Txt("Report saved", "رپورٹ محفوظ ہو گئی")
    val saveFailed = Txt("Could not save the report", "رپورٹ محفوظ نہیں ہو سکی")
    val pdfFooter = Txt(
        "Generated on the phone by Easy Life. Anonymous counts; money rounded to Rs 10.",
        "آسان زندگی نے فون پر بنائی۔ بغیر نام کی گنتی؛ رقم Rs 10 تک گول۔",
    )
    val demoWarning = Txt("Demo mode is on: these numbers include fictional demo activity.", "نمونہ موڈ آن ہے: ان اعداد میں فرضی سرگرمی بھی شامل ہے۔")

    // Highlight tiles
    val warnings = Txt("Limit warnings shown", "حد کی وارننگز")
    val actionsAfter = Txt("Actions after a warning", "وارننگ کے بعد اقدامات")
    val cyclesUnder = Txt("Cycles kept under the limit after a warning", "وارننگ کے بعد حد سے نیچے رہے")
    val paidOnTime = Txt("Bills paid before the due date", "مقررہ تاریخ سے پہلے ادا شدہ بل")
    val lateFees = Txt("Late fees avoided", "بچائی گئی لیٹ فیس")
    val docs = Txt("Documents read or translated", "پڑھے یا ترجمہ کیے گئے کاغذات")
    val comparisons = Txt("Shopping comparisons", "خریداری کے موازنے")
    val shopSavings = Txt("Estimated shopping savings", "خریداری میں اندازاً بچت")
    val excluded = Txt("Transactions excluded from budget", "بجٹ سے نکالے گئے لین دین")
    val feedbackCount = Txt("Feedback given", "دی گئی رائے")
    val avgRating = Txt("Average usefulness (1–5)", "اوسط فائدہ (1–5)")

    // Modules (report rows)
    val moduleLimits = Txt("Bijli Guard", "بجلی گارڈ")
    val moduleBills = Txt("Bills", "بل")
    val moduleBudget = Txt("Budget", "بجٹ")
    val moduleUrdu = Txt("Urdu Helper", "اردو مددگار")
    val moduleShopping = Txt("Smart Shopping", "سمارٹ شاپنگ")
    val moduleScan = Txt("Scans", "اسکین")
    val moduleOther = Txt("Other", "دیگر")

    // Sources
    val sourceOnDevice = Txt("Read on your phone", "فون پر پڑھا گیا")
    val sourceGemini = Txt("Smart AI (Gemini)", "سمارٹ AI (Gemini)")
    val sourceClaude = Txt("Smart AI (Claude)", "سمارٹ AI (Claude)")
    val sourceDemo = Txt("Demo data", "نمونہ ڈیٹا")

    // Feedback
    val feedbackTitle = Txt("Your feedback", "آپ کی رائے")
    val feedbackIntro = Txt("Three quick questions. It takes one minute.", "تین آسان سوال۔ ایک منٹ لگے گا۔")
    val q1 = Txt("How useful was Easy Life?", "آسان زندگی کتنی مفید رہی؟")
    val q1Low = Txt("Not useful", "مفید نہیں")
    val q1High = Txt("Very useful", "بہت مفید")
    val q2 = Txt("What did it help with?", "اس نے کس چیز میں مدد کی؟")
    val q2Electricity = Txt("Electricity limit", "بجلی کی حد")
    val q2Bills = Txt("Paying bills on time", "بل وقت پر ادا کرنا")
    val q2Budget = Txt("Budget and spending", "بجٹ اور خرچ")
    val q2Urdu = Txt("Reading documents", "کاغذات پڑھنا")
    val q2Shopping = Txt("Shopping prices", "خریداری کی قیمتیں")
    val q2Other = Txt("Something else", "کچھ اور")
    val q3 = Txt("Did it change anything you do?", "کیا اس سے آپ کے کسی کام میں تبدیلی آئی؟")
    val yes = Txt("Yes", "ہاں")
    val no = Txt("No", "نہیں")
    val comment = Txt("Tell us more (optional)", "مزید بتائیں (اختیاری)")
    val commentHint = Txt("e.g. We now switch off the AC earlier", "مثلاً اب ہم AC پہلے بند کر دیتے ہیں")
    val send = Txt("Send feedback", "رائے بھیجیں")
    val later = Txt("Later", "بعد میں")
    val anonymousNote = Txt(
        "Sent without your name or phone number. Numbers like phone or CNIC in the comment are hidden.",
        "آپ کے نام یا فون نمبر کے بغیر بھیجی جاتی ہے۔ تبصرے میں فون یا شناختی کارڈ نمبر چھپا دیے جاتے ہیں۔",
    )
    val thanks = Txt("Thank you! Your feedback was sent.", "شکریہ! آپ کی رائے بھیج دی گئی۔")
    val thanksSavedLocally = Txt("Thank you! Saved on your phone; it will be sent when online.", "شکریہ! فون پر محفوظ ہو گئی؛ انٹرنیٹ ملنے پر بھیج دی جائے گی۔")
    val todayTitle = Txt("How is Easy Life helping?", "آسان زندگی کتنی مدد کر رہی ہے؟")
    val todayBody = Txt("Answer 3 quick questions to help this student project.", "اس طالب علم کے منصوبے کی مدد کے لیے 3 آسان سوالوں کے جواب دیں۔")
}
