package com.easylife.app.feature.urdu

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.ModelState
import com.easylife.app.core.ai.schema.DocExplainResult
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.Pill
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SmartAiChip
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.devtools.copyToClipboard
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SProvenance
import com.easylife.app.core.i18n.SSettings
import com.easylife.app.core.i18n.SUrdu
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.parse.DocInsights
import com.easylife.app.core.scan.rememberScanLaunchers
import com.easylife.app.core.speech.SpeechState
import com.easylife.app.core.speech.VoiceStatus
import com.easylife.app.core.speech.rememberSpeechState
import com.easylife.app.navigation.BillConfirmRoute
import com.easylife.app.navigation.DocumentRoute
import com.easylife.app.navigation.LocalSnackbar
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun UrduScreen(tab: Int, draftId: String?, nav: NavHostController, vm: UrduViewModel = hiltViewModel()) {
    var selected by rememberSaveable { mutableIntStateOf(tab) }
    LaunchedEffect(draftId) {
        if (draftId != null) {
            selected = 1
            vm.loadDraft(draftId)
        }
    }
    val voice by vm.voice.collectAsStateWithLifecycle()
    Scaffold(topBar = { EasyTopBar(SUrdu.title.t(), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selected) {
                listOf(SUrdu.tabText, SUrdu.tabScan, SUrdu.tabSpeak).forEachIndexed { i, label ->
                    Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label.t()) })
                }
            }
            when (selected) {
                0 -> TextTab(vm)
                1 -> ScanTab(vm, nav)
                else -> SpeakTab(vm, onTranslated = { selected = 0 })
            }
        }
    }
    if (voice != null && voice != VoiceStatus.READY) VoiceGuideDialog(vm)
}

// ------------------------------------------------------------------------------------------------ shared bits

@Composable
fun ModelDownloadCard(state: ModelState, onDownload: (Boolean) -> Unit) {
    if (state == ModelState.READY) return
    EasyCard(containerColor = EasyTheme.colors.urdu.container) {
        Text(
            when (state) {
                ModelState.DOWNLOADING -> SUrdu.downloading.t()
                ModelState.FAILED -> SSettings.modelFailed.t()
                else -> SUrdu.modelNeeded.t()
            },
            style = MaterialTheme.typography.bodyMedium,
            color = EasyTheme.colors.urdu.onContainer,
        )
        if (state == ModelState.DOWNLOADING) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(SUrdu.wifiWaiting.t(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            TextButton(onClick = { onDownload(false) }) { Text(SSettings.downloadAnyNetwork.t()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onDownload(true) }) { Text(SSettings.download.t()) }
                TextButton(onClick = { onDownload(false) }) { Text(SSettings.downloadAnyNetwork.t()) }
            }
        }
    }
}

@Composable
fun ReadAloudButton(text: String, lang: AppLanguage, vm: UrduViewModel) {
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    if (speaking) {
        TextButton(onClick = vm::stopSpeaking) {
            Icon(Icons.Rounded.Stop, null)
            Spacer(Modifier.width(4.dp))
            Text(SCommon.stopReading.t())
        }
    } else {
        TextButton(onClick = { vm.speak(text, lang) }) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
            Spacer(Modifier.width(4.dp))
            Text(SCommon.readAloud.t())
        }
    }
}

@Composable
private fun VoiceGuideDialog(vm: UrduViewModel) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = vm::dismissVoiceGuide,
        title = { Text(SUrdu.voiceMissingTitle.t()) },
        text = { Text(SUrdu.voiceMissingBody.t()) },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(vm.installVoiceIntent()) }
                vm.dismissVoiceGuide()
            }) { Text(SUrdu.installVoice.t()) }
        },
        dismissButton = { TextButton(onClick = vm::dismissVoiceGuide) { Text(SCommon.close.t()) } },
    )
}

private fun shareText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ------------------------------------------------------------------------------------------------ Text tab

@Composable
private fun TextTab(vm: UrduViewModel) {
    val s by vm.text.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val smart by vm.smartAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val copied = SCommon.copied.t()
    val smartMsg = SProvenance.comingWithSmartAi.t()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.fromUrdu) SUrdu.urToEn.t() else SUrdu.enToUr.t(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::swap) { Icon(Icons.Rounded.SwapHoriz, SUrdu.swapDirection.t()) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(s.mode == TranslateMode.NORMAL, { vm.setMode(TranslateMode.NORMAL) }, { Text(SUrdu.modeNormal.t()) })
            listOf(TranslateMode.SIMPLE to SUrdu.modeSimple, TranslateMode.ROMAN_TO_URDU to SUrdu.modeRomanToUrdu, TranslateMode.URDU_TO_ROMAN to SUrdu.modeUrduToRoman)
                .forEach { (mode, label) ->
                    FilterChip(
                        selected = s.mode == mode,
                        onClick = { if (smart) vm.setMode(mode) else scope.launch { snackbar.showSnackbar(smartMsg) } },
                        label = { Text(label.t()) },
                        modifier = Modifier.semantics { if (!smart) contentDescription = "${label.en}. ${SProvenance.smartAiBadge.en}" },
                    )
                }
        }
        if (!smart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmartAiChip()
                Spacer(Modifier.width(8.dp))
                Text(SProvenance.comingWithSmartAi.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (s.mode == TranslateMode.NORMAL) ModelDownloadCard(model, vm::downloadModel)
        OutlinedTextField(
            value = s.input,
            onValueChange = vm::setInput,
            label = { Text(SUrdu.typeHere.t()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
        )
        BigButton(SUrdu.translate.t(), enabled = s.input.isNotBlank() && !s.busy && (s.mode != TranslateMode.NORMAL || model == ModelState.READY), onClick = vm::translateText)
        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.failed) InfoBanner(SUrdu.translateFailed.t(), BannerKind.WARNING)
        s.output?.let { out ->
            val outLang = if (s.fromUrdu) AppLanguage.EN else AppLanguage.UR
            EasyCard {
                Text(SUrdu.translation.t(), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SelectionContainer { Text(out, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)) }
                Spacer(Modifier.height(8.dp))
                s.source?.let { SourceChip(it) }
                Text(SUrdu.machineNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row {
                    ReadAloudButton(out, outLang, vm)
                    IconButton(onClick = {
                        copyToClipboard(context, out)
                        scope.launch { snackbar.showSnackbar(copied) }
                    }) { Icon(Icons.Rounded.ContentCopy, SCommon.copy.t()) }
                    IconButton(onClick = { shareText(context, out) }) { Icon(Icons.Rounded.Share, SCommon.share.t()) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Scan & Read tab

@Composable
private fun ScanTab(vm: UrduViewModel, nav: NavHostController) {
    val s by vm.scan.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pasting by remember { mutableStateOf(false) }
    val launchers = rememberScanLaunchers(onImages = { vm.fromImages(it) }, onScannerUnavailable = {})
    val reminderAdded = SUrdu.reminderAdded.t()
    val savedMsg = SUrdu.savedDocument.t()

    if (s.busy) {
        LoadingBox(SUrdu.reading.t())
        return
    }
    val insights = s.insights
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.text == null || insights == null) {
            BigButton(SUrdu.scanPaper.t(), icon = Icons.Rounded.DocumentScanner, onClick = launchers.scanDocument)
            BigOutlinedButton(SUrdu.pickPhoto.t(), icon = Icons.Rounded.PhotoLibrary, onClick = launchers.pickPhoto)
            BigOutlinedButton(SUrdu.pasteText.t(), icon = Icons.Rounded.ContentCopy, onClick = { pasting = true })
            InfoBanner(SUrdu.shareHint.t())
            SavedDocumentsList(nav)
        } else {
            s.imagePath?.let { PagePreview(it) }
            DocumentInsightsView(
                insights = insights,
                source = s.insightsSource,
                translation = s.translation,
                original = s.text.orEmpty(),
                lang = lang,
                vm = vm,
                model = model,
                onAddReminder = { title, date ->
                    vm.addReminder(title, date)
                    scope.launch { snackbar.showSnackbar(reminderAdded) }
                },
            )
            if (s.translation == null && model == ModelState.READY) {
                OutlinedButton(onClick = vm::translateScanNow) { Text(SUrdu.translate.t()) }
            }
            if (s.savedId == null) {
                BigButton(SUrdu.saveDocument.t(), onClick = {
                    vm.saveDocument(SUrdu.title.en)
                    scope.launch { snackbar.showSnackbar(savedMsg) }
                })
            }
            val billLike = insights.docType == "bill" || (insights.keyDetails.any { it.kind == "amount" } && insights.keyDetails.any { it.kind == "deadline" })
            val draft = vm.draftForBill()
            if (billLike && draft != null) {
                BigOutlinedButton(SUrdu.saveAsBill.t(), onClick = { nav.navigate(BillConfirmRoute(draftId = draft, kind = "other_bill")) })
            }
        }
    }
    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(SUrdu.pasteText.t()) },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    vm.fromPastedText(text)
                    pasting = false
                }) { Text(SCommon.ok.t()) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(SCommon.cancel.t()) } },
        )
    }
}

/** Shared by the scan tab and saved documents. */
@Composable
fun DocumentInsightsView(
    insights: DocExplainResult,
    source: com.easylife.app.core.ai.Provenance?,
    translation: String?,
    original: String,
    lang: AppLanguage,
    vm: UrduViewModel,
    model: ModelState,
    onAddReminder: (String, LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill((SUrdu.docTypes[insights.docType] ?: SUrdu.docTypes.getValue("other")).t(), EasyTheme.colors.urdu.container, EasyTheme.colors.urdu.onContainer)
        Spacer(Modifier.width(8.dp))
        source?.let { SourceChip(it) }
    }
    if (DocInsights.isMedicalOrLegal(insights.docType)) InfoBanner(SUrdu.medicalLegal.t(), BannerKind.DANGER)

    val summary = if (lang == AppLanguage.UR) insights.summaryUr else insights.summaryEn
    val realSummary = summary.filterNot { it.contains("not advice") || it.contains("مشورہ نہیں") }
    EasyCard {
        Text(SUrdu.summary.t(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (realSummary.isEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                SmartAiChip()
            }
            Text(SUrdu.smartSummaryLocked.t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        } else {
            realSummary.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp)) }
            ReadAloudButton(realSummary.joinToString("\n"), lang, vm)
        }
    }

    if (insights.actions.isNotEmpty()) {
        SectionHeader(SUrdu.thingsToDo.t())
        insights.actions.forEach { a ->
            EasyCard {
                Text(if (lang == AppLanguage.UR || a.textEn == null) a.textUr else a.textEn, style = MaterialTheme.typography.bodyLarge)
                if (lang == AppLanguage.UR && a.textEn != null) {
                    Text(a.textEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                val due = a.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (due != null) {
                    TextButton(onClick = { onAddReminder(a.textEn ?: a.textUr, due) }) {
                        Icon(Icons.Rounded.EventAvailable, null)
                        Spacer(Modifier.width(6.dp))
                        Text("${SUrdu.addReminder.t()} · ${Fmt.shortDate(due, lang)}")
                    }
                }
            }
        }
    }

    SectionHeader(SUrdu.keyDetails.t())
    EasyCard {
        if (insights.keyDetails.isEmpty()) Text(SUrdu.nothingFound.t())
        insights.keyDetails.forEach { d ->
            val value = when (d.kind) {
                "deadline", "event", "date" -> runCatching { Fmt.date(LocalDate.parse(d.value), lang) }.getOrDefault(d.value)
                "amount" -> d.value.toDoubleOrNull()?.let { Fmt.rupees(it, lang) } ?: d.value
                else -> Fmt.ltr(d.value, lang)
            }
            LabeledValue(if (lang == AppLanguage.UR || d.labelEn == null) d.labelUr else d.labelEn, value)
        }
    }

    SectionHeader(SUrdu.fullTranslation.t())
    if (translation != null) {
        EasyCard {
            SelectionContainer { Text(translation, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)) }
            Text(SUrdu.machineNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReadAloudButton(translation, AppLanguage.UR, vm)
        }
    } else {
        ModelDownloadCard(model, vm::downloadModel)
    }
    var showOriginal by remember { mutableStateOf(false) }
    TextButton(onClick = { showOriginal = !showOriginal }) { Text(SUrdu.original.t()) }
    if (showOriginal) {
        EasyCard {
            SelectionContainer { Text(original, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)) }
            ReadAloudButton(original, AppLanguage.EN, vm)
        }
    }
}

@Composable
private fun SavedDocumentsList(nav: NavHostController, vm: SavedDocumentsViewModel = hiltViewModel()) {
    val docs by vm.docs.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    SectionHeader(SUrdu.savedDocs.t())
    if (docs.isEmpty()) {
        Text(SUrdu.noSavedDocs.t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    docs.forEach { d ->
        ListItem(
            headlineContent = { Text(d.title, maxLines = 2) },
            supportingContent = { Text((SUrdu.docTypes[d.docType] ?: SUrdu.docTypes.getValue("other")).t() + " · " + Fmt.shortDate(d.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), lang)) },
            leadingContent = { Icon(Icons.Rounded.Description, null) },
            trailingContent = { if (d.isDemo) com.easylife.app.core.designsystem.DemoChip() },
        )
        TextButton(onClick = { nav.navigate(DocumentRoute(d.id)) }) { Text(SCommon.more.t()) }
    }
}

// ------------------------------------------------------------------------------------------------ Speak tab

@Composable
private fun SpeakTab(vm: UrduViewModel, onTranslated: () -> Unit) {
    val context = LocalContext.current
    val speech = rememberSpeechState()
    var speakUrdu by rememberSaveable { mutableStateOf(true) }
    var heard by rememberSaveable { mutableStateOf("") }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    // Fallback: the system's own voice typing screen (no microphone permission needed by us).
    val systemRecognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                heard = it
                vm.translateHeard(it, speakUrdu)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(SUrdu.speakIn.t(), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(speakUrdu, { speakUrdu = true }, { Text(SUrdu.urdu.t()) })
            FilterChip(!speakUrdu, { speakUrdu = false }, { Text(SUrdu.english.t()) })
        }
        val lang = if (speakUrdu) AppLanguage.UR else AppLanguage.EN
        when {
            !speech.available -> {
                InfoBanner(SUrdu.speechUnavailable.t(), BannerKind.INFO)
                OutlinedButton(onClick = { runCatching { systemRecognizer.launch(SpeechState.recognizerIntent(lang)) } }) { Text(SUrdu.holdToSpeak.t()) }
            }
            !hasMic -> {
                EasyCard {
                    Text(SUrdu.micPermission.t(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text(SUrdu.allowMic.t()) }
                }
            }
            else -> {
                val label = if (speech.listening) SUrdu.listening.t() else SUrdu.holdToSpeak.t()
                Box(
                    Modifier
                        .size(132.dp)
                        .background(if (speech.listening) EasyTheme.colors.urdu.accent else MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = label }
                        .pointerInput(lang) {
                            detectTapGestures(onPress = {
                                speech.start(lang) { text ->
                                    heard = text
                                    vm.translateHeard(text, speakUrdu)
                                }
                                tryAwaitRelease()
                                speech.finish()
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp)) }
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (speech.partial.isNotBlank()) Text(speech.partial, style = MaterialTheme.typography.bodyLarge)
                if (speech.error != null) Text(SUrdu.speechError.t(), color = MaterialTheme.colorScheme.error)
            }
        }
        if (heard.isNotBlank()) {
            val t by vm.text.collectAsStateWithLifecycle()
            EasyCard {
                Text(SUrdu.youSaid.t(), style = MaterialTheme.typography.labelLarge)
                Text(heard, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                Spacer(Modifier.height(8.dp))
                if (t.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                t.output?.let { out ->
                    Text(SUrdu.translation.t(), style = MaterialTheme.typography.labelLarge)
                    Text(out, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                    ReadAloudButton(out, if (speakUrdu) AppLanguage.EN else AppLanguage.UR, vm)
                }
                if (t.failed) TextButton(onClick = onTranslated) { Text(SUrdu.modelNeeded.t()) }
            }
        }
    }
}
