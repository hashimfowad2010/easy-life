package com.easylife.app.feature.bills

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.schema.BillExtractResult
import com.easylife.app.core.data.repo.BillsRepository
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.DateField
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.SBills
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.t
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.notify.WorkScheduler
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.scan.ScanKind
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.easylife.app.navigation.BillDetailRoute
import com.easylife.app.navigation.LimitsRoute
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BillConfirmState(
    val loading: Boolean = true,
    val extract: BillExtractResult = BillExtractResult(),
    val provenance: Provenance = Provenance.MANUAL,
    val imagePath: String? = null,
    val ocrText: String? = null,
    val draftId: String? = null,
)

@HiltViewModel
class BillConfirmViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val router: AiRouter,
    private val bills: BillsRepository,
    private val impact: ImpactTracker,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(BillConfirmState())
    val state = _state.asStateFlow()
    private var loaded = false

    fun load(draftId: String?, kind: String?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val kindType = kind?.let { ScanKind.of(it).billType } ?: "other"
            val draft = draftId?.let { drafts.get(it) }
            if (draft == null) {
                _state.value = BillConfirmState(loading = false, extract = BillExtractResult(billType = kindType, confidence = 1.0))
                return@launch
            }
            val r = router.run(AiTask.BillExtract, AiInput(text = draft.ocr?.plainText ?: draft.text, ocr = draft.ocr, imagePaths = draft.imagePaths))
            var extract = r.value ?: BillExtractResult(billType = kindType)
            // The user's one-tap choice on the scan screen wins over a vague parse.
            if (extract.billType == "other" && kindType != "other") extract = extract.copy(billType = kindType)
            _state.value = BillConfirmState(
                loading = false,
                extract = extract.copy(uncertainFields = (extract.uncertainFields + r.uncertainFields).distinct()),
                provenance = if (draft.isSample) Provenance.DEMO else r.provenance,
                imagePath = draft.imagePaths.firstOrNull(),
                ocrText = draft.ocr?.plainText ?: draft.text,
                draftId = draft.id,
            )
        }
    }

    /** Saves and returns (billId, isElectricity). Counts how many fields the user had to correct. */
    fun save(edited: BillExtractResult, onDone: (Long, Boolean) -> Unit) = viewModelScope.launch {
        val s = _state.value
        val original = s.extract
        val changed = listOf(
            original.provider to edited.provider, original.billingMonth to edited.billingMonth, original.issueDate to edited.issueDate,
            original.dueDate to edited.dueDate, original.readingDate to edited.readingDate, original.amountWithinDue to edited.amountWithinDue,
            original.amountAfterDue to edited.amountAfterDue, original.unitsConsumed to edited.unitsConsumed, original.presentReading to edited.presentReading,
            original.previousReading to edited.previousReading,
        ).count { (a, b) -> a != b }
        val source = if (s.draftId == null) Provenance.MANUAL else s.provenance
        if (s.draftId != null) impact.logCorrections(AiTask.BillExtract, source.name, changed)
        val id = bills.saveConfirmed(edited, source.name, s.ocrText, isDemo = source == Provenance.DEMO)
        s.draftId?.let { drafts.delete(it) }
        scheduler.checkSoon()
        onDone(id, edited.billType == "electricity")
    }
}

@Composable
fun BillConfirmScreen(draftId: String?, kind: String?, nav: NavHostController, vm: BillConfirmViewModel = hiltViewModel()) {
    LaunchedEffect(draftId, kind) { vm.load(draftId, kind) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val savedLinked = SBills.savedAndLinked.t()
    val saved = SBills.saved.t()

    Scaffold(topBar = { EasyTopBar(SBills.confirmTitle.t(), onBack = { nav.popBackStack() }) }) { padding ->
        if (s.loading) {
            LoadingBox(SBills.reading.t(), Modifier.padding(padding))
            return@Scaffold
        }
        val e0 = s.extract
        val unsure = e0.uncertainFields.toSet()
        var type by remember(e0) { mutableStateOf(e0.billType) }
        var provider by remember(e0) { mutableStateOf(e0.provider ?: "") }
        var reference by remember(e0) { mutableStateOf(e0.referenceNo ?: "") }
        var month by remember(e0) { mutableStateOf(e0.billingMonth ?: "") }
        var issue by remember(e0) { mutableStateOf(e0.issueDate?.let(LocalDate::parse)) }
        var due by remember(e0) { mutableStateOf(e0.dueDate?.let(LocalDate::parse)) }
        var readingDate by remember(e0) { mutableStateOf(e0.readingDate?.let(LocalDate::parse)) }
        var within by remember(e0) { mutableStateOf(e0.amountWithinDue?.toLong()?.toString() ?: "") }
        var after by remember(e0) { mutableStateOf(e0.amountAfterDue?.toLong()?.toString() ?: "") }
        var units by remember(e0) { mutableStateOf(e0.unitsConsumed?.toString() ?: "") }
        var prev by remember(e0) { mutableStateOf(e0.previousReading?.toLong()?.toString() ?: "") }
        var present by remember(e0) { mutableStateOf(e0.presentReading?.toLong()?.toString() ?: "") }
        var status by remember(e0) { mutableStateOf(e0.consumerStatusPrinted) }
        val monthValid = month.isBlank() || runCatching { YearMonth.parse(month) }.isSuccess

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            s.imagePath?.let { PagePreview(it) }
            if (s.draftId != null) SourceChip(s.provenance)
            Text(SBills.confirmIntro.t(), style = MaterialTheme.typography.bodyMedium)
            if (unsure.isNotEmpty()) InfoBanner(SCommon.checkHighlighted.t(), BannerKind.WARNING)

            Text(SBills.billType.t(), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SBills.types.forEach { k -> FilterChip(selected = type == k, onClick = { type = k }, label = { Text(SBills.type(k).t()) }) }
            }
            ConfirmField(SBills.provider.t(), provider, { provider = it.take(60) }, uncertain = "provider" in unsure)
            ConfirmField(SBills.billingMonth.t(), month, { month = it.take(7) }, uncertain = "billingMonth" in unsure || !monthValid, supporting = if (!monthValid) SBills.invalidDate.t() else null)
            DateField(SBills.issueDate.t(), issue, { issue = it }, uncertain = "issueDate" in unsure)
            DateField(SBills.dueDate.t(), due, { due = it }, uncertain = "dueDate" in unsure || due == null)
            if (due == null) Text(SBills.needDue.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            ConfirmField(SBills.amountWithin.t(), within, { within = it.filter(Char::isDigit).take(8) }, numeric = true, uncertain = "amountWithinDue" in unsure)
            ConfirmField(SBills.amountAfter.t(), after, { after = it.filter(Char::isDigit).take(8) }, numeric = true, uncertain = "amountAfterDue" in unsure)
            if (type == "electricity") {
                DateField(SBills.readingDate.t(), readingDate, { readingDate = it }, uncertain = "readingDate" in unsure)
                ConfirmField(SBills.units.t(), units, { units = it.filter(Char::isDigit).take(6) }, numeric = true, uncertain = "unitsConsumed" in unsure)
                ConfirmField(SBills.previousReading.t(), prev, { prev = it.filter(Char::isDigit).take(8) }, numeric = true, uncertain = "previousReading" in unsure)
                ConfirmField(SBills.presentReading.t(), present, { present = it.filter(Char::isDigit).take(8) }, numeric = true, uncertain = "presentReading" in unsure)
                Text(SBills.statusPrinted.t(), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("protected", "non_protected", "unknown").forEach { k ->
                        FilterChip(selected = status == k, onClick = { status = k }, label = {
                            Text(
                                when (k) {
                                    "protected" -> com.easylife.app.core.i18n.SLimits.statusProtected.t()
                                    "non_protected" -> com.easylife.app.core.i18n.SLimits.statusNonProtected.t()
                                    else -> SCommon.unknown.t()
                                },
                            )
                        })
                    }
                }
                if (e0.history.isNotEmpty()) Text(SBills.historyFound.t(e0.history.size), style = MaterialTheme.typography.bodyMedium)
            }
            ConfirmField(SBills.referenceNo.t(), reference, { reference = it.take(30) }, uncertain = "referenceNo" in unsure)

            BigButton(SBills.save.t(), enabled = monthValid, onClick = {
                val edited = e0.copy(
                    billType = type,
                    provider = provider.ifBlank { null },
                    referenceNo = reference.ifBlank { null },
                    billingMonth = month.ifBlank { null },
                    issueDate = issue?.toString(),
                    dueDate = due?.toString(),
                    readingDate = if (type == "electricity") readingDate?.toString() else null,
                    amountWithinDue = within.toDoubleOrNull(),
                    amountAfterDue = after.toDoubleOrNull(),
                    unitsConsumed = if (type == "electricity") units.toIntOrNull() else null,
                    previousReading = if (type == "electricity") prev.toDoubleOrNull() else null,
                    presentReading = if (type == "electricity") present.toDoubleOrNull() else null,
                    consumerStatusPrinted = status,
                )
                vm.save(edited) { id, electric ->
                    scope.launch { snackbar.showSnackbar(if (electric) savedLinked else saved) }
                    // Ask for notifications now, when reminders become useful (just-in-time, spec rule 11).
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Clear the scan/confirm screens so Back never returns to an already-saved form.
                    val start = nav.graph.findStartDestination().id
                    if (electric) {
                        nav.navigate(LimitsRoute) { popUpTo(start) }
                    } else {
                        nav.navigate(BillDetailRoute(id)) { popUpTo(start) }
                    }
                }
            })
        }
    }
}
