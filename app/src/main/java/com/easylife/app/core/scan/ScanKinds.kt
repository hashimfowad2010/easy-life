package com.easylife.app.core.scan

import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.i18n.SScan
import com.easylife.app.core.i18n.Txt

/** What a scanned image can be, and which module handles it (Universal Scan routing). */
enum class ScanKind(val key: String, val label: Txt, val module: Module) {
    ELECTRICITY_BILL("electricity_bill", SScan.kindElectricity, Module.BILLS),
    GAS_BILL("gas_bill", SScan.kindGas, Module.BILLS),
    WATER_BILL("water_bill", SScan.kindWater, Module.BILLS),
    INTERNET_BILL("internet_bill", SScan.kindInternet, Module.BILLS),
    MOBILE_BILL("mobile_bill", SScan.kindMobile, Module.BILLS),
    SCHOOL_FEE("school_fee", SScan.kindSchool, Module.BILLS),
    OTHER_BILL("other_bill", SScan.kindOtherBill, Module.BILLS),
    METER("meter", SScan.kindMeter, Module.LIMITS),
    RECEIPT("receipt", SScan.kindReceipt, Module.BUDGET),
    PRICE_TAG("price_tag", SScan.kindPriceTag, Module.SHOPPING),
    DOCUMENT("document", SScan.kindDocument, Module.URDU),
    UNKNOWN("unknown", SScan.kindUnknown, Module.URDU),
    ;

    val isBill: Boolean get() = module == Module.BILLS

    /** The bill type stored for bills of this kind. */
    val billType: String get() = when (this) {
        ELECTRICITY_BILL -> "electricity"
        GAS_BILL -> "gas"
        WATER_BILL -> "water"
        INTERNET_BILL -> "internet"
        MOBILE_BILL -> "mobile_postpaid"
        SCHOOL_FEE -> "school_fee"
        else -> "other"
    }

    companion object {
        fun of(key: String?): ScanKind = entries.firstOrNull { it.key == key } ?: UNKNOWN

        /** Choices offered on the confirm screen (one tap to correct the guess). */
        val choices = listOf(ELECTRICITY_BILL, GAS_BILL, SCHOOL_FEE, INTERNET_BILL, OTHER_BILL, METER, RECEIPT, PRICE_TAG, DOCUMENT)
    }
}
