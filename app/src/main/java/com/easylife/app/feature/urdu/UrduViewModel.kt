package com.easylife.app.feature.urdu

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.ModelState
import com.easylife.app.core.ai.OnDeviceTranslator
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.data.db.DocumentDao
import com.easylife.app.core.data.db.ReminderEntity
import com.easylife.app.core.data.db.SavedDocumentEntity
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.ocr.OcrEngine
import com.easylife.app.core.parse.DocInsights
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.speech.Tts
import com.easylife.app.core.speech.VoiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

enum class TranslateMode(val key: String) { NORMAL("normal"), SIMPLE("simple"), ROMAN_TO_URDU("roman_to_urdu"), URDU_TO_ROMAN("urdu_to_roman") }

data class TextTabState(
    val fromUrdu: Boolean = false,
    val mode: TranslateMode = TranslateMode.NORMAL,
    val input: String = "",
    val output: String? = null,
    val source: Provenance? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

data class ScanTabState(
    val busy: Boolean = false,
    val text: String? = null,
    val imagePath: String? = null,
    val isSample: Boolean = false,
    val insights: DocExplainResult? = null,
    val insightsSource: Provenance? = null,
    val translation: String? = null,
    val savedId: Long? = null,
    val draftId: String? = null,
)

/** Shared by all three tabs so a scanned paper can be read aloud or translated without re-reading it. */
@HiltViewModel
class UrduViewModel @Inject constructor(
    private val translator: OnDeviceTranslator,
    private val router: AiRouter,
    private val drafts: ScanDraftStore,
    private val ocr: OcrEngine,
    private val documents: DocumentDao,
    private val tts: Tts,
    private val impact: ImpactTracker,
) : ViewModel() {

    val model: StateFlow<ModelState> = translator.state
    val speaking: StateFlow<Boolean> = tts.speaking

    private val _text = MutableStateFlow(TextTabState())
    val text = _text.asStateFlow()
    private val _scan = MutableStateFlow(ScanTabState())
    val scan = _scan.asStateFlow()
    private val _voice = MutableStateFlow<VoiceStatus?>(null)
    val voice = _voice.asStateFlow()
    private val _smartAvailable = MutableStateFlow(false)
    val smartAvailable = _smartAvailable.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var loadedDraft: String? = null

    init {
        viewModelScope.launch {
            translator.refreshState()
            // Smart AI modes are offered only if a cloud provider would actually run right now.
            _smartAvailable.value = router.plan(AiTask.DocExplain).candidates.any { it.isCloud }
        }
    }

    fun downloadModel(wifiOnly: Boolean) = viewModelScope.launch { runCatching { translator.download(wifiOnly) } }

    // ---- Text tab

    fun setInput(s: String) = _text.update { it.copy(input = s, output = null, failed = false) }
    fun swap() = _text.update { it.copy(fromUrdu = !it.fromUrdu, input = it.output ?: it.input, output = null) }
    fun setMode(m: TranslateMode) = _text.update { it.copy(mode = m, output = null) }

    fun translateText() = viewModelScope.launch {
        val s = _text.value
        if (s.input.isBlank()) return@launch
        _text.update { it.copy(busy = true, failed = false) }
        val from = if (s.fromUrdu) AppLanguage.UR else AppLanguage.EN
        val to = if (s.fromUrdu) AppLanguage.EN else AppLanguage.UR
        val r = router.run(AiTask.Translate, AiInput(text = s.input, params = mapOf("from" to from.tag, "to" to to.tag, "mode" to s.mode.key)))
        _text.update { it.copy(busy = false, output = r.value?.text, source = r.provenance.takeIf { r.ok }, failed = !r.ok) }
        if (r.ok) impact.log(ImpactEvents.DOC_TRANSLATED, "urdu", detail = "text")
    }

    /** From the Speak tab: put recognised speech in the text tab and translate it. */
    fun translateHeard(heard: String, spokenUrdu: Boolean) {
        _text.update { TextTabState(fromUrdu = spokenUrdu, input = heard) }
        translateText()
    }

    // ---- Scan & Read tab

    fun loadDraft(draftId: String) {
        if (loadedDraft == draftId) return
        loadedDraft = draftId
        viewModelScope.launch {
            val draft = drafts.get(draftId) ?: return@launch
            _scan.value = ScanTabState(busy = true, imagePath = draft.imagePaths.firstOrNull(), isSample = draft.isSample, draftId = draftId)
            var doc = draft.ocr
            if (doc == null && draft.imagePaths.isNotEmpty()) doc = runCatching { ocr.recognizeAll(draft.imagePaths) }.getOrNull()
            analyze(doc?.rowText ?: draft.text.orEmpty(), draft.imagePaths, draft.isSample)
        }
    }

    fun fromImages(uris: List<Uri>) = viewModelScope.launch { loadDraft(drafts.createFromImages(uris).id) }

    fun fromPastedText(t: String) = viewModelScope.launch { loadDraft(drafts.createFromText(t, null).id) }

    private suspend fun analyze(text: String, images: List<String>, isSample: Boolean) {
        if (text.isBlank()) {
            _scan.update { it.copy(busy = false, text = "") }
            return
        }
        val r = router.run(AiTask.DocExplain, AiInput(text = text, imagePaths = images))
        val insights = r.value ?: DocInsights.explain(text)
        impact.log(ImpactEvents.DOC_READ, "urdu", detail = insights.docType)
        val englishText = text.count { it in 'A'..'z' } > text.count { it in '؀'..'ۿ' }
        val translation = if (englishText && translator.refreshState() == ModelState.READY) {
            runCatching { translator.translate(text, AppLanguage.EN, AppLanguage.UR) }.getOrNull()?.also {
                impact.log(ImpactEvents.DOC_TRANSLATED, "urdu", detail = "document")
            }
        } else {
            null
        }
        _scan.update {
            it.copy(
                busy = false,
                text = text,
                insights = insights,
                insightsSource = if (isSample) Provenance.DEMO else r.provenance,
                translation = translation,
            )
        }
    }

    fun translateScanNow() = viewModelScope.launch {
        val t = _scan.value.text ?: return@launch
        if (translator.refreshState() != ModelState.READY) return@launch
        val out = runCatching { translator.translate(t, AppLanguage.EN, AppLanguage.UR) }.getOrNull()
        _scan.update { it.copy(translation = out) }
    }

    fun saveDocument(titleFallback: String) = viewModelScope.launch {
        val s = _scan.value
        val text = s.text ?: return@launch
        val insights = s.insights
        val title = text.lines().firstOrNull { it.trim().lowercase().startsWith("subject") }?.substringAfter(':')?.trim()
            ?: text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: titleFallback
        val id = documents.insert(
            SavedDocumentEntity(
                title = title,
                docType = insights?.docType ?: "other",
                sourceText = text,
                translatedText = s.translation,
                insightsJson = insights?.let { json.encodeToString(DocExplainResult.serializer(), it) },
                source = (s.insightsSource ?: Provenance.ON_DEVICE).name,
                isDemo = s.isSample,
            ),
        )
        s.draftId?.let { drafts.delete(it) }
        _scan.update { it.copy(savedId = id, draftId = null) }
    }

    fun addReminder(title: String, date: LocalDate) = viewModelScope.launch {
        documents.insertReminder(ReminderEntity(title = title.take(80), date = date, sourceDocId = _scan.value.savedId, isDemo = _scan.value.isSample))
    }

    /** Keep the draft for "Save as a bill" (the bill screen reads it). */
    fun draftForBill(): String? = _scan.value.draftId

    // ---- Read aloud

    fun speak(text: String, lang: AppLanguage) = viewModelScope.launch { _voice.value = tts.speak(text, lang) }
    fun stopSpeaking() = tts.stop()
    fun dismissVoiceGuide() {
        _voice.value = null
    }
    fun installVoiceIntent() = tts.installVoiceIntent()

    override fun onCleared() {
        tts.stop()
    }
}
