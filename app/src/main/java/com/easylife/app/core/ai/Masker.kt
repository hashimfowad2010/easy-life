package com.easylife.app.core.ai

/**
 * On-device masking (spec rule 4). Runs before ANY text leaves the phone.
 * Hides digit runs that look like account, card, CNIC, IBAN, reference or phone numbers.
 * Keeps amounts ("Rs 1,250.00") and dates ("22-10-2026", "22 OCT 26") so the AI can still do its job.
 */
object Masker {
    const val MASK = "[MASKED]"

    data class Result(val text: String, val maskedCount: Int)

    private val idPatterns: List<Regex> = listOf(
        // IBAN, with or without spaces: PK36 SCBL 0000 0011 2345 6702
        Regex("""\bPK\s?\d{2}\s?[A-Z]{4}(?:\s?\d{4}){4}\b""", RegexOption.IGNORE_CASE),
        // CNIC 35202-1234567-1 (also without dashes, caught by long runs below)
        Regex("""\b\d{5}-\d{7}-\d\b"""),
        // Card numbers in groups: 4111 1111 1111 1111 / 4111-1111-1111-1111
        Regex("""\b\d{4}[ -]\d{4}[ -]\d{4}[ -]\d{1,7}\b"""),
        // LESCO-style reference numbers: 11 11111 1111111 (optionally followed by a letter like U / R)
        Regex("""\b\d{2}\s\d{5}\s\d{7}(?:\s?[A-Z])?\b"""),
        // Pakistani mobile numbers: +92 300 1234567, 0300-1234567, 03001234567
        Regex("""(?<![\d])(?:\+92|0092|0)\s?3\d{2}[\s-]?\d{7}\b"""),
        // Landlines with area code: 042-35761234
        Regex("""\b0\d{2,3}-\d{7,8}\b"""),
        // Any contiguous run of 11+ digits (accounts, cards, references, CNIC without dashes)
        Regex("""\d{11,}"""),
        // Email addresses
        Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""),
    )

    /** Mask identifiers before sending text to a cloud AI provider. */
    fun forCloud(text: String): Result = maskIds(text)

    /**
     * Stronger masking for "Copy masked OCR text" in Parser Lab: also hides names and addresses that follow
     * typical labels (NAME, ADDRESS, S/O, D/O, W/O, Mr, Mrs, Consumer Name).
     */
    fun forSharing(text: String): Result {
        var count = 0
        val lines = text.lines().map { line ->
            val m = nameLabel.find(line)
            if (m != null && line.substring(m.range.last + 1).isNotBlank()) {
                count++
                line.substring(0, m.range.last + 1) + " " + MASK
            } else {
                line
            }
        }
        val ids = maskIds(lines.joinToString("\n"))
        return Result(ids.text, ids.maskedCount + count)
    }

    private val nameLabel = Regex(
        // The lookahead stops "MRP 250" (a price tag) from being read as "Mr".
        """^\s*(?:consumer\s+name|name\s*(?:&|and)\s*address|customer\s+name|student(?:'s)?\s+name|father(?:'s)?\s+name|name|address|s/o|d/o|w/o|mrs\.?|mr\.?|ms\.?)(?=[\s:.\-]|$)\s*[:.\-]?""",
        RegexOption.IGNORE_CASE,
    )

    private fun maskIds(text: String): Result {
        var out = text
        var count = 0
        for (p in idPatterns) {
            out = p.replace(out) { count++; MASK }
        }
        return Result(out, count)
    }
}
