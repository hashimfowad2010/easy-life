package com.easylife.app.feature.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.easylife.app.core.ai.AiInput
import com.easylife.app.core.ai.AiRouter
import com.easylife.app.core.ai.AiTask
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.ai.schema.MeterReadResult
import com.easylife.app.core.data.db.MeterEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.LimitsRepository
import com.easylife.app.core.data.repo.toInstantLocal
import com.easylife.app.core.data.repo.toLocal
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.DateField
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SLimits
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.impact.ImpactEvents
import com.easylife.app.core.impact.ImpactTracker
import com.easylife.app.core.notify.WorkScheduler
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.navigation.LimitsRoute
import com.easylife.app.navigation.MeterCameraRoute
import com.easylife.app.navigation.MeterSetupRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AddReadingState(
    val loading: Boolean = true,
    val meter: MeterEntity? = null,
    val previous: Double? = null,
    val imagePath: String? = null,
    val result: MeterReadResult? = null,
    val provenance: Provenance? = null,
    val draftId: String? = null,
)

@HiltViewModel
class AddReadingViewModel @Inject constructor(
    private val limits: LimitsRepository,
    private val prefs: PrefsRepository,
    private val drafts: ScanDraftStore,
    private val router: AiRouter,
    private val impact: ImpactTracker,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(AddReadingState())
    val state = _state.asStateFlow()
    private var loaded = false

    fun load(meterId: Long, draftId: String?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val meter = if (meterId > 0) limits.meter(meterId) else {
                val selected = prefs.current().selectedMeterId
                limits.meters.first().let { all -> all.firstOrNull { it.id == selected } ?: all.firstOrNull() }
            }
            val last = meter?.let { limits.latestReading(it.id) }
            val previous = last?.reading ?: meter?.cycleStartReading
            val since = last?.let { ChronoUnit.HOURS.between(it.takenAt.toLocal(), LocalDateTime.now()) / 24.0 }
                ?: meter?.cycleStartDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toDouble() }
            var state = AddReadingState(loading = false, meter = meter, previous = previous, draftId = draftId)
            if (draftId != null) {
                val draft = drafts.get(draftId)
                if (draft != null) {
                    val r = router.run(
                        AiTask.MeterRead,
                        AiInput(
                            ocr = draft.ocr,
                            imagePaths = draft.imagePaths,
                            text = draft.text,
                            params = buildMap {
                                previous?.let { put("previousReading", it.toLong().toString()) }
                                since?.let { put("daysSincePrevious", it.toString()) }
                                meter?.let { put("meterDigits", it.meterDigits.toString()) }
                            },
                        ),
                    )
                    state = state.copy(
                        imagePath = draft.imagePaths.firstOrNull(),
                        result = r.value,
                        provenance = if (draft.isSample) Provenance.DEMO else r.provenance,
                    )
                }
            }
            _state.value = state
        }
    }

    fun save(value: Double, date: LocalDate, onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val meter = s.meter ?: return@launch
        val at = if (date == LocalDate.now()) LocalDateTime.now() else date.atTime(LocalTime.of(20, 0))
        val source = s.provenance?.name ?: Provenance.MANUAL.name
        limits.addReading(meter.id, value, at.toInstantLocal(), source, s.result?.confidence)
        impact.log(ImpactEvents.METER_READING, "limits")
        s.result?.reading?.let { aiValue ->
            impact.logCorrections(AiTask.MeterRead, s.provenance?.name ?: "", if (aiValue == value) 0 else 1)
        }
        s.draftId?.let { drafts.delete(it) }
        scheduler.checkSoon()
        onDone()
    }
}

@Composable
fun AddReadingScreen(meterId: Long, draftId: String?, nav: NavHostController, vm: AddReadingViewModel = hiltViewModel()) {
    LaunchedEffect(meterId, draftId) { vm.load(meterId, draftId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    Scaffold(topBar = { EasyTopBar(SLimits.readingTitle.t(), onBack = { nav.popBackStack() }) }) { padding ->
        if (s.loading) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val meter = s.meter
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (meter == null) {
                InfoBanner(SLimits.noMeterYet.t(), BannerKind.WARNING)
                BigButton(SLimits.setupTitle.t(), onClick = { nav.navigate(MeterSetupRoute()) })
                return@Column
            }
            s.imagePath?.let { PagePreview(it) }
            val r = s.result
            var text by remember(r) { mutableStateOf(r?.reading?.toLong()?.toString() ?: "") }
            var date by remember { mutableStateOf(LocalDate.now()) }
            val value = text.toDoubleOrNull()
            val previous = s.previous
            val lower = value != null && previous != null && value < previous
            val unsure = r != null && (r.confidence < 0.8 || r.reading == null)

            s.provenance?.let { SourceChip(it) }
            if (unsure) InfoBanner(SLimits.notSure.t(), BannerKind.WARNING)
            ConfirmField(
                label = SLimits.readingValue.t(),
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(8) },
                numeric = true,
                uncertain = unsure || lower,
                supporting = r?.digitsSeen?.takeIf { it.isNotBlank() }?.let { SLimits.digitsSeen.t(Fmt.ltr(it, lang)) },
            )
            if (lower && previous != null) InfoBanner(SLimits.lowerThanLast.t(Fmt.ltr(Fmt.number(previous), lang)), BannerKind.WARNING)
            if (value != null && previous != null && value >= previous) {
                Text(SLimits.since.t(Fmt.ltr(Fmt.smart(value - previous), lang)), style = MaterialTheme.typography.bodyMedium)
            }
            r?.candidates?.takeIf { it.isNotEmpty() }?.let { others ->
                Text(SLimits.otherCandidates.t(), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { c -> AssistChip(onClick = { text = c.toLong().toString() }, label = { Text(Fmt.number(c.toLong().toInt())) }) }
                }
            }
            DateField(SLimits.takenOn.t(), date, { date = it ?: LocalDate.now() })
            if (s.imagePath == null) {
                BigOutlinedButton(SLimits.takePhoto.t(), icon = Icons.Rounded.CameraAlt, onClick = { nav.navigate(MeterCameraRoute(meter.id)) })
            }
            BigButton(SCommon.save.t(), enabled = value != null && value > 0, onClick = {
                vm.save(value!!, date) {
                    if (!nav.popBackStack(LimitsRoute, inclusive = false)) {
                        nav.navigate(LimitsRoute) { popUpTo(nav.graph.findStartDestination().id) }
                    }
                }
            })
        }
    }
}
