package com.easylife.app.core.parse

/** One grocery item with its English/Urdu names and every way people type or say it. */
data class GroceryItem(
    val key: String,
    val nameEn: String,
    val nameUr: String,
    val category: String,
    val aliases: List<String>,
    /** Unit used when the list gives a bare number, e.g. "anday 12" -> 12 pieces. */
    val countUnit: String? = null,
)

/**
 * Bilingual grocery dictionary (English, Roman Urdu, Urdu script). Used by the on-device shopping-list parser and
 * as the shared item keys for community prices. Easy to extend: add a line.
 */
object GroceryDictionary {

    private fun item(key: String, en: String, ur: String, cat: String, vararg aliases: String, countUnit: String? = null) =
        GroceryItem(key, en, ur, cat, aliases.toList() + en.lowercase() + ur, countUnit)

    val items: List<GroceryItem> = listOf(
        // Staples
        item("sugar", "Sugar", "چینی", "staples", "cheeni", "chini", "cheenee", "shakar", "شکر"),
        item("flour", "Flour (atta)", "آٹا", "staples", "aata", "atta", "ata", "wheat flour", "chakki atta"),
        item("rice", "Rice", "چاول", "staples", "chawal", "chaawal", "chawel", "basmati", "sela", "rice"),
        item("lentils_masoor", "Red lentils (masoor)", "مسور کی دال", "staples", "masoor", "masoor daal", "masoor dal", "masur", "red lentils"),
        item("lentils_moong", "Moong lentils", "مونگ کی دال", "staples", "moong", "moong daal", "mung", "moong dal"),
        item("lentils_mash", "Mash lentils", "ماش کی دال", "staples", "mash", "mash daal", "maash", "urad"),
        item("lentils_chana", "Chana daal", "چنے کی دال", "staples", "chana daal", "chana dal", "chanay ki daal"),
        item("lentils", "Lentils (daal)", "دال", "staples", "daal", "dal", "dall"),
        item("chickpeas", "Chickpeas", "چنے", "staples", "chanay", "chanay", "kabuli chana", "safaid chanay", "chickpea"),
        item("cooking_oil", "Cooking oil", "کوکنگ آئل", "staples", "oil", "tel", "cooking oil", "تیل", "canola", "sunflower oil"),
        item("ghee", "Ghee", "گھی", "staples", "ghee", "banaspati", "desi ghee", "ghi"),
        item("salt", "Salt", "نمک", "staples", "namak", "salt"),
        item("tea", "Tea leaves", "چائے کی پتی", "staples", "chai", "chai patti", "chaye", "chaye ki patti", "patti", "پتی", "tea"),
        item("red_chilli", "Red chilli powder", "لال مرچ", "staples", "lal mirch", "laal mirch", "red chilli", "chilli powder"),
        item("turmeric", "Turmeric", "ہلدی", "staples", "haldi", "haldee"),
        item("cumin", "Cumin", "زیرہ", "staples", "zeera", "jeera", "zeerah"),
        item("garam_masala", "Garam masala", "گرم مصالحہ", "staples", "garam masala", "masala", "مصالحہ"),
        item("gram_flour", "Gram flour (besan)", "بیسن", "staples", "besan", "baisan"),
        item("maida", "Maida", "میدہ", "staples", "maida", "maidah", "white flour"),
        item("semolina", "Semolina (suji)", "سوجی", "staples", "suji", "sooji"),
        item("vermicelli", "Vermicelli", "سویاں", "staples", "sewaiyan", "sawaiyan", "seviyan"),
        item("bread", "Bread", "ڈبل روٹی", "staples", "double roti", "dabal roti", "bread", "roti"),
        item("rusk", "Rusk", "پاپے", "snacks", "rusk", "papay", "paape"),
        item("biscuits", "Biscuits", "بسکٹ", "snacks", "biscuit", "biscuits", "biskut"),
        item("noodles", "Noodles", "نوڈلز", "staples", "noodles", "maggi", "noodle"),
        item("honey", "Honey", "شہد", "staples", "shehad", "shahad", "honey"),
        item("vinegar", "Vinegar", "سرکہ", "staples", "sirka", "vinegar"),
        item("ketchup", "Ketchup", "کیچپ", "staples", "ketchup", "sauce", "chutney"),
        // Dairy & eggs
        item("milk", "Milk", "دودھ", "dairy", "doodh", "dudh", "milk", "olpers", "milkpak", "nesvita", countUnit = "packet"),
        item("yogurt", "Yogurt (dahi)", "دہی", "dairy", "dahi", "dahee", "yoghurt", "yogurt", "curd"),
        item("butter", "Butter", "مکھن", "dairy", "makhan", "makkhan", "butter"),
        item("cheese", "Cheese", "پنیر", "dairy", "cheese", "paneer"),
        item("cream", "Cream", "کریم", "dairy", "cream", "malai", "ملائی"),
        item("eggs", "Eggs", "انڈے", "dairy", "anday", "anda", "andey", "ande", "egg", "eggs", "انڈا", countUnit = "piece"),
        // Produce
        item("potato", "Potatoes", "آلو", "produce", "aloo", "alu", "aaloo", "potato", "potatoes"),
        item("onion", "Onions", "پیاز", "produce", "piyaz", "pyaz", "piaz", "onion", "onions"),
        item("tomato", "Tomatoes", "ٹماٹر", "produce", "tamatar", "timatar", "tomato", "tomatoes"),
        item("garlic", "Garlic", "لہسن", "produce", "lehsan", "lahsan", "lehson", "garlic"),
        item("ginger", "Ginger", "ادرک", "produce", "adrak", "adrakh", "ginger"),
        item("green_chilli", "Green chillies", "ہری مرچ", "produce", "hari mirch", "harri mirch", "green chilli", "green chillies"),
        item("lemon", "Lemons", "لیموں", "produce", "lemon", "lemons", "nimbu", "leemu", countUnit = "piece"),
        item("coriander", "Fresh coriander", "ہرا دھنیا", "produce", "dhania", "dhaniya", "hara dhania", "coriander"),
        item("mint", "Mint", "پودینہ", "produce", "podina", "pudina", "mint"),
        item("spinach", "Spinach", "پالک", "produce", "palak", "spinach"),
        item("cauliflower", "Cauliflower", "پھول گوبھی", "produce", "gobi", "phool gobi", "gobhi", "cauliflower"),
        item("cabbage", "Cabbage", "بند گوبھی", "produce", "band gobi", "cabbage"),
        item("peas", "Peas", "مٹر", "produce", "matar", "mattar", "peas"),
        item("carrot", "Carrots", "گاجر", "produce", "gajar", "gajjar", "carrot", "carrots"),
        item("cucumber", "Cucumber", "کھیرا", "produce", "kheera", "khira", "cucumber"),
        item("okra", "Okra (bhindi)", "بھنڈی", "produce", "bhindi", "bhindee", "okra", "lady finger"),
        item("banana", "Bananas", "کیلے", "produce", "kela", "kelay", "kele", "banana", "bananas", countUnit = "dozen"),
        item("apple", "Apples", "سیب", "produce", "saib", "seb", "apple", "apples"),
        item("mango", "Mangoes", "آم", "produce", "aam", "mango", "mangoes"),
        item("orange", "Oranges (kinnow)", "کینو", "produce", "kinno", "kinnow", "malta", "orange", "oranges", countUnit = "dozen"),
        item("guava", "Guava", "امرود", "produce", "amrood", "amrud", "guava"),
        // Meat
        item("chicken", "Chicken", "مرغی", "meat", "murghi", "murgi", "chicken", "chikan"),
        item("beef", "Beef", "گائے کا گوشت", "meat", "beef", "bara gosht", "gosht", "گوشت"),
        item("mutton", "Mutton", "بکرے کا گوشت", "meat", "mutton", "bakra", "chota gosht"),
        item("fish", "Fish", "مچھلی", "meat", "machli", "machhli", "fish"),
        // Household
        item("detergent", "Washing powder", "سرف", "household", "surf", "sarf", "washing powder", "detergent", "ariel", "surf excel", "bonus"),
        item("soap", "Soap", "صابن", "personal_care", "sabun", "saban", "soap", "lux", "safeguard", "lifebuoy"),
        item("dishwash", "Dishwash", "برتن دھونے کا صابن", "household", "dishwash", "bartan soap", "max", "lemon max"),
        item("tissue", "Tissues", "ٹشو", "household", "tissue", "tissues", "tissue paper"),
        item("toilet_cleaner", "Toilet cleaner", "ٹوائلٹ کلینر", "household", "harpic", "toilet cleaner"),
        item("bleach", "Bleach", "بلیچ", "household", "bleach", "clorox"),
        item("matches", "Matches", "ماچس", "household", "machis", "maachis", "matches"),
        item("garbage_bags", "Garbage bags", "کچرے کے تھیلے", "household", "garbage bags", "shopper", "shoppers"),
        // Personal care
        item("shampoo", "Shampoo", "شیمپو", "personal_care", "shampoo", "sunsilk", "head and shoulders"),
        item("toothpaste", "Toothpaste", "ٹوتھ پیسٹ", "personal_care", "toothpaste", "colgate", "manjan", "sensodyne"),
        item("toothbrush", "Toothbrush", "ٹوتھ برش", "personal_care", "toothbrush", "brush"),
        item("diapers", "Diapers", "پیمپر", "personal_care", "pampers", "diaper", "diapers"),
        // Beverages & snacks
        item("water", "Drinking water", "پینے کا پانی", "beverages", "pani", "paani", "mineral water", "water", "nestle water", "aquafina"),
        item("cold_drink", "Cold drink", "کولڈ ڈرنک", "beverages", "cold drink", "coke", "pepsi", "sprite", "7up", "dew", "fanta"),
        item("juice", "Juice", "جوس", "beverages", "juice", "jus", "nestle juice"),
        item("chips", "Chips", "چپس", "snacks", "chips", "lays", "kurkure"),
        item("chocolate", "Chocolate", "چاکلیٹ", "snacks", "chocolate", "dairy milk", "kitkat"),
    )

    /** Brands that identify an item on their own ("Dalda 5 litre" -> cooking oil, brand Dalda). */
    val brands: Map<String, String> = mapOf(
        "dalda" to "cooking_oil", "habib" to "cooking_oil", "sufi" to "cooking_oil", "eva" to "cooking_oil", "mezan" to "cooking_oil",
        "seasons" to "cooking_oil", "kisan" to "ghee", "tapal" to "tea", "lipton" to "tea", "supreme" to "tea", "vital" to "tea",
        "olpers" to "milk", "milkpak" to "milk", "nurpur" to "butter", "shan" to "garam_masala", "national" to "garam_masala",
        "ariel" to "detergent", "bonus" to "detergent", "lux" to "soap", "safeguard" to "soap", "lifebuoy" to "soap",
        "colgate" to "toothpaste", "harpic" to "toilet_cleaner", "pampers" to "diapers", "lays" to "chips", "nestle" to "milk",
        "guard" to "rice", "falak" to "rice", "kolson" to "noodles", "maggi" to "noodles", "peek freans" to "biscuits", "lu" to "biscuits",
    )

    private val byKey = items.associateBy { it.key }

    fun byKey(key: String): GroceryItem? = byKey[key]

    /** All (alias, item) pairs, longest alias first so "masoor daal" beats "daal". */
    val aliasIndex: List<Pair<String, GroceryItem>> by lazy {
        items.flatMap { item -> item.aliases.map { it.lowercase() to item } }.sortedByDescending { it.first.length }
    }
}
