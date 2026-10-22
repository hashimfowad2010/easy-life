package com.easylife.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.data.db.ImpactDao
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.HorizontalBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SImpact
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.impact.FeedbackAnswer
import com.easylife.app.core.impact.FeedbackRepository
import com.easylife.app.core.impact.FeedbackResult
import com.easylife.app.core.impact.ImpactPdf
import com.easylife.app.core.impact.ImpactReport
import com.easylife.app.core.impact.ImpactSummary
import com.easylife.app.core.impact.highlightTiles
import com.easylife.app.core.impact.moduleLabel
import com.easylife.app.core.impact.sourceLabel
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

// ------------------------------------------------------------------------------------------------ Impact report

data class ImpactReportState(val summary: ImpactSummary? = null, val demo: Boolean = false)

@HiltViewModel
class ImpactReportViewModel @Inject constructor(
    private val dao: ImpactDao,
    private val prefs: PrefsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ImpactReportState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = prefs.current()
            val events = dao.getAll().map { ImpactReport.Event(it.event, it.module, it.value, it.detail, it.createdAt) }
            _state.value = ImpactReportState(ImpactReport.build(events, p.pilotCode, Instant.now()), p.demoMode)
        }
    }

    suspend fun saveCsv(uri: Uri): Boolean = write(uri) { out, s -> out.write(ImpactReport.csv(s).toByteArray()) }

    suspend fun savePdf(uri: Uri, lang: AppLanguage): Boolean = write(uri) { out, s -> ImpactPdf.write(context, s, lang, _state.value.demo, out) }

    private suspend fun write(uri: Uri, block: (java.io.OutputStream, ImpactSummary) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val s = _state.value.summary ?: return@withContext false
        runCatching { context.contentResolver.openOutputStream(uri)?.use { block(it, s) } ?: error("no stream") }.isSuccess
    }
}

@Composable
fun ImpactReportScreen(onBack: () -> Unit, vm: ImpactReportViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val savedText = SImpact.saved.t()
    val failedText = SImpact.saveFailed.t()
    fun report(ok: Boolean) = scope.launch { snackbar.showSnackbar(if (ok) savedText else failedText) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { report(vm.saveCsv(uri)) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { report(vm.savePdf(uri, lang)) }
    }
    Scaffold(topBar = { EasyTopBar(SImpact.reportTitle.t(), onBack = onBack) }) { padding ->
        val s = st.summary
        if (s == null) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(SImpact.reportIntro.t(), style = MaterialTheme.typography.bodyMedium)
            val zone = ZoneId.systemDefault()
            Text(
                listOfNotNull(
                    s.from?.let { SImpact.since.t(Fmt.date(it.atZone(zone).toLocalDate(), lang)) },
                    s.pilotCode?.let { SImpact.pilot.t(it) } ?: SImpact.noPilot.t(),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (st.demo) InfoBanner(SImpact.demoWarning.t(), BannerKind.WARNING)
            if (s.isEmpty) InfoBanner(SImpact.nothingYet.t())

            SectionHeader(SImpact.headline.t())
            highlightTiles(s.highlights, lang).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, value) ->
                        EasyCard(Modifier.weight(1f).heightIn(min = 96.dp), containerColor = EasyTheme.colors.limits.container.copy(alpha = 0.5f)) {
                            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(label.t(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (s.moduleActivity.isNotEmpty()) {
                SectionHeader(SImpact.perModule.t())
                val max = s.moduleActivity.values.max().coerceAtLeast(1)
                s.moduleActivity.forEach { (m, n) ->
                    HorizontalBar(label = moduleLabel(m).t(), valueText = Fmt.number(n), fraction = n.toFloat() / max, color = MaterialTheme.colorScheme.primary)
                }
            }

            val sources = s.sources.filter { it.results > 0 || it.confirmations > 0 }
            if (sources.isNotEmpty()) {
                SectionHeader(SImpact.bySource.t())
                Text(SImpact.bySourceNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val max = sources.maxOf { it.results }.coerceAtLeast(1)
                sources.forEach { src ->
                    val note = if (src.confirmations > 0) " · " + SImpact.corrected.t(src.correctedPercent) else ""
                    HorizontalBar(
                        label = sourceLabel(src.source).t(),
                        valueText = SImpact.results.t(src.results) + note,
                        fraction = src.results.toFloat() / max,
                        color = EasyTheme.colors.bills.accent,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            BigButton(SImpact.saveCsv.t(), icon = Icons.Rounded.TableChart, onClick = { csvLauncher.launch("easy-life-impact.csv") })
            BigOutlinedButton(SImpact.savePdf.t(), icon = Icons.Rounded.PictureAsPdf, onClick = { pdfLauncher.launch("easy-life-impact.pdf") })
        }
    }
}

// ------------------------------------------------------------------------------------------------ Feedback

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedback: FeedbackRepository,
    private val prefs: PrefsRepository,
) : ViewModel() {
    fun submit(answer: FeedbackAnswer, onDone: (FeedbackResult) -> Unit) = viewModelScope.launch { onDone(feedback.submit(answer)) }
    fun later(onDone: () -> Unit) = viewModelScope.launch {
        prefs.snoozeFeedback()
        onDone()
    }
}

private val helpedLabels = mapOf(
    "electricity" to SImpact.q2Electricity,
    "bills" to SImpact.q2Bills,
    "budget" to SImpact.q2Budget,
    "documents" to SImpact.q2Urdu,
    "shopping" to SImpact.q2Shopping,
    "other" to SImpact.q2Other,
)

@Composable
fun FeedbackScreen(onBack: () -> Unit, vm: FeedbackViewModel = hiltViewModel()) {
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val thanks = SImpact.thanks.t()
    val thanksLater = SImpact.thanksSavedLocally.t()
    var usefulness by remember { mutableStateOf<Int?>(null) }
    val helped = remember { mutableStateListOf<String>() }
    var changed by remember { mutableStateOf<Boolean?>(null) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Scaffold(topBar = { EasyTopBar(SImpact.feedbackTitle.t(), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(SImpact.feedbackIntro.t(), style = MaterialTheme.typography.bodyMedium)

            SectionHeader("1. " + SImpact.q1.t())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..5).forEach { n ->
                    FilterChip(
                        selected = usefulness == n,
                        onClick = { usefulness = n },
                        label = { Text(n.toString(), style = MaterialTheme.typography.titleLarge) },
                        modifier = Modifier.semantics { contentDescription = "$n / 5" },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(SImpact.q1Low.t(), style = MaterialTheme.typography.bodySmall)
                Text(SImpact.q1High.t(), style = MaterialTheme.typography.bodySmall)
            }

            SectionHeader("2. " + SImpact.q2.t())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeedbackAnswer.HELPED_KEYS.forEach { key ->
                    FilterChip(
                        selected = key in helped,
                        onClick = { if (key in helped) helped.remove(key) else helped.add(key) },
                        label = { Text(helpedLabels.getValue(key).t()) },
                    )
                }
            }

            SectionHeader("3. " + SImpact.q3.t())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = changed == true, onClick = { changed = true }, label = { Text(SImpact.yes.t()) })
                FilterChip(selected = changed == false, onClick = { changed = false }, label = { Text(SImpact.no.t()) })
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(500) },
                label = { Text(SImpact.comment.t()) },
                placeholder = { Text(SImpact.commentHint.t()) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(SImpact.anonymousNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            BigButton(
                SImpact.send.t(),
                enabled = usefulness != null && changed != null && !sending,
                onClick = {
                    sending = true
                    val answer = FeedbackAnswer(usefulness!!, helped.toList(), changed!!, comment, if (lang == AppLanguage.UR) "ur" else "en")
                    vm.submit(answer) { result ->
                        // Not the screen scope: it is cancelled as soon as the screen closes.
                        kotlinx.coroutines.MainScope().launch { snackbar.showSnackbar(if (result == FeedbackResult.SENT) thanks else thanksLater) }
                        onBack()
                    }
                },
            )
            TextButton(onClick = { vm.later(onBack) }) { Text(SImpact.later.t()) }
        }
    }
}
