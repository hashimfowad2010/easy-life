package com.easylife.app.core.i18n

object SNotify {
    val chLimits = Txt("Electricity limit warnings", "بجلی کی حد کی وارننگ")
    val chBills = Txt("Bill reminders", "بل کی یاد دہانی")
    val chBudget = Txt("Budget alerts", "بجٹ الرٹ")
    val chMeter = Txt("Meter photo reminders", "میٹر کی تصویر کی یاد دہانی")

    val watchTitle = Txt("Electricity: getting close", "بجلی: حد قریب ہے")
    val watchBody = Txt(
        "At this pace you'll reach %1\$s units by %2\$s. Keep to %3\$s units a day to stay under %4\$s.",
        "اسی رفتار سے %2\$s تک %1\$s یونٹ ہو جائیں گے۔ %4\$s سے کم رہنے کے لیے روزانہ %3\$s یونٹ تک رکھیں۔",
    )
    val dangerTitle = Txt("Warning: you may cross %1\$s units", "خبردار: %1\$s یونٹ پار ہو سکتے ہیں")
    val dangerBody = Txt(
        "At this pace you'll cross %1\$s units around %2\$s. Keep to %3\$s units a day to stay protected.",
        "اسی رفتار سے تقریباً %2\$s کو %1\$s یونٹ پار ہو جائیں گے۔ بچنے کے لیے روزانہ %3\$s یونٹ تک رکھیں۔",
    )
    val crossedTitle = Txt("You have crossed %1\$s units", "آپ %1\$s یونٹ پار کر چکے ہیں")
    val crossedBody = Txt(
        "This cycle is over the limit. Open Easy Life to see what it means and how to come back under it.",
        "اس مہینے حد پار ہو گئی ہے۔ آسان زندگی کھول کر دیکھیں اس کا کیا مطلب ہے اور واپس کیسے آئیں۔",
    )
    val meterPhotoTitle = Txt("Time to snap your meter", "میٹر کی تصویر لینے کا وقت")
    val meterPhotoBody = Txt(
        "Last reading was %1\$s days ago. A fresh reading keeps the warning accurate.",
        "پچھلی ریڈنگ %1\$s دن پہلے تھی۔ نئی ریڈنگ سے وارننگ درست رہتی ہے۔",
    )
    val billDueTitle = Txt("%1\$s bill due %2\$s", "%1\$s کا بل %2\$s جمع کروائیں")
    val billDueBody = Txt("Pay %1\$s by %2\$s to avoid the late fee of %3\$s.", "%2\$s تک %1\$s ادا کریں تاکہ %3\$s جرمانہ نہ لگے۔")
    val billDueBodyNoFee = Txt("Pay %1\$s by %2\$s.", "%2\$s تک %1\$s ادا کریں۔")
    val billOverdueTitle = Txt("%1\$s bill is overdue", "%1\$s کا بل لیٹ ہو گیا ہے")
    val budgetTitle = Txt("%1\$s budget %2\$s used", "%1\$s کا بجٹ %2\$s استعمال ہو چکا")
    val budgetBody = Txt("%1\$s left for %2\$s days. Keep to %3\$s a day.", "%2\$s دن کے لیے %1\$s باقی ہیں۔ روزانہ %3\$s تک رکھیں۔")
    val reminderTitle = Txt("Reminder: %1\$s", "یاد دہانی: %1\$s")
    val reminderToday = Txt("Today is the date you saved.", "آج وہ تاریخ ہے جو آپ نے محفوظ کی تھی۔")
}
