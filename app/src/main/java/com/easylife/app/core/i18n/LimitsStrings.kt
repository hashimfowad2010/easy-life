package com.easylife.app.core.i18n

object SLimits {
    val title = Txt("Bijli Guard", "بجلی گارڈ")
    val noMeterTitle = Txt("Protect your 200-unit status", "اپنا 200 یونٹ والا فائدہ بچائیں")
    val noMeterBody = Txt(
        "If you use 201 units even once, protected rates stop for 6 months and the per-unit price can double. " +
            "Easy Life warns you before that happens.",
        "اگر ایک بار بھی 201 یونٹ ہو جائیں تو 6 مہینے تک رعایتی ریٹ ختم ہو جاتا ہے اور فی یونٹ قیمت تقریباً دگنی ہو سکتی ہے۔ " +
            "آسان زندگی اس سے پہلے آپ کو خبردار کرتی ہے۔",
    )
    val scanMyBill = Txt("Scan my electricity bill", "بجلی کا بل اسکین کریں")
    val enterByHand = Txt("Enter last bill by hand", "پچھلا بل خود لکھیں")
    val unitsThisCycle = Txt("units this cycle", "یونٹ اس مہینے")
    val ofLimit = Txt("of %1\$s", "%1\$s میں سے")
    val riskSafe = Txt("On track", "ٹھیک چل رہا ہے")
    val riskWatch = Txt("Getting close", "حد کے قریب")
    val riskDanger = Txt("May cross the limit", "حد پار ہو سکتی ہے")
    val riskCrossed = Txt("Limit crossed", "حد پار ہو گئی")
    val headlineSafe = Txt(
        "At this pace you'll use about %1\$s units by %2\$s — under %3\$s.",
        "اسی رفتار سے %2\$s تک تقریباً %1\$s یونٹ ہوں گے — %3\$s سے کم۔",
    )
    val headlineWatch = Txt(
        "At this pace you'll reach %1\$s units by %2\$s. Keep to %3\$s units a day to stay under %4\$s.",
        "اسی رفتار سے %2\$s تک %1\$s یونٹ ہو جائیں گے۔ %4\$s سے کم رہنے کے لیے روزانہ %3\$s یونٹ تک رکھیں۔",
    )
    val headlineDanger = Txt(
        "At this pace you'll reach %1\$s units by %2\$s and cross %4\$s around %5\$s. Keep to %3\$s units a day to stay protected.",
        "اسی رفتار سے %2\$s تک %1\$s یونٹ ہو جائیں گے اور تقریباً %5\$s کو %4\$s پار ہو جائیں گے۔ بچنے کے لیے روزانہ %3\$s یونٹ تک رکھیں۔",
    )
    val headlineCrossed = Txt(
        "You have used %1\$s units this cycle — more than %2\$s.",
        "اس مہینے %1\$s یونٹ استعمال ہو چکے ہیں — %2\$s سے زیادہ۔",
    )
    val dailyAllowance = Txt("Daily allowance", "روزانہ کی حد")
    val allowanceValue = Txt("≤ %1\$s / day", "روزانہ %1\$s تک")
    val projected = Txt("Projected", "اندازاً کل")
    val daysLeft = Txt("Days to reading", "ریڈنگ میں دن")
    val usingNow = Txt("Using now", "موجودہ استعمال")
    val perDayValue = Txt("%1\$s / day", "روزانہ %1\$s")
    val crossDate = Txt("Would cross on", "حد پار ہوگی")
    val needReading = Txt(
        "Add one more reading so Easy Life can see how fast you use electricity.",
        "ایک اور ریڈنگ شامل کریں تاکہ آسان زندگی دیکھ سکے کہ بجلی کتنی تیزی سے خرچ ہو رہی ہے۔",
    )
    val usingPrior = Txt("Using last month's average until you add a reading.", "جب تک آپ ریڈنگ نہیں ڈالتے، پچھلے مہینے کا اوسط استعمال ہو رہا ہے۔")
    val readingDown = Txt(
        "One reading (%2\$s) is lower than the one before (%1\$s). Please check it — it was left out.",
        "ایک ریڈنگ (%2\$s) پچھلی ریڈنگ (%1\$s) سے کم ہے۔ براہ کرم چیک کریں — اسے حساب میں شامل نہیں کیا گیا۔",
    )
    val rolloverNote = Txt("The meter seems to have rolled over to zero; this was counted correctly.", "لگتا ہے میٹر دوبارہ صفر سے شروع ہوا؛ حساب درست کیا گیا ہے۔")
    val noBaseline = Txt(
        "Add the \"present reading\" and reading date from your last bill to start this cycle.",
        "یہ مہینہ شروع کرنے کے لیے پچھلے بل کی \"موجودہ ریڈنگ\" اور ریڈنگ کی تاریخ لکھیں۔",
    )
    val overdue = Txt(
        "The expected reading date has passed. Scan your new bill to start the next cycle.",
        "ریڈنگ کی متوقع تاریخ گزر گئی ہے۔ اگلا مہینہ شروع کرنے کے لیے نیا بل اسکین کریں۔",
    )
    val slipTitle = Txt("If the meter reader comes late", "اگر میٹر ریڈر دیر سے آئے")
    val slipDays = Txt("Read %1\$s days late", "%1\$s دن دیر سے")
    val slipWarn = Txt(
        "If the meter is read %1\$s days late, you'd be at %2\$s units.",
        "اگر میٹر %1\$s دن دیر سے پڑھا گیا تو %2\$s یونٹ ہو جائیں گے۔",
    )
    val readingDate = Txt("Expected reading date: %1\$s", "متوقع ریڈنگ کی تاریخ: %1\$s")
    val changeDate = Txt("Change", "بدلیں")
    val billTitle = Txt("Estimated bill this cycle", "اس مہینے کا اندازاً بل")
    val stayUnder = Txt("Stay at %1\$s units or less", "%1\$s یونٹ یا کم رہیں")
    val crossTo = Txt("Reach %1\$s units", "%1\$s یونٹ ہو جائیں")
    val difference = Txt("That's %1\$s more", "یعنی %1\$s زیادہ")
    val sixMonth = Txt(
        "Crossing once can cost about %1\$s extra over the next 6 months (protected rates stop).",
        "ایک بار حد پار ہونے سے اگلے 6 مہینوں میں تقریباً %1\$s اضافی لگ سکتے ہیں (رعایتی ریٹ ختم)۔",
    )
    val estimateNote = Txt(
        "Estimate from tariff data of %1\$s. Taxes and adjustments vary — calibrate with your last bill.",
        "%1\$s کے ٹیرف ڈیٹا سے اندازہ۔ ٹیکس اور ایڈجسٹمنٹ بدلتے رہتے ہیں — پچھلے بل سے درست کریں۔",
    )
    val calibrated = Txt("Calibrated with your bill (×%1\$s)", "آپ کے بل سے درست کیا گیا (×%1\$s)")
    val protectedYes = Txt("Protected consumer", "محفوظ (پروٹیکٹڈ) صارف")
    val protectedNo = Txt("Not protected right now", "ابھی محفوظ (پروٹیکٹڈ) نہیں")
    val protectedUnknown = Txt("Status not known yet (treated as protected)", "حیثیت ابھی معلوم نہیں (محفوظ مانا گیا ہے)")
    val protectedExplain = Txt(
        "You used 200 units or less in each of the last 6 months, so you get lower protected rates.",
        "پچھلے 6 مہینوں میں ہر مہینے 200 یونٹ یا کم استعمال ہوئے، اس لیے آپ کو کم (پروٹیکٹڈ) ریٹ ملتا ہے۔",
    )
    val nonProtectedExplain = Txt(
        "In %1\$s you used more than 200 units, so protected rates stop for 6 months.",
        "%1\$s میں 200 سے زیادہ یونٹ استعمال ہوئے، اس لیے 6 مہینے تک رعایتی ریٹ نہیں ملے گا۔",
    )
    val canReturn = Txt(
        "Stay at 200 units or less every month and you can be protected again from the %1\$s bill.",
        "ہر مہینے 200 یونٹ یا کم رکھیں تو %1\$s کے بل سے دوبارہ محفوظ ہو سکتے ہیں۔",
    )
    val unknownExplain = Txt(
        "Scan a bill with its 12-month table, or set the status in meter settings.",
        "12 مہینوں کی جدول والا بل اسکین کریں، یا میٹر سیٹنگز میں حیثیت لکھیں۔",
    )
    val nextBoundary = Txt("Next price step at %1\$s units.", "اگلا قیمت کا درجہ %1\$s یونٹ پر۔")
    val rulesChanging = Txt(
        "Electricity rules may change (the government has discussed ending the 6-month rule). Easy Life updates its tariff data online.",
        "بجلی کے قوانین بدل سکتے ہیں (حکومت 6 مہینے والا اصول ختم کرنے پر غور کر چکی ہے)۔ آسان زندگی ٹیرف ڈیٹا آن لائن اپ ڈیٹ کرتی ہے۔",
    )
    val addReading = Txt("Add reading", "ریڈنگ شامل کریں")
    val history = Txt("History", "تاریخچہ")
    val appliances = Txt("Appliance advisor", "آلات کا مشیر")
    val calibrate = Txt("Calibrate with my last bill", "پچھلے بل سے درست کریں")
    val meterSettings = Txt("Meter settings", "میٹر سیٹنگز")
    val customLimits = Txt("Other limits (gas, data…)", "دوسری حدیں (گیس، ڈیٹا…)")
    val lastReading = Txt("Last reading %1\$s · %2\$s", "آخری ریڈنگ %1\$s · %2\$s")
    val tips = Txt("See saving tips", "بچت کے مشورے دیکھیں")
    val tariffUpdated = Txt("Tariff data updated: %1\$s", "ٹیرف ڈیٹا اپ ڈیٹ: %1\$s")

    // Meter setup
    val setupTitle = Txt("Meter settings", "میٹر سیٹنگز")
    val meterName = Txt("Name (e.g. Home, Upstairs)", "نام (مثلاً گھر، اوپر والا)")
    val provider = Txt("Electricity company", "بجلی کی کمپنی")
    val lastBillReadingDate = Txt("Reading date on your last bill", "پچھلے بل پر ریڈنگ کی تاریخ")
    val lastBillPresentReading = Txt("\"Present reading\" on your last bill", "پچھلے بل پر \"موجودہ ریڈنگ\"")
    val nextReadingDate = Txt("Expected next reading date", "اگلی ریڈنگ کی متوقع تاریخ")
    val limitUnits = Txt("Limit (units)", "حد (یونٹ)")
    val safetyBuffer = Txt("Safety buffer (units)", "حفاظتی گنجائش (یونٹ)")
    val safetyBufferHelp = Txt("Warn a little before the limit, e.g. 5 units early.", "حد سے تھوڑا پہلے خبردار کریں، مثلاً 5 یونٹ پہلے۔")
    val consumerStatus = Txt("Consumer status", "صارف کی حیثیت")
    val statusAuto = Txt("Automatic (from bills)", "خودکار (بل سے)")
    val statusProtected = Txt("Protected", "محفوظ (پروٹیکٹڈ)")
    val statusNonProtected = Txt("Non-protected", "غیر محفوظ")
    val statusLifeline = Txt("Lifeline (up to 100 units)", "لائف لائن (100 یونٹ تک)")
    val billingMode = Txt("How units are charged", "یونٹ کا حساب کیسے")
    val billingAuto = Txt("Follow tariff data", "ٹیرف ڈیٹا کے مطابق")
    val billingIncremental = Txt("Each slab at its own rate", "ہر سلیب اپنے ریٹ پر")
    val billingSingle = Txt("All units at one slab's rate", "تمام یونٹ ایک سلیب کے ریٹ پر")
    val meterDigits = Txt("Digits on meter display", "میٹر پر ہندسوں کی تعداد")
    val photoEvery = Txt("Remind me to snap the meter every (days)", "میٹر کی تصویر یاد دلائیں ہر (دن)")
    val deleteMeter = Txt("Delete this meter", "یہ میٹر حذف کریں")

    // Add reading
    val readingTitle = Txt("Meter reading", "میٹر ریڈنگ")
    val readingValue = Txt("Reading (kWh units on the meter)", "ریڈنگ (میٹر پر kWh یونٹ)")
    val takenOn = Txt("Taken on", "تاریخ")
    val takePhoto = Txt("Take a photo of the meter", "میٹر کی تصویر لیں")
    val digitsSeen = Txt("Digits the phone saw: %1\$s", "فون نے یہ ہندسے دیکھے: %1\$s")
    val lowerThanLast = Txt(
        "This is lower than your last reading (%1\$s). Please check the digits.",
        "یہ پچھلی ریڈنگ (%1\$s) سے کم ہے۔ براہ کرم ہندسے چیک کریں۔",
    )
    val notSure = Txt("The phone was not sure. Please check each digit before saving.", "فون کو یقین نہیں تھا۔ محفوظ کرنے سے پہلے ہر ہندسہ چیک کریں۔")
    val otherCandidates = Txt("Other numbers seen:", "دوسرے نمبر جو نظر آئے:")
    val readingSaved = Txt("Reading saved", "ریڈنگ محفوظ ہو گئی")
    val noMeterYet = Txt("Set up your meter first.", "پہلے اپنا میٹر شامل کریں۔")
    val since = Txt("+%1\$s units since last reading", "پچھلی ریڈنگ سے %1\$s یونٹ زیادہ")

    // History
    val dailyUse = Txt("Daily use (between readings)", "روزانہ استعمال (ریڈنگز کے درمیان)")
    val monthlyUse = Txt("Monthly units (from bills)", "مہینہ وار یونٹ (بلوں سے)")
    val monthsOver = Txt("Months over 200 units", "200 یونٹ سے زیادہ والے مہینے")
    val noMonthsOver = Txt("No month over 200 units in your history.", "آپ کے ریکارڈ میں کوئی مہینہ 200 یونٹ سے زیادہ نہیں۔")
    val readings = Txt("Readings", "ریڈنگز")
    val deleteReading = Txt("Delete this reading?", "یہ ریڈنگ حذف کریں؟")
    val noHistory = Txt("No history yet. Scan a bill to load 12 months.", "ابھی کوئی ریکارڈ نہیں۔ 12 مہینوں کے لیے بل اسکین کریں۔")

    // Appliances
    val appliancesIntro = Txt(
        "List your appliances once. Easy Life estimates where your units go and suggests simple cuts. Wattages are approximate — edit them if you know better.",
        "اپنے آلات ایک بار لکھیں۔ آسان زندگی اندازہ لگاتی ہے کہ یونٹ کہاں خرچ ہوتے ہیں اور آسان بچت بتاتی ہے۔ واٹ تقریباً ہیں — اگر معلوم ہوں تو بدل دیں۔",
    )
    val addAppliance = Txt("Add appliance", "آلہ شامل کریں")
    val watts = Txt("Watts (approx.)", "واٹ (تقریباً)")
    val hoursPerDay = Txt("Hours per day", "روزانہ گھنٹے")
    val quantity = Txt("How many", "کتنے")
    val estimatedPerDay = Txt("About %1\$s units a day from these appliances", "ان آلات سے روزانہ تقریباً %1\$s یونٹ")
    val needSaving = Txt("To stay under the limit, save about %1\$s units a day:", "حد کے اندر رہنے کے لیے روزانہ تقریباً %1\$s یونٹ بچائیں:")
    val easyCuts = Txt("Easy ways to save:", "بچت کے آسان طریقے:")
    val cutLine = Txt("Reduce %1\$s by %2\$s a day → saves about %3\$s units a day", "%1\$s روزانہ %2\$s کم چلائیں → روزانہ تقریباً %3\$s یونٹ بچت")
    val unitsDay = Txt("%1\$s units/day", "%1\$s یونٹ روزانہ")
    val inverterTip = Txt(
        "Tip: set the AC to 26°C and use a fan with it — each degree warmer saves electricity.",
        "مشورہ: AC کو 26 ڈگری پر رکھیں اور ساتھ پنکھا چلائیں — ہر ڈگری زیادہ رکھنے سے بجلی بچتی ہے۔",
    )

    // Calibrate
    val calibrateTitle = Txt("Calibrate with my last bill", "پچھلے بل سے درست کریں")
    val calibrateIntro = Txt(
        "Our estimate covers only the energy price. Enter your last bill's units and payable amount and Easy Life will include taxes and adjustments in future estimates.",
        "ہمارا اندازہ صرف بجلی کی قیمت کا ہے۔ پچھلے بل کے یونٹ اور قابلِ ادائیگی رقم لکھیں تو آسان زندگی آئندہ اندازوں میں ٹیکس اور ایڈجسٹمنٹ شامل کرے گی۔",
    )
    val billUnits = Txt("Units on that bill", "اس بل کے یونٹ")
    val billPayable = Txt("Payable within due date (Rs)", "مقررہ تاریخ تک قابلِ ادائیگی رقم (Rs)")
    val calibrationDone = Txt("Saved. Estimates now include taxes (×%1\$s).", "محفوظ ہو گیا۔ اب اندازوں میں ٹیکس شامل ہیں (×%1\$s)۔")
    val calibrationBad = Txt("These numbers don't look right. Please check them.", "یہ اعداد درست نہیں لگتے۔ براہ کرم چیک کریں۔")
    val clearCalibration = Txt("Remove calibration", "درستگی ہٹائیں")

    // Custom limits
    val customTitle = Txt("Other limits", "دوسری حدیں")
    val customIntro = Txt(
        "Track anything with a limit — gas units, mobile data, water — with the same early warnings.",
        "کسی بھی حد والی چیز پر نظر رکھیں — گیس، موبائل ڈیٹا، پانی — انہی ابتدائی وارننگز کے ساتھ۔",
    )
    val newLimit = Txt("New limit", "نئی حد")
    val limitName = Txt("Name (e.g. Mobile data)", "نام (مثلاً موبائل ڈیٹا)")
    val unitName = Txt("Unit (e.g. GB, units)", "اکائی (مثلاً GB، یونٹ)")
    val limitValue = Txt("Limit", "حد")
    val periodDays = Txt("Resets every (days)", "ہر کتنے دن بعد نئی شروع ہو")
    val modeReading = Txt("I read a counter (like a meter)", "میں کاؤنٹر پڑھتا/پڑھتی ہوں (میٹر کی طرح)")
    val modeAmount = Txt("I add amounts used", "میں استعمال شدہ مقدار شامل کرتا/کرتی ہوں")
    val addEntry = Txt("Add entry", "اندراج شامل کریں")
    val entryValue = Txt("Value", "قدر")
    val used = Txt("Used", "استعمال")
}

object SAppliance {
    val names: Map<String, Txt> = mapOf(
        "AC_1_TON" to Txt("AC 1 ton", "AC ایک ٹن"),
        "AC_1_5_TON" to Txt("AC 1.5 ton", "AC ڈیڑھ ٹن"),
        "AC_1_TON_INVERTER" to Txt("Inverter AC 1 ton", "انورٹر AC ایک ٹن"),
        "AC_1_5_TON_INVERTER" to Txt("Inverter AC 1.5 ton", "انورٹر AC ڈیڑھ ٹن"),
        "FRIDGE" to Txt("Fridge", "فریج"),
        "FRIDGE_INVERTER" to Txt("Inverter fridge", "انورٹر فریج"),
        "DEEP_FREEZER" to Txt("Deep freezer", "ڈیپ فریزر"),
        "WATER_MOTOR" to Txt("Water motor", "پانی کی موٹر"),
        "IRON" to Txt("Iron", "استری"),
        "WASHING_MACHINE" to Txt("Washing machine", "واشنگ مشین"),
        "CEILING_FAN" to Txt("Ceiling fans", "چھت کے پنکھے"),
        "INVERTER_FAN" to Txt("Inverter (DC) fans", "انورٹر (DC) پنکھے"),
        "PEDESTAL_FAN" to Txt("Pedestal fan", "اسٹینڈ والا پنکھا"),
        "AIR_COOLER" to Txt("Air cooler", "روم کولر"),
        "LED_LIGHT" to Txt("LED lights", "LED بلب"),
        "TUBE_LIGHT" to Txt("Tube lights", "ٹیوب لائٹ"),
        "ENERGY_SAVER" to Txt("Energy savers", "انرجی سیور"),
        "ELECTRIC_GEYSER" to Txt("Electric geyser", "بجلی کا گیزر"),
        "ROOM_HEATER" to Txt("Room heater", "ہیٹر"),
        "TV" to Txt("TV", "ٹی وی"),
        "COMPUTER" to Txt("Computer", "کمپیوٹر"),
        "MICROWAVE" to Txt("Microwave", "مائیکروویو"),
        "ELECTRIC_KETTLE" to Txt("Electric kettle", "بجلی کی کیتلی"),
        "UPS_CHARGING" to Txt("UPS charging", "UPS چارجنگ"),
        "OTHER" to Txt("Other", "دیگر"),
    )

    fun name(type: String): Txt = names[type] ?: Txt(type, type)
}

object SBills {
    val title = Txt("Bills", "بل")
    val upcoming = Txt("Upcoming", "آنے والے")
    val overdue = Txt("Overdue", "لیٹ")
    val paid = Txt("Paid", "ادا شدہ")
    val dueOn = Txt("Due %1\$s", "آخری تاریخ %1\$s")
    val dueRelative = Txt("Due %1\$s", "جمع کروائیں %1\$s")
    val overdueBy = Txt("Overdue since %1\$s", "%1\$s سے لیٹ")
    val paidOn = Txt("Paid %1\$s", "ادا کیا %1\$s")
    val lateFee = Txt("Late fee %1\$s", "جرمانہ %1\$s")
    val payToSave = Txt("Pay by %1\$s to save %2\$s", "%1\$s تک ادا کریں اور %2\$s بچائیں")
    val markPaid = Txt("Mark paid", "ادا شدہ کریں")
    val markUnpaid = Txt("Mark unpaid", "غیر ادا شدہ کریں")
    val addManually = Txt("Add by hand", "خود لکھیں")
    val scanBill = Txt("Scan a bill", "بل اسکین کریں")
    val empty = Txt("No bills yet", "ابھی کوئی بل نہیں")
    val emptyBody = Txt(
        "Scan a bill and Easy Life will remind you before the due date, so you never pay a late fee.",
        "بل اسکین کریں، آسان زندگی آخری تاریخ سے پہلے یاد دلائے گی تاکہ جرمانہ نہ لگے۔",
    )
    val confirmTitle = Txt("Check the bill", "بل چیک کریں")
    val confirmIntro = Txt(
        "Please check every field before saving. Fix anything that is wrong.",
        "محفوظ کرنے سے پہلے ہر خانہ چیک کریں۔ جو غلط ہو اسے درست کریں۔",
    )
    val reading = Txt("Reading the bill on your phone…", "فون پر بل پڑھا جا رہا ہے…")
    val billType = Txt("Bill type", "بل کی قسم")
    val provider = Txt("Company / school", "کمپنی / اسکول")
    val referenceNo = Txt("Reference / consumer no. (kept on this phone only)", "ریفرنس / صارف نمبر (صرف اس فون پر)")
    val billingMonth = Txt("Bill month (YYYY-MM)", "بل کا مہینہ (YYYY-MM)")
    val issueDate = Txt("Issue date", "جاری ہونے کی تاریخ")
    val dueDate = Txt("Due date", "آخری تاریخ")
    val readingDate = Txt("Meter reading date", "میٹر ریڈنگ کی تاریخ")
    val amountWithin = Txt("Payable within due date (Rs)", "آخری تاریخ تک رقم (Rs)")
    val amountAfter = Txt("Payable after due date (Rs)", "آخری تاریخ کے بعد رقم (Rs)")
    val units = Txt("Units consumed", "استعمال شدہ یونٹ")
    val previousReading = Txt("Previous reading", "پچھلی ریڈنگ")
    val presentReading = Txt("Present reading", "موجودہ ریڈنگ")
    val statusPrinted = Txt("Status printed on bill", "بل پر لکھی حیثیت")
    val historyFound = Txt("%1\$s months of history found", "%1\$s مہینوں کا ریکارڈ ملا")
    val save = Txt("Save bill", "بل محفوظ کریں")
    val savedAndLinked = Txt("Bill saved. Bijli Guard updated with this bill.", "بل محفوظ ہو گیا۔ بجلی گارڈ اس بل سے اپ ڈیٹ ہو گیا۔")
    val saved = Txt("Bill saved. Reminders set.", "بل محفوظ ہو گیا۔ یاد دہانیاں لگ گئیں۔")
    val dateHint = Txt("e.g. 2026-10-23", "مثلاً 2026-10-23")
    val invalidDate = Txt("Use the format YYYY-MM-DD", "اس طرح لکھیں: YYYY-MM-DD")
    val needDue = Txt("Add the due date so Easy Life can remind you.", "آخری تاریخ لکھیں تاکہ آسان زندگی یاد دلا سکے۔")
    val deleteBill = Txt("Delete bill", "بل حذف کریں")
    val deleteConfirm = Txt("Delete this bill?", "یہ بل حذف کریں؟")
    val historyTitle = Txt("Past months on this bill", "اس بل پر پچھلے مہینے")
    val monthlyTotals = Txt("Monthly totals", "مہینہ وار کل")
    val openLimits = Txt("Open in Bijli Guard", "بجلی گارڈ میں کھولیں")
    val reminders = Txt("Reminders: 5 days, 2 days and on the due date.", "یاد دہانی: 5 دن، 2 دن پہلے اور آخری دن۔")
    val notificationsAsk = Txt(
        "Allow notifications so Easy Life can remind you before due dates?",
        "کیا آسان زندگی کو اطلاعات کی اجازت دیں تاکہ آخری تاریخ سے پہلے یاد دلا سکے؟",
    )
    val allow = Txt("Allow", "اجازت دیں")
    val typeElectricity = Txt("Electricity", "بجلی")
    val typeGas = Txt("Gas", "گیس")
    val typeWater = Txt("Water", "پانی")
    val typeInternet = Txt("Internet / phone", "انٹرنیٹ / فون")
    val typeMobile = Txt("Mobile postpaid", "موبائل پوسٹ پیڈ")
    val typeSchool = Txt("School fee", "اسکول فیس")
    val typeRent = Txt("Rent", "کرایہ")
    val typeOther = Txt("Other", "دیگر")

    fun type(key: String?): Txt = when (key) {
        "electricity" -> typeElectricity
        "gas" -> typeGas
        "water" -> typeWater
        "internet" -> typeInternet
        "mobile_postpaid" -> typeMobile
        "school_fee" -> typeSchool
        "rent" -> typeRent
        else -> typeOther
    }

    val types = listOf("electricity", "gas", "water", "internet", "mobile_postpaid", "school_fee", "rent", "other")
}
