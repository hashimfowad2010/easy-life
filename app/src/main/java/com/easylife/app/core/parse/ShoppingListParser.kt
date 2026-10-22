package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.ListItemResult
import com.easylife.app.core.ai.schema.ListNormalizeResult

/**
 * On-device `list_normalize`: "2 kg cheeni, 1 dozen anday, Dalda 5 litre, aadha kilo dahi" ->
 * [{Sugar, چینی, 2 kg}, {Eggs, انڈے, 1 dozen}, {Cooking oil, 5 l, brand Dalda}, {Yogurt, 0.5 kg}].
 */
object ShoppingListParser {

    private val splitter = Regex("""[,\n;،۔+]|\s+(?:aur|and|اور|or)\s+""", RegexOption.IGNORE_CASE)

    private val numberWords = mapOf(
        "aik" to 1.0, "ek" to 1.0, "one" to 1.0, "a" to 1.0, "an" to 1.0, "ایک" to 1.0,
        "do" to 2.0, "two" to 2.0, "دو" to 2.0,
        "teen" to 3.0, "three" to 3.0, "تین" to 3.0,
        "char" to 4.0, "chaar" to 4.0, "four" to 4.0, "چار" to 4.0,
        "panch" to 5.0, "paanch" to 5.0, "five" to 5.0, "پانچ" to 5.0,
        "chay" to 6.0, "chhe" to 6.0, "che" to 6.0, "six" to 6.0, "چھ" to 6.0,
        "saat" to 7.0, "seven" to 7.0, "سات" to 7.0,
        "aath" to 8.0, "eight" to 8.0, "آٹھ" to 8.0,
        "nau" to 9.0, "nine" to 9.0, "نو" to 9.0,
        "das" to 10.0, "ten" to 10.0, "دس" to 10.0,
        "barah" to 12.0, "twelve" to 12.0, "بارہ" to 12.0,
        "aadha" to 0.5, "adha" to 0.5, "half" to 0.5, "آدھا" to 0.5, "آدھی" to 0.5,
        "derh" to 1.5, "dairh" to 1.5, "ڈیڑھ" to 1.5,
        "dhai" to 2.5, "dhaai" to 2.5, "ڈھائی" to 2.5,
        "sawa" to 1.25, "سوا" to 1.25,
    )

    private val unitWords: List<Pair<Regex, String>> = listOf(
        Regex("""^(?:kg|kgs|kilo|kilos|kilogram|kilograms|کلو)$""") to "kg",
        Regex("""^(?:g|gm|gms|gram|grams|grm|گرام)$""") to "g",
        Regex("""^(?:l|ltr|ltrs|litre|litres|liter|liters|لیٹر)$""") to "l",
        Regex("""^(?:ml|mls)$""") to "ml",
        Regex("""^(?:dozen|darjan|darjen|dz|dzn|درجن)$""") to "dozen",
        Regex("""^(?:pc|pcs|piece|pieces|adad|عدد|nag)$""") to "piece",
        Regex("""^(?:packet|packets|pack|packs|pkt|paket|پیکٹ|dabba|dabbe|box|bottle|bottles|botal|bag|bags|tin|carton)$""") to "packet",
        Regex("""^(?:pao|paao|paav|پاؤ)$""") to "pao",
    )

    fun unitOf(word: String): String? {
        val w = word.lowercase().trim('.', ',')
        return unitWords.firstOrNull { it.first.matches(w) }?.second
    }

    fun parse(text: String): ListNormalizeResult {
        val segments = splitter.split(text).map { it.trim() }.filter { it.isNotBlank() }
        val items = segments.mapNotNull { parseSegment(it) }
        val recognized = items.count { it.recognized }
        val confidence = if (items.isEmpty()) 0.0 else 0.5 + 0.45 * recognized / items.size
        return ListNormalizeResult(items, confidence)
    }

    fun parseSegment(segment: String): ListItemResult? {
        var rest = " " + segment.lowercase().replace(Regex("""(\d)([a-z؀-ۿ])"""), "$1 $2") + " "
        var qty: Double? = null
        var unit: String? = null

        // Numbers ("2", "1.5", "½") and number words ("aadha", "do").
        Regex("""(?<![\w.])(\d+(?:\.\d+)?|½|¼)(?![\w.])""").find(rest)?.let { m ->
            qty = when (m.value) { "½" -> 0.5; "¼" -> 0.25; else -> m.value.toDoubleOrNull() }
            rest = rest.replaceRange(m.range, " ")
        }
        val tokens = rest.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val remaining = mutableListOf<String>()
        for (t in tokens) {
            val u = unitOf(t)
            when {
                u != null && unit == null -> unit = u
                qty == null && numberWords.containsKey(t) && !(t == "a" || t == "an") -> qty = numberWords[t]
                qty == null && (t == "a" || t == "an") -> qty = 1.0
                else -> remaining += t
            }
        }
        if (unit == "pao") {
            // "pao" is a quarter kilo.
            qty = (qty ?: 1.0) * 0.25
            unit = "kg"
        }
        val name = remaining.joinToString(" ").trim().trim('-', ':', '.')
        if (name.isBlank()) return null

        val brandEntry = GroceryDictionary.brands.entries.firstOrNull { (b, _) -> Regex("""(?<![a-z])${Regex.escape(b)}(?![a-z])""").containsMatchIn(name) }
        val match = findItem(name) ?: brandEntry?.let { GroceryDictionary.byKey(it.value) }
        val brand = brandEntry?.key?.replaceFirstChar { it.uppercase() }

        if (match == null) {
            val pretty = name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            return ListItemResult(
                nameEn = pretty,
                nameUr = name,
                itemKey = name.replace(Regex("""[^\p{L}\p{N}]+"""), "_").trim('_'),
                qty = qty ?: 1.0,
                unit = unit,
                brandPref = brand,
                category = "other",
                rawText = segment,
                recognized = false,
            )
        }
        val finalUnit = unit ?: if (qty != null) match.countUnit else null
        return ListItemResult(
            nameEn = match.nameEn,
            nameUr = match.nameUr,
            itemKey = match.key,
            qty = qty ?: 1.0,
            unit = finalUnit,
            brandPref = brand,
            category = match.category,
            rawText = segment,
            recognized = true,
        )
    }

    private fun findItem(name: String): GroceryItem? {
        val padded = " $name "
        for ((alias, item) in GroceryDictionary.aliasIndex) {
            if (Regex("""(?<![\p{L}])${Regex.escape(alias)}(?![\p{L}])""").containsMatchIn(padded)) return item
        }
        // Small typos: "cheni", "tamater".
        val words = name.split(" ").filter { it.length >= 4 }
        for ((alias, item) in GroceryDictionary.aliasIndex) {
            if (alias.length < 4 || alias.contains(' ')) continue
            if (words.any { Labels.editDistance(it, alias) <= 1 }) return item
        }
        return null
    }
}
