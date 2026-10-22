package com.easylife.app.core.ai.providers

import com.easylife.app.core.ai.AiError
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiProvider
import com.easylife.app.core.ai.AiResult
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Availability
import com.easylife.app.core.ai.ModelState
import com.easylife.app.core.ai.OnDeviceTranslator
import com.easylife.app.core.ai.ProviderId
import com.easylife.app.core.ai.schema.TranslateResult
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.ocr.OcrDocument
import com.easylife.app.core.ocr.OcrEngine
import com.easylife.app.core.parse.BillParser
import com.easylife.app.core.parse.DocInsights
import com.easylife.app.core.parse.ImageClassifier
import com.easylife.app.core.parse.MeterReadingParser
import com.easylife.app.core.parse.ReceiptParser
import com.easylife.app.core.parse.ShoppingListParser
import com.easylife.app.core.parse.SmsParser
import com.easylife.app.core.parse.WeeklySummaryTemplates
import javax.inject.Inject

/**
 * The default provider: free, offline, private. ML Kit OCR + rule-based parsers + ML Kit translation + templates.
 */
class OnDeviceProvider @Inject constructor(
    private val ocr: OcrEngine,
    private val translator: OnDeviceTranslator,
) : AiProvider {

    override val id = ProviderId.ON_DEVICE

    override fun supports(task: AiTask<*>): Boolean = true

    override suspend fun isAvailable(task: AiTask<*>): Availability = when (task) {
        AiTask.Translate -> {
            val state = translator.refreshState()
            if (state == ModelState.READY) Availability.Yes else Availability.no("Urdu translation model not downloaded yet")
        }
        else -> Availability.Yes
    }

    override suspend fun <R : Any> run(task: AiTask<R>, input: AiInput): AiResult<R> {
        val value: Any = when (task) {
            AiTask.ClassifyImage -> ImageClassifier.classify(document(input).plainText)
            AiTask.MeterRead -> MeterReadingParser.parse(
                document(input),
                MeterReadingParser.Context(
                    previousReading = input.params["previousReading"]?.toDoubleOrNull(),
                    daysSincePrevious = input.params["daysSincePrevious"]?.toDoubleOrNull(),
                    meterDigits = input.params["meterDigits"]?.toIntOrNull(),
                ),
            )
            AiTask.BillExtract -> BillParser.parse(document(input))
            AiTask.ReceiptExtract -> ReceiptParser.parse(document(input))
            AiTask.SmsParse -> SmsParser.parse(input.params["sender"].orEmpty(), input.text.orEmpty()).result
                ?: return AiResult.failure(id, AiError.NoResult)
            AiTask.Translate -> {
                val mode = input.params["mode"] ?: "normal"
                if (mode != "normal") return AiResult.failure(id, AiError.NotSupported)
                val from = AppLanguage.fromTag(input.params["from"]) ?: AppLanguage.EN
                val to = AppLanguage.fromTag(input.params["to"]) ?: AppLanguage.UR
                TranslateResult(translator.translate(input.text.orEmpty(), from, to), confidence = 0.7)
            }
            AiTask.DocExplain -> DocInsights.explain(input.text ?: document(input).rowText)
            AiTask.ListNormalize -> ShoppingListParser.parse(input.text.orEmpty())
            AiTask.WeeklySummary -> WeeklySummaryTemplates.build(input.params)
        }
        @Suppress("UNCHECKED_CAST")
        val typed = value as R
        val (confidence, uncertain) = confidenceOf(typed)
        if (task == AiTask.MeterRead || task == AiTask.BillExtract || task == AiTask.ReceiptExtract) {
            if (input.ocr == null && input.imagePaths.isEmpty() && input.text.isNullOrBlank()) {
                return AiResult.failure(id, AiError.NoResult)
            }
        }
        return AiResult(typed, confidence, uncertain, id)
    }

    private suspend fun document(input: AiInput): OcrDocument = when {
        input.ocr != null -> input.ocr
        input.imagePaths.isNotEmpty() -> ocr.recognizeAll(input.imagePaths)
        else -> OcrDocument.fromPlainText(input.text.orEmpty())
    }
}

/** Shared by providers: pull confidence/uncertain fields out of any task schema. */
internal fun confidenceOf(value: Any): Pair<Double, List<String>> = when (value) {
    is com.easylife.app.core.ai.schema.ClassifyResult -> value.confidence to value.uncertainFields
    is com.easylife.app.core.ai.schema.MeterReadResult -> value.confidence to value.uncertainFields
    is com.easylife.app.core.ai.schema.BillExtractResult -> value.confidence to value.uncertainFields
    is com.easylife.app.core.ai.schema.ReceiptExtractResult -> value.confidence to value.uncertainFields
    is com.easylife.app.core.ai.schema.SmsParseResult -> value.confidence to value.uncertainFields
    is TranslateResult -> value.confidence to emptyList()
    is com.easylife.app.core.ai.schema.DocExplainResult -> value.confidence to emptyList()
    is com.easylife.app.core.ai.schema.ListNormalizeResult -> value.confidence to emptyList()
    is com.easylife.app.core.ai.schema.WeeklySummaryResult -> value.confidence to emptyList()
    else -> 0.5 to emptyList()
}
