package com.easylife.app.core.devtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiResult
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Masker
import com.easylife.app.core.ai.RoutePlan
import com.easylife.app.core.cloud.RemoteConfigRepository
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.SettingsNavRow
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SDev
import com.easylife.app.core.i18n.SScan
import com.easylife.app.core.i18n.t
import com.easylife.app.core.ocr.OcrDocument
import com.easylife.app.core.ocr.OcrEngine
import com.easylife.app.core.parse.SmsParser
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.scan.rememberScanLaunchers
import com.easylife.app.navigation.ErrorReportRoute
import com.easylife.app.navigation.LocalSnackbar
import com.easylife.app.navigation.ParserLabRoute
import com.easylife.app.navigation.RouterViewRoute
import com.easylife.app.navigation.SmsLabRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import javax.inject.Inject

private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Easy Life", text))
}

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = modifier)
    }
}

@Composable
fun DevToolsScreen(nav: NavHostController) {
    Scaffold(topBar = { EasyTopBar(SDev.title.t(), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding)) {
            SettingsNavRow(SDev.parserLab.t(), SDev.parserLabNote.t(), Icons.Rounded.Science) { nav.navigate(ParserLabRoute) }
            SettingsNavRow(SDev.smsLab.t(), SDev.smsLabNote.t(), Icons.Rounded.Sms) { nav.navigate(SmsLabRoute) }
            SettingsNavRow(SDev.errorReport.t(), SDev.errorReportNote.t(), Icons.Rounded.BugReport) { nav.navigate(ErrorReportRoute) }
            SettingsNavRow(SDev.routerView.t(), SDev.routerViewNote.t(), Icons.Rounded.Hub) { nav.navigate(RouterViewRoute) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Parser Lab

data class LabState(
    val busy: Boolean = false,
    val imagePath: String? = null,
    val ocr: OcrDocument? = null,
    val task: AiTask<*> = AiTask.BillExtract,
    val result: String? = null,
    val source: AiResult<*>? = null,
)

@HiltViewModel
class ParserLabViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val ocrEngine: OcrEngine,
    private val router: AiRouter,
    private val errorLog: ErrorLog,
) : ViewModel() {
    private val _state = MutableStateFlow(LabState())
    val state = _state.asStateFlow()

    val tasks: List<AiTask<*>> = listOf(AiTask.BillExtract, AiTask.MeterRead, AiTask.ReceiptExtract, AiTask.ClassifyImage, AiTask.DocExplain, AiTask.ListNormalize)

    fun fromImages(uris: List<android.net.Uri>) = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true, result = null)
        val draft = drafts.createFromImages(uris)
        val doc = runCatching { ocrEngine.recognizeAll(draft.imagePaths) }.onFailure { errorLog.record("parser-lab-ocr", it) }.getOrNull()
        _state.value = _state.value.copy(busy = false, imagePath = draft.imagePaths.firstOrNull(), ocr = doc)
        run()
    }

    fun fromText(text: String) {
        _state.value = _state.value.copy(imagePath = null, ocr = OcrDocument.fromPlainText(text), result = null)
        run()
    }

    fun setTask(task: AiTask<*>) {
        _state.value = _state.value.copy(task = task)
        run()
    }

    fun run() = viewModelScope.launch {
        val s = _state.value
        val doc = s.ocr ?: return@launch
        _state.value = s.copy(busy = true)
        val input = AiInput(text = doc.plainText, ocr = doc, imagePaths = listOfNotNull(s.imagePath))
        val r = runTask(s.task, input)
        _state.value = _state.value.copy(busy = false, result = r.first, source = r.second)
    }

    private suspend fun <R : Any> runTask(task: AiTask<R>, input: AiInput): Pair<String, AiResult<R>> {
        val r = router.run(task, input)
        val json = r.value?.let { prettyJson.encodeToString(task.serializer as KSerializer<R>, it) } ?: "(no result: ${r.error?.message})"
        val header = "task=${task.key} source=${r.source.key} confidence=${"%.2f".format(r.confidence)} uncertain=${r.uncertainFields}\n" +
            "attempts=${r.attempts.joinToString { "${it.provider.key}:${it.error?.message ?: "ok"}(${it.millis}ms)" }}\n\n"
        return header + json to r
    }
}

@Composable
fun ParserLabScreen(onBack: () -> Unit, vm: ParserLabViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    val copied = SCommon.copied.t()
    val launchers = rememberScanLaunchers(onImages = { vm.fromImages(it) }, onScannerUnavailable = {})
    Scaffold(topBar = { EasyTopBar(SDev.parserLab.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = launchers.scanDocument) { Text(SScan.document.t()) }
                OutlinedButton(onClick = launchers.pickPhoto) { Text(SScan.gallery.t()) }
            }
            OutlinedTextField(pasted, { pasted = it }, label = { Text("OCR text") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
            TextButton(onClick = { vm.fromText(pasted) }, enabled = pasted.isNotBlank()) { Text(SDev.run.t()) }
            Text(SDev.parseAs.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.tasks.forEach { t -> FilterChip(s.task == t, { vm.setTask(t) }, { Text(t.key) }) }
            }
            if (s.busy) LoadingBox(modifier = Modifier.heightIn(max = 160.dp))
            s.imagePath?.let { PagePreview(it) }
            s.source?.let { SourceChip(it.provenance) }
            s.ocr?.let { doc ->
                EasyCard {
                    Text(SDev.rawOcr.t(), style = MaterialTheme.typography.titleSmall)
                    Mono(doc.rowText.take(6_000))
                    Row {
                        TextButton(onClick = {
                            copyToClipboard(context, Masker.forSharing(doc.rowText).text)
                            scope.launch { snackbar.showSnackbar(copied) }
                        }) { Text(SDev.copyMaskedOcr.t()) }
                    }
                }
            }
            s.result?.let { r ->
                EasyCard {
                    Text(SDev.parseResult.t(), style = MaterialTheme.typography.titleSmall)
                    Mono(r)
                    TextButton(onClick = {
                        copyToClipboard(context, Masker.forSharing(r).text)
                        scope.launch { snackbar.showSnackbar(copied) }
                    }) { Text(SDev.copyParse.t()) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ SMS Lab

@Composable
fun SmsLabScreen(onBack: () -> Unit) {
    var sender by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var out by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Scaffold(topBar = { EasyTopBar(SDev.smsLab.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(sender, { sender = it }, label = { Text(SDev.sender.t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text(SDev.pasteSms.t()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            BigButton(SDev.run.t(), enabled = body.isNotBlank(), onClick = {
                val security = SmsParser.isSecurityMessage(body)
                val o = SmsParser.parse(sender, body, LocalDateTime.now())
                out = buildString {
                    appendLine("sender: '$sender' (allow-list check happens in Budget; bank senders are confirmed by you)")
                    appendLine("OTP/security filter: ${if (security) "DISCARD — never stored" else "pass"}")
                    appendLine("kind: ${o.kind}  template: ${o.templateId ?: "-"}")
                    appendLine()
                    o.result?.let { appendLine(prettyJson.encodeToString(com.easylife.app.core.ai.schema.SmsParseResult.serializer(), it)) }
                }
            })
            out?.let {
                EasyCard { Mono(it) }
                TextButton(onClick = { copyToClipboard(context, Masker.forSharing(body + "\n---\n" + it).text) }) { Text(SDev.copyParse.t()) }
            }
            InfoBanner(SDev.smsLabNote.t())
        }
    }
}

// ------------------------------------------------------------------------------------------------ Error report

@HiltViewModel
class ErrorReportViewModel @Inject constructor(val errorLog: ErrorLog) : ViewModel()

@Composable
fun ErrorReportScreen(onBack: () -> Unit, vm: ErrorReportViewModel = hiltViewModel()) {
    var report by remember { mutableStateOf(vm.errorLog.report()) }
    val context = LocalContext.current
    Scaffold(topBar = { EasyTopBar(SDev.errorReport.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyToClipboard(context, report) }) { Text(SDev.copyReport.t()) }
                TextButton(onClick = {
                    vm.errorLog.clear()
                    report = vm.errorLog.report()
                }) { Text(SDev.clearErrors.t()) }
            }
            Mono(report)
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { throw RuntimeException("Test crash from Easy Life developer tools") }) {
                Text(SDev.testCrash.t(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Router view

@HiltViewModel
class RouterViewViewModel @Inject constructor(private val router: AiRouter, val config: RemoteConfigRepository) : ViewModel() {
    private val _plans = MutableStateFlow<List<RoutePlan>>(emptyList())
    val plans = _plans.asStateFlow()

    fun refresh() = viewModelScope.launch { _plans.value = AiTask.all.map { router.plan(it) } }

    fun refreshConfig() = viewModelScope.launch {
        config.refresh(force = true)
        refresh()
    }
}

@Composable
fun RouterViewScreen(onBack: () -> Unit, vm: RouterViewViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    val plans by vm.plans.collectAsStateWithLifecycle()
    val cfg by vm.config.config.collectAsStateWithLifecycle()
    Scaffold(topBar = { EasyTopBar(SDev.routerView.t(), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${SDev.configSource.t()}: ${cfg.source} · gemini_enabled=${cfg.geminiEnabled} (${cfg.geminiModel}, ${cfg.geminiDailyLimit}/day) · claude_enabled=${cfg.claudeEnabled} · prompts ${cfg.prompts.version}", style = MaterialTheme.typography.bodySmall)
            if (cfg.invalidKeys.isNotEmpty()) InfoBanner("Invalid remote keys (using defaults): ${cfg.invalidKeys}")
            AssistChip(onClick = { vm.refreshConfig() }, label = { Text(SDev.refreshConfig.t()) })
            plans.forEach { plan ->
                EasyCard {
                    Text(plan.task.key, style = MaterialTheme.typography.titleSmall)
                    plan.steps.forEach { step ->
                        Text(
                            "${if (step.willTry) "✓" else "✗"} ${step.provider.key}: ${step.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.willTry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
