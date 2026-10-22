package com.easylife.app.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.easylife.app.core.ai.schema.ReceiptExtractResult
import com.easylife.app.core.budget.Tx
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.ObservedPrice
import com.easylife.app.core.data.repo.ShoppingRepository
import com.easylife.app.core.data.repo.toInstantLocal
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.LabeledValue
import com.easylife.app.core.designsystem.LoadingBox
import com.easylife.app.core.designsystem.PagePreview
import com.easylife.app.core.designsystem.SourceChip
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SShop
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.parse.ShoppingListParser
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.scan.ScanKind
import com.easylife.app.navigation.BudgetRoute
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ReceiptState(
    val loading: Boolean = true,
    val receipt: ReceiptExtractResult? = null,
    val provenance: Provenance = Provenance.ON_DEVICE,
    val imagePath: String? = null,
    val isPriceTag: Boolean = false,
    val consent: Boolean? = null,
)

@HiltViewModel
class ReceiptConfirmViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val router: AiRouter,
    private val budget: BudgetRepository,
    private val shopping: ShoppingRepository,
    private val prefs: PrefsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptState())
    val state = _state.asStateFlow()
    private var draftId: String? = null

    fun load(id: String) {
        if (draftId == id) return
        draftId = id
        viewModelScope.launch {
            val d = drafts.get(id)
            val r = d?.let { router.run(AiTask.ReceiptExtract, AiInput(text = it.ocr?.plainText ?: it.text, ocr = it.ocr, imagePaths = it.imagePaths)) }
            _state.value = ReceiptState(
                loading = false,
                receipt = r?.value,
                provenance = if (d?.isSample == true) Provenance.DEMO else r?.provenance ?: Provenance.ON_DEVICE,
                imagePath = d?.imagePaths?.firstOrNull(),
                isPriceTag = ScanKind.of(d?.kind) == ScanKind.PRICE_TAG,
                consent = prefs.current().communityPricesConsent,
            )
        }
    }

    fun setConsent(v: Boolean) = viewModelScope.launch {
        prefs.setCommunityPricesConsent(v)
        _state.value = _state.value.copy(consent = v)
    }

    fun save(store: String, area: String, total: Double?, addToBudget: Boolean, share: Boolean, onDone: (Int) -> Unit) = viewModelScope.launch {
        val s = _state.value
        val r = s.receipt ?: return@launch
        val date = r.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val isDemo = s.provenance == Provenance.DEMO
        if (addToBudget && total != null && total > 0) {
            budget.saveTransaction(
                Tx(0, date.atTime(if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON).toInstantLocal(), total, true, null, store.ifBlank { null }, "cash", null, "groceries", false),
                origin = "RECEIPT",
                source = s.provenance.name,
                category = "groceries",
                isDemo = isDemo,
            )
        }
        // Each recognised item becomes a price observation (pack size from the receipt line, else one packet).
        val observed = r.items.mapNotNull { item ->
            val parsed = ShoppingListParser.parseSegment(item.name) ?: return@mapNotNull null
            if (!parsed.recognized) return@mapNotNull null
            val line = item.lineTotal ?: return@mapNotNull null
            val perPack = line / (item.qty.takeIf { it > 0 } ?: 1.0)
            ObservedPrice(parsed.itemKey, item.name.ifBlank { parsed.nameEn }, perPack, item.packSize ?: 1.0, item.unit ?: "packet")
        }
        val n = if (observed.isNotEmpty() && store.isNotBlank()) shopping.recordPrices(store, area.ifBlank { null }, observed, share && !isDemo) else 0
        draftId?.let { drafts.delete(it) }
        onDone(n)
    }
}

@Composable
fun ReceiptConfirmScreen(draftId: String, nav: NavHostController, vm: ReceiptConfirmViewModel = hiltViewModel()) {
    LaunchedEffect(draftId) { vm.load(draftId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val snackbar = LocalSnackbar.current
    val savedTxt = SShop.pricesSaved
    Scaffold(topBar = { EasyTopBar(SBudget.receiptTitle.t(), onBack = { nav.popBackStack() }) }) { padding ->
        if (s.loading) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val r = s.receipt
        var store by remember(r) { mutableStateOf(r?.storeName ?: "") }
        var area by remember(r) { mutableStateOf(r?.storeArea ?: "") }
        var total by remember(r) { mutableStateOf(r?.total?.toLong()?.toString() ?: "") }
        var addToBudget by remember(s.isPriceTag) { mutableStateOf(!s.isPriceTag) }
        var share by remember(s.consent) { mutableStateOf(s.consent == true) }
        var askConsent by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.imagePath?.let { PagePreview(it) }
            SourceChip(s.provenance)
            if (r == null || r.items.isEmpty()) InfoBanner(com.easylife.app.core.i18n.SScan.nothingRead.t(), BannerKind.WARNING)
            if (r != null && r.uncertainFields.isNotEmpty()) InfoBanner(SCommon.checkHighlighted.t(), BannerKind.WARNING)
            ConfirmField(SBudget.storeName.t(), store, { store = it.take(60) }, uncertain = r?.uncertainFields?.contains("storeName") == true)
            ConfirmField(SBudget.storeArea.t(), area, { area = it.take(60) })
            r?.items?.let { items ->
                EasyCard { items.forEach { LabeledValue("${it.name} ×${Fmt.smart(it.qty)}", it.lineTotal?.let { v -> Fmt.rupees(v, lang) } ?: "—") } }
            }
            ConfirmField(SCommon.total.t() + " (Rs)", total, { total = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, numeric = true, uncertain = r?.uncertainFields?.contains("total") == true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(addToBudget, { addToBudget = it })
                Text(SBudget.receiptAsTx.t(total.toDoubleOrNull()?.let { Fmt.rupees(it, lang) } ?: ""), modifier = Modifier.width(280.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(share, { v -> if (v && s.consent == null) askConsent = true else { share = v; if (s.consent != null) vm.setConsent(v) } })
                Column {
                    Text(SBudget.shareReceiptPrices.t())
                    Text(SBudget.shareReceiptPricesNote.t(), style = MaterialTheme.typography.bodySmall)
                }
            }
            BigButton(SCommon.save.t(), enabled = r != null, onClick = {
                vm.save(store, area, total.toDoubleOrNull(), addToBudget, share) { n ->
                    launchSnack(snackbar, savedTxt.format(lang, n))
                    nav.navigate(BudgetRoute) { popUpTo(nav.graph.findStartDestination().id) }
                }
            })
        }
        if (askConsent) {
            AlertDialog(
                onDismissRequest = { askConsent = false },
                title = { Text(SShop.consentTitle.t()) },
                text = { Text(SShop.consentBody.t()) },
                confirmButton = { TextButton(onClick = { vm.setConsent(true); share = true; askConsent = false }) { Text(SShop.consentYes.t()) } },
                dismissButton = { TextButton(onClick = { vm.setConsent(false); share = false; askConsent = false }) { Text(SShop.consentNo.t()) } },
            )
        }
    }
}

private fun launchSnack(host: androidx.compose.material3.SnackbarHostState, text: String) {
    kotlinx.coroutines.MainScope().launch { host.showSnackbar(text) }
}
