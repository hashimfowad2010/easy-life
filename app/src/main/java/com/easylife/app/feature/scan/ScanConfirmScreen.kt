package com.easylife.app.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.ModuleIcon
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.designsystem.title
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SScan
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.ocr.OcrEngine
import com.easylife.app.core.scan.ScanDraft
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.scan.ScanKind
import com.easylife.app.navigation.AddReadingRoute
import com.easylife.app.navigation.BillConfirmRoute
import com.easylife.app.navigation.route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanConfirmState(
    val loading: Boolean = true,
    val draft: ScanDraft? = null,
    val kind: ScanKind? = null,
    val alternatives: List<ScanKind> = emptyList(),
    val provenance: Provenance? = null,
    val nothingRead: Boolean = false,
    val missing: Boolean = false,
    val receipt: ReceiptExtractResult? = null,
)

@HiltViewModel
class ScanConfirmViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val ocr: OcrEngine,
    private val router: AiRouter,
    private val impact: ImpactTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanConfirmState())
    val state = _state.asStateFlow()
    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            var draft = drafts.get(id) ?: run {
                _state.value = ScanConfirmState(loading = false, missing = true)
                return@launch
            }
            if (draft.ocr == null && draft.imagePaths.isNotEmpty()) {
                val doc = runCatching { ocr.recognizeAll(draft.imagePaths) }.getOrNull()
                draft = draft.copy(ocr = doc)
                drafts.save(draft)
            }
            val doc = draft.ocr
            if ((doc == null || doc.isEmpty) && draft.imagePaths.isEmpty()) {
                _state.value = ScanConfirmState(loading = false, draft = draft, nothingRead = true)
                return@launch
            }
            val result = router.run(AiTask.ClassifyImage, AiInput(text = doc?.plainText, ocr = doc, imagePaths = draft.imagePaths))
            val kind = result.value?.let { ScanKind.of(it.kind) } ?: ScanKind.UNKNOWN
            val alternatives = result.value?.alternatives.orEmpty().map { ScanKind.of(it) }
            draft = draft.copy(kind = kind.key, kindSource = result.source.key)
            drafts.save(draft)
            impact.log(ImpactEvents.SCAN_DONE, "scan", detail = kind.key)
            _state.value = ScanConfirmState(
                loading = false,
                draft = draft,
                kind = kind,
                alternatives = alternatives.filter { it != kind && it != ScanKind.UNKNOWN },
                provenance = if (draft.isSample) Provenance.DEMO else result.provenance,
                nothingRead = doc == null || doc.isEmpty,
            )
            if (kind == ScanKind.RECEIPT || kind == ScanKind.PRICE_TAG) readReceipt()
        }
    }

    fun choose(kind: ScanKind) {
        _state.update { it.copy(kind = kind, receipt = null) }
        viewModelScope.launch {
            _state.value.draft?.let { drafts.save(it.copy(kind = kind.key, kindSource = "user")) }
            if (kind == ScanKind.RECEIPT || kind == ScanKind.PRICE_TAG) readReceipt()
        }
    }

    private suspend fun readReceipt() {
        val d = _state.value.draft ?: return
        val r = router.run(AiTask.ReceiptExtract, AiInput(text = d.ocr?.plainText, ocr = d.ocr, imagePaths = d.imagePaths))
        _state.update { it.copy(receipt = r.value) }
    }
}

@Composable
fun ScanConfirmScreen(draftId: String, nav: NavHostController, vm: ScanConfirmViewModel = hiltViewModel()) {
    LaunchedEffect(draftId) { vm.load(draftId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    Scaffold(topBar = { EasyTopBar(SScan.title.t(), onBack = { nav.popBackStack() }) }) { padding ->
        if (s.loading) {
            LoadingBox(SScan.reading.t(), Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val draft = s.draft
            if (s.missing || draft == null) {
                InfoBanner(SScan.nothingRead.t(), BannerKind.WARNING)
                return@Column
            }
            draft.imagePaths.firstOrNull()?.let { PagePreview(it) }
            if (draft.isSample) InfoBanner(SScan.sampleNote.t(), BannerKind.INFO)
            if (draft.imagePaths.isEmpty() && draft.text != null) {
                EasyCard {
                    Text(draft.text.take(500), style = MaterialTheme.typography.bodySmall, maxLines = 10, modifier = Modifier.heightIn(max = 220.dp))
                }
            }
            if (s.nothingRead) InfoBanner(SScan.nothingRead.t(), BannerKind.WARNING)

            val kind = s.kind ?: ScanKind.UNKNOWN
            EasyCard {
                Text(SScan.looksLike.t(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModuleIcon(kind.module, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kind.label.t(), style = MaterialTheme.typography.titleLarge)
                        Text(SScan.goesTo.t(kind.module.title.t()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                s.provenance?.let {
                    Spacer(Modifier.height(10.dp))
                    SourceChip(it)
                }
            }
            Text(SScan.isThisRight.t(), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (s.alternatives + ScanKind.choices).distinct().forEach { k ->
                    FilterChip(selected = k == kind, onClick = { vm.choose(k) }, label = { Text(k.label.t()) })
                }
            }

            s.receipt?.let { r ->
                EasyCard {
                    Text(SScan.receiptComingTitle.t(), style = MaterialTheme.typography.titleMedium)
                    r.storeName?.let { LabeledValue(SCommon.name.t(), it) }
                    r.items.take(12).forEach { item ->
                        LabeledValue(item.name, item.lineTotal?.let { Fmt.rupees(it, lang) } ?: "—")
                    }
                    r.total?.let { LabeledValue(SCommon.total.t(), Fmt.rupees(it, lang)) }
                    Text(
                        SComingReceipt.t(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            BigButton(SCommon.continueText.t(), icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = {
                when {
                    kind.isBill -> nav.navigate(BillConfirmRoute(draftId = draft.id, kind = kind.key))
                    kind == ScanKind.METER -> nav.navigate(AddReadingRoute(meterId = 0, draftId = draft.id))
                    kind == ScanKind.RECEIPT || kind == ScanKind.PRICE_TAG -> nav.navigate(Module.BUDGET.route())
                    else -> nav.navigate(Module.URDU.route())
                }
            })
            BigOutlinedButton(SScan.typeInstead.t(), icon = Icons.Rounded.Edit, onClick = {
                nav.navigate(BillConfirmRoute(draftId = null, kind = if (kind.isBill) kind.key else ScanKind.OTHER_BILL.key))
            })
        }
    }
}

private val SComingReceipt = com.easylife.app.core.i18n.Txt(
    "Saving receipts to Budget and sharing shop prices arrive with the Budget and Shopping modules.",
    "رسید کو بجٹ میں محفوظ کرنا اور دکان کی قیمتیں بانٹنا بجٹ اور شاپنگ حصوں کے ساتھ آئے گا۔",
)
