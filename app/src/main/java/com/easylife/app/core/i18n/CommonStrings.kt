package com.easylife.app.core.i18n

/** Text shared across modules. Urdu kept simple and everyday. */
object SCommon {
    val appName = Txt("Easy Life", "آسان زندگی")
    val ok = Txt("OK", "ٹھیک ہے")
    val cancel = Txt("Cancel", "منسوخ")
    val save = Txt("Save", "محفوظ کریں")
    val saved = Txt("Saved", "محفوظ ہو گیا")
    val delete = Txt("Delete", "حذف کریں")
    val edit = Txt("Edit", "تبدیل کریں")
    val done = Txt("Done", "ہو گیا")
    val next = Txt("Next", "آگے")
    val back = Txt("Back", "واپس")
    val skip = Txt("Skip", "چھوڑیں")
    val close = Txt("Close", "بند کریں")
    val retry = Txt("Try again", "دوبارہ کوشش کریں")
    val continueText = Txt("Continue", "جاری رکھیں")
    val yes = Txt("Yes", "ہاں")
    val no = Txt("No", "نہیں")
    val undo = Txt("Undo", "واپس لیں")
    val add = Txt("Add", "شامل کریں")
    val share = Txt("Share", "شیئر کریں")
    val copy = Txt("Copy", "کاپی کریں")
    val copied = Txt("Copied", "کاپی ہو گیا")
    val seeAll = Txt("See all", "سب دیکھیں")
    val more = Txt("More", "مزید")
    val loading = Txt("Working…", "کام ہو رہا ہے…")
    val search = Txt("Search", "تلاش")
    val optional = Txt("optional", "اختیاری")
    val today = Txt("Today", "آج")
    val estimated = Txt("Estimated", "اندازاً")
    val approximate = Txt("approximate", "تقریباً")
    val notSet = Txt("Not set", "طے نہیں")
    val unknown = Txt("Unknown", "معلوم نہیں")
    val on = Txt("On", "آن")
    val off = Txt("Off", "آف")
    val error = Txt("Something went wrong", "کچھ غلط ہو گیا")
    val readAloud = Txt("Read aloud", "سن کر سمجھیں")
    val stopReading = Txt("Stop", "روکیں")
    val showInUrdu = Txt("Show in Urdu", "اردو میں دکھائیں")
    val confirm = Txt("Confirm", "تصدیق کریں")
    val checkThis = Txt("Please check this", "براہ کرم اسے دیکھ لیں")
    val checkHighlighted = Txt(
        "Please check the highlighted fields. The phone was not sure about them.",
        "نشان زدہ خانے ضرور دیکھ لیں۔ فون کو ان کے بارے میں یقین نہیں تھا۔",
    )
    val nothingHereYet = Txt("Nothing here yet", "ابھی یہاں کچھ نہیں")
    val permissionDenied = Txt(
        "Permission not given. You can still type it in yourself.",
        "اجازت نہیں ملی۔ آپ خود بھی لکھ سکتے ہیں۔",
    )
    val openSettings = Txt("Open settings", "سیٹنگز کھولیں")
    val units = Txt("units", "یونٹ")
    val perDay = Txt("per day", "روزانہ")
    val rupeesShort = Txt("Rs", "Rs")
    val amount = Txt("Amount", "رقم")
    val date = Txt("Date", "تاریخ")
    val notes = Txt("Notes", "نوٹ")
    val name = Txt("Name", "نام")
    val total = Txt("Total", "کل")
    val days = Txt("days", "دن")
    val tipsTitle = Txt("Tips", "مشورے")
    val reviewInbox = Txt("Review inbox", "جانچ کے لیے")
    val beta = Txt("Beta", "آزمائشی")
}

/** Where a result came from. Shown on every AI result and stored with every saved record. */
object SProvenance {
    val onDevice = Txt("Read on your phone", "آپ کے فون پر پڑھا گیا")
    val gemini = Txt("Smart AI (Gemini)", "سمارٹ AI (Gemini)")
    val claude = Txt("Smart AI (Claude)", "سمارٹ AI (Claude)")
    val demo = Txt("Demo data", "نمونہ ڈیٹا")
    val manual = Txt("Entered by you", "آپ نے لکھا")
    val imported = Txt("Imported", "درآمد شدہ")
    val bankSms = Txt("From bank SMS (read on your phone)", "بینک SMS سے (فون پر پڑھا گیا)")
    val community = Txt("Community report", "دوسرے صارفین کی رپورٹ")
    val smartAiBadge = Txt("Smart AI", "سمارٹ AI")
    val comingWithSmartAi = Txt(
        "Needs Smart AI: turn it on in Settings → Smart AI (free, needs internet). Explains in simple Urdu.",
        "سمارٹ AI چاہیے: سیٹنگز ← سمارٹ AI میں آن کریں (مفت، انٹرنیٹ ضروری)۔ آسان اردو میں سمجھاتا ہے۔",
    )
}

object SNav {
    val today = Txt("Today", "آج")
    val modules = Txt("Modules", "حصے")
    val scan = Txt("Scan", "اسکین")
    val settings = Txt("Settings", "سیٹنگز")
    val limits = Txt("Bijli Guard", "بجلی گارڈ")
    val limitsShort = Txt("Limits", "حدیں")
    val bills = Txt("Bills", "بل")
    val budget = Txt("Budget", "بجٹ")
    val urdu = Txt("Urdu Helper", "اردو مددگار")
    val shopping = Txt("Smart Shopping", "سمارٹ شاپنگ")
    val limitsDesc = Txt("Warns before you cross 200 units", "200 یونٹ سے پہلے خبردار کرتا ہے")
    val billsDesc = Txt("Due dates, late fees, reminders", "آخری تاریخ، جرمانہ، یاد دہانی")
    val budgetDesc = Txt("Spending from bank SMS, no passwords", "بینک SMS سے خرچ، پاس ورڈ کے بغیر")
    val urduDesc = Txt("English ↔ Urdu, read aloud", "انگریزی ↔ اردو، آواز میں سنیں")
    val shoppingDesc = Txt("Compare prices, check your budget", "قیمتیں ملائیں، بجٹ دیکھیں")
    val moduleOff = Txt("Turned off in Settings", "سیٹنگز میں بند ہے")
}
