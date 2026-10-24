package com.easylife.app.core.i18n

/** Short, honest privacy notice (same text as PRIVACY.md). */
object SPrivacy {
    val title = Txt("Privacy", "پرائیویسی")
    val points: List<Pair<Txt, Txt>> = listOf(
        Txt("Your data stays on this phone", "آپ کا ڈیٹا اسی فون پر رہتا ہے") to Txt(
            "Bills, meter readings, bank messages, transactions and lists are saved only on this phone, in an encrypted database. They are never synced or backed up to any server.",
            "بل، میٹر ریڈنگ، بینک پیغامات، لین دین اور فہرستیں صرف اسی فون پر خفیہ (encrypted) ڈیٹا بیس میں محفوظ ہوتی ہیں۔ یہ کبھی کسی سرور پر نہیں جاتیں۔",
        ),
        Txt("No bank passwords, ever", "بینک پاس ورڈ کبھی نہیں") to Txt(
            "Easy Life never asks for bank usernames, passwords, PINs, OTPs, CVVs, full card or account numbers. Adding a bank means choosing its name and, if you like, the last 4 digits.",
            "آسان زندگی کبھی بینک یوزر نیم، پاس ورڈ، پن، OTP، CVV، پورا کارڈ یا اکاؤنٹ نمبر نہیں مانگتی۔ بینک شامل کرنے کا مطلب صرف بینک کا نام اور چاہیں تو آخری 4 ہندسے۔",
        ),
        Txt("OTP messages are thrown away", "OTP پیغامات فوراً ضائع") to Txt(
            "Messages with OTPs, PINs or passwords are recognised and discarded immediately. They are never stored or sent anywhere.",
            "OTP، پن یا پاس ورڈ والے پیغامات پہچان کر فوراً ضائع کر دیے جاتے ہیں۔ یہ نہ محفوظ ہوتے ہیں نہ کہیں بھیجے جاتے ہیں۔",
        ),
        Txt("Photos are read on the phone", "تصاویر فون پر ہی پڑھی جاتی ہیں") to Txt(
            "Bill, meter and receipt photos are read on your phone and deleted after you save the result.",
            "بل، میٹر اور رسید کی تصاویر فون پر پڑھی جاتی ہیں اور نتیجہ محفوظ ہونے کے بعد مٹا دی جاتی ہیں۔",
        ),
        Txt("Smart AI only if you agree", "سمارٹ AI صرف آپ کی اجازت سے") to Txt(
            "Online Smart AI (Google Gemini, free) is off until you turn it on. Before it is used, Easy Life explains what is sent and asks you. Account, card, CNIC and phone numbers, names and addresses are hidden on the phone first. Google may use and review what is sent on its free tier. Bank SMS and budget data are never sent.",
            "آن لائن سمارٹ AI (گوگل جیمنائی، مفت) تب تک بند رہتا ہے جب تک آپ آن نہ کریں۔ استعمال سے پہلے آسان زندگی بتاتی ہے کہ کیا بھیجا جائے گا اور اجازت مانگتی ہے۔ اکاؤنٹ، کارڈ، شناختی کارڈ اور فون نمبر، نام اور پتے پہلے ہی چھپا دیے جاتے ہیں۔ مفت سروس میں گوگل بھیجی گئی چیزیں استعمال اور دیکھ سکتا ہے۔ بینک SMS اور بجٹ کا ڈیٹا کبھی نہیں بھیجا جاتا۔",
        ),
        Txt("Anonymous counts only with your OK", "صرف اجازت سے گمنام گنتی") to Txt(
            "If you opt in, Easy Life sends anonymous counts (like \"a reminder helped pay on time\") to show the project's impact. No names, numbers, messages or bills. Amounts are rounded. Switch it off any time.",
            "اگر آپ اجازت دیں تو منصوبے کا اثر دکھانے کے لیے صرف گمنام گنتی بھیجی جاتی ہے (جیسے \"یاد دہانی سے بل وقت پر جمع ہوا\")۔ نام، نمبر، پیغامات یا بل نہیں۔ رقم گول کی جاتی ہے۔ کبھی بھی بند کریں۔",
        ),
        Txt("Shop prices you choose to share", "دکان کی قیمتیں جو آپ بانٹیں") to Txt(
            "If you agree, item prices from your receipts are shared anonymously with the store name and a rough area — never your exact location.",
            "اگر آپ اجازت دیں تو رسید کی قیمتیں دکان کے نام اور تقریبی علاقے کے ساتھ گمنام طور پر شیئر ہوتی ہیں — آپ کی درست جگہ کبھی نہیں۔",
        ),
        Txt("You are in control", "اختیار آپ کے پاس ہے") to Txt(
            "In Settings you can export all your data, delete all your data, turn off any module and turn Smart AI on or off.",
            "سیٹنگز میں آپ اپنا سارا ڈیٹا نکال سکتے ہیں، سب مٹا سکتے ہیں، کوئی بھی حصہ بند کر سکتے ہیں اور سمارٹ AI آن یا آف کر سکتے ہیں۔",
        ),
    )
    val notAdvice = Txt(
        "Easy Life gives estimates and simple explanations, not financial, medical or legal advice.",
        "آسان زندگی اندازے اور آسان وضاحت دیتی ہے، مالی، طبی یا قانونی مشورہ نہیں۔",
    )
}
