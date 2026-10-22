package com.easylife.app.core.parse

import com.easylife.app.core.ai.schema.DocAction
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.ai.schema.KeyDetail
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import java.time.LocalDate

/**
 * On-device `doc_explain`: finds dates, deadlines, amounts, phone numbers and "things to do" with rules, and
 * writes short Urdu action lines from templates. (A real summary needs Smart AI; the full text is translated by
 * ML Kit separately.)
 */
object DocInsights {

    private val medicalWords = listOf(
        "prescription", "tablet", "capsule", "syrup", "dose", "dosage", "injection", "pharmacy", "diagnosis", "patient",
        "hospital", "clinic", "lab report", "blood test", "medicine", "rx",
    )
    private val legalWords = listOf(
        "agreement", "court", "affidavit", "stamp paper", "power of attorney", "tenant", "landlord", "lease", "plaintiff",
        "defendant", "advocate", "hereby", "legal notice", "witness", "section ",
    )

    fun docType(text: String): String {
        val t = text.lowercase()
        fun any(words: List<String>) = words.count { t.contains(it) }
        return when {
            any(medicalWords) >= 2 -> "medical"
            any(legalWords) >= 2 -> "legal"
            any(listOf("parents", "students", "principal", "school", "class")) >= 2 && any(listOf("circular", "dear parents", "notice", "inform")) >= 1 -> "school_circular"
            BillParser.detect(text).billType != "other" && (t.contains("due date") || t.contains("payable")) -> "bill"
            any(listOf("application form", "fill in", "signature", "form no", "applicant")) >= 1 -> "form"
            t.contains("notice") -> "notice"
            t.contains("dear") && (t.contains("regards") || t.contains("sincerely")) -> "letter"
            else -> "other"
        }
    }

    fun isMedicalOrLegal(docType: String) = docType == "medical" || docType == "legal"

    private val phoneRegex = Regex("""(?<!\d)(?:\+92[\s-]?|0)(?:3\d{2}[\s-]?\d{7}|\d{2,3}[\s-]?\d{7,8})(?!\d)""")
    private val timeRegex = Regex("""\b\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)(?:\s*(?:to|-|till|until)\s*\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.))?""", RegexOption.IGNORE_CASE)
    private val deadlineWords = Regex("""\b(?:last\s+date|deadline|due\s+date|before|by|till|until|not\s+later\s+than|on\s+or\s+before)\b""", RegexOption.IGNORE_CASE)
    private val eventWords = Regex("""\b(?:held|will\s+be|scheduled|starts?|begins?|reopen|exam|meeting|holiday|closed)\b""", RegexOption.IGNORE_CASE)
    private val actionWords = Regex(
        """\b(?:must|please|kindly|required|requested|submit|pay|payable|bring|wear|sign|attend|clear|deposit|contact|call|return|send|collect|ensure)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val amountInText = Regex("""(?:Rs\.?|PKR|Rupees)\s?([\d,]+(?:\.\d{1,2})?)(?:\s*/-)?""", RegexOption.IGNORE_CASE)

    /** Split into sentences without breaking after "Rs.", "No.", "Dr." etc. */
    fun sentences(text: String): List<String> =
        text.replace("\r", "")
            .split(Regex("""(?<=[.!?])(?<!\b(?:Rs|No|Dr|Mr|Mrs|Ms|St|vs|etc|Ref)\.)\s+(?=[A-Z0-9])|\n+"""))
            .map { it.trim() }
            .filter { it.length > 2 }

    fun explain(text: String): DocExplainResult {
        val type = docType(text)
        val details = mutableListOf<KeyDetail>()
        val actions = mutableListOf<DocAction>()

        for (s in sentences(text)) {
            val dates = Dates.findAll(s)
            for (d in dates) {
                val isDeadline = deadlineWords.containsMatchIn(s)
                val isEvent = !isDeadline && eventWords.containsMatchIn(s)
                val (labelUr, labelEn, kind) = when {
                    isDeadline -> Triple("آخری تاریخ", "Deadline", "deadline")
                    isEvent -> Triple("تقریب / دن", "Event date", "event")
                    else -> Triple("تاریخ", "Date", "date")
                }
                // ISO value so the UI can format it and offer "Add date to reminders".
                details += KeyDetail(labelUr, d.value.toString(), labelEn, kind)
            }
            for (m in amountInText.findAll(s)) {
                val lower = s.lowercase()
                val (labelUr, labelEn) = when {
                    lower.contains("late fee") || lower.contains("fine") || lower.contains("penalty") -> "جرمانہ" to "Late fee / fine"
                    lower.contains("fee") -> "فیس" to "Fee"
                    lower.contains("total") || lower.contains("payable") -> "کل رقم" to "Total payable"
                    else -> "رقم" to "Amount"
                }
                Num.parseAmount(m.groupValues[1])?.let { details += KeyDetail(labelUr, it.toString(), labelEn, "amount") }
            }
            phoneRegex.findAll(s).forEach { details += KeyDetail("فون نمبر", it.value, "Phone", "phone") }
            timeRegex.findAll(s).forEach { details += KeyDetail("وقت", it.value, "Time", "time") }

            if (actionWords.containsMatchIn(s)) {
                val due = dates.firstOrNull()?.value
                val amount = amountInText.find(s)?.groupValues?.get(1)?.let(Num::parseAmount)
                actions += DocAction(
                    textUr = urduAction(s, amount, due),
                    dueDate = due?.toString(),
                    amount = amount,
                    textEn = s.take(200),
                )
            }
        }

        val subject = text.lines().firstOrNull { it.trim().lowercase().startsWith("subject") }?.substringAfter(':')?.trim()
        val summaryEn = buildList {
            if (subject != null) add(subject)
            if (isMedicalOrLegal(type)) add(0, "This is a simple explanation, not advice. Please confirm with a doctor, pharmacist or lawyer.")
        }
        val summaryUr = buildList {
            if (isMedicalOrLegal(type)) add("یہ صرف آسان وضاحت ہے، مشورہ نہیں۔ براہ کرم ڈاکٹر، فارماسسٹ یا وکیل سے تصدیق کریں۔")
        }
        return DocExplainResult(
            docType = type,
            summaryUr = summaryUr,
            actions = actions.distinctBy { it.textEn },
            keyDetails = details.distinctBy { it.kind to it.value },
            summaryEn = summaryEn,
            confidence = 0.6,
        )
    }

    /** Short Urdu instruction from simple templates, e.g. "ادائیگی کریں: Rs 1,500 — 31 اکتوبر 2026 سے پہلے". */
    fun urduAction(sentence: String, amount: Double?, due: LocalDate?): String {
        val s = sentence.lowercase()
        val verb = when {
            Regex("""\b(?:pay|payable|fee|dues|clear|deposit)\b""").containsMatchIn(s) -> "ادائیگی کریں"
            Regex("""\b(?:submit|return|send)\b""").containsMatchIn(s) -> "جمع کروائیں"
            Regex("""\bbring\b""").containsMatchIn(s) -> "ساتھ لائیں"
            Regex("""\bwear\b""").containsMatchIn(s) -> "یہ پہن کر آئیں"
            Regex("""\bsign\b""").containsMatchIn(s) -> "دستخط کریں"
            Regex("""\b(?:attend|held|meeting)\b""").containsMatchIn(s) -> "شرکت کریں"
            Regex("""\b(?:contact|call)\b""").containsMatchIn(s) -> "رابطہ کریں"
            else -> "یہ کام کریں"
        }
        val ur = AppLanguage.UR
        return buildString {
            append(verb)
            if (amount != null) append(": ").append(Fmt.rupees(amount, ur))
            if (due != null) append(" — ").append(Fmt.date(due, ur)).append(if (deadlineWords.containsMatchIn(sentence)) " سے پہلے" else "")
        }
    }
}
