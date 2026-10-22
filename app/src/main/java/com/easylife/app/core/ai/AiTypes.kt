package com.easylife.app.core.ai

import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.ai.schema.ClassifyResult
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.ai.schema.ListNormalizeResult
import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.ai.schema.SmsParseResult
import com.easylife.app.core.ai.schema.TranslateResult
import com.easylife.app.core.ai.schema.WeeklySummaryResult
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.ocr.OcrDocument
import kotlinx.serialization.KSerializer

enum class ProviderId(val key: String) {
    ON_DEVICE("on_device"),
    GEMINI_FREE("gemini_free"),
    CLAUDE("claude"),
    MOCK("mock"),
    ;

    val isCloud: Boolean get() = this == GEMINI_FREE || this == CLAUDE

    companion object {
        fun fromKey(key: String): ProviderId? = entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

/**
 * A typed AI task. Every provider must return the same result type [R] (the task schema).
 * [sensitive] tasks carry financial data and are NEVER sent to a free-tier provider (spec rule 5).
 */
sealed class AiTask<R : Any>(
    val key: String,
    val serializer: KSerializer<R>,
    val usesImage: Boolean,
    val sensitive: Boolean = false,
) {
    data object ClassifyImage : AiTask<ClassifyResult>("classify_image", ClassifyResult.serializer(), usesImage = true)
    data object MeterRead : AiTask<MeterReadResult>("meter_read", MeterReadResult.serializer(), usesImage = true)
    data object BillExtract : AiTask<BillExtractResult>("bill_extract", BillExtractResult.serializer(), usesImage = true)
    data object ReceiptExtract : AiTask<ReceiptExtractResult>("receipt_extract", ReceiptExtractResult.serializer(), usesImage = true)
    data object SmsParse : AiTask<SmsParseResult>("sms_parse", SmsParseResult.serializer(), usesImage = false, sensitive = true)
    data object Translate : AiTask<TranslateResult>("translate", TranslateResult.serializer(), usesImage = false)
    data object DocExplain : AiTask<DocExplainResult>("doc_explain", DocExplainResult.serializer(), usesImage = true)
    data object ListNormalize : AiTask<ListNormalizeResult>("list_normalize", ListNormalizeResult.serializer(), usesImage = false)
    data object WeeklySummary : AiTask<WeeklySummaryResult>("weekly_summary", WeeklySummaryResult.serializer(), usesImage = false)

    companion object {
        val all: List<AiTask<*>> by lazy {
            listOf(ClassifyImage, MeterRead, BillExtract, ReceiptExtract, SmsParse, Translate, DocExplain, ListNormalize, WeeklySummary)
        }

        fun byKey(key: String): AiTask<*>? = all.firstOrNull { it.key == key }
    }
}

/** Input to a task. On-device providers use [ocr]/[text]; cloud vision providers use [imagePaths]. */
data class AiInput(
    val text: String? = null,
    val ocr: OcrDocument? = null,
    /** Absolute file paths or content:// URIs of page images. */
    val imagePaths: List<String> = emptyList(),
    /** Task-specific parameters, e.g. previousReading, from/to/mode for translate. */
    val params: Map<String, String> = emptyMap(),
    val lang: AppLanguage = AppLanguage.EN,
)

sealed interface AiError {
    val message: String

    data object RateLimited : AiError { override val message = "rate limited (429)" }
    data object Timeout : AiError { override val message = "timed out" }
    data object Network : AiError { override val message = "no internet" }
    data object NotSupported : AiError { override val message = "task not supported" }
    data object NoResult : AiError { override val message = "nothing found" }
    data class InvalidJson(val detail: String) : AiError { override val message = "invalid JSON: $detail" }
    data class Unavailable(val reason: String) : AiError { override val message = reason }
    data class Failed(val detail: String) : AiError { override val message = detail }
}

data class AiAttempt(val provider: ProviderId, val error: AiError?, val millis: Long)

data class AiResult<T>(
    val value: T?,
    val confidence: Double,
    val uncertainFields: List<String>,
    val source: ProviderId,
    val error: AiError? = null,
    val attempts: List<AiAttempt> = emptyList(),
) {
    val ok: Boolean get() = value != null && error == null
    val provenance: Provenance get() = Provenance.of(source)

    companion object {
        fun <T> failure(source: ProviderId, error: AiError): AiResult<T> =
            AiResult(null, 0.0, emptyList(), source, error)
    }
}

data class Availability(val available: Boolean, val reason: String) {
    companion object {
        val Yes = Availability(true, "ready")
        fun no(reason: String) = Availability(false, reason)
    }
}

interface AiProvider {
    val id: ProviderId
    fun supports(task: AiTask<*>): Boolean
    suspend fun isAvailable(task: AiTask<*>): Availability
    suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R>
}
