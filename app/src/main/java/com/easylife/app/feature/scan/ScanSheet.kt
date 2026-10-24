package com.easylife.app.feature.scan

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylife.app.core.budget.PastedSms
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.designsystem.TintedIcon
import com.easylife.app.core.i18n.SScan
import com.easylife.app.core.i18n.t
import com.easylife.app.core.scan.ScanDraftStore
import com.easylife.app.core.scan.rememberScanLaunchers
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanSheetViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val budget: BudgetRepository,
    private val prefs: PrefsRepository,
) : ViewModel() {
    // Work runs in viewModelScope so the callback (which navigates) is always on the main thread.
    fun fromImages(uris: List<Uri>, onReady: (String) -> Unit) = viewModelScope.launch { onReady(drafts.createFromImages(uris).id) }

    /**
     * Shared text: a bank or wallet SMS goes straight into Budget (the main APK can't read SMS itself);
     * anything else opens in Urdu Helper.
     */
    fun fromSharedText(text: String, onBudget: (BudgetRepository.PasteSummary) -> Unit, onReady: (String) -> Unit) = viewModelScope.launch {
        if (Module.BUDGET in prefs.current().enabledModules && PastedSms.isBankAlert(text)) {
            onBudget(budget.ingestPasted(text))
        } else {
            onReady(drafts.createFromText(text, com.easylife.app.core.scan.ScanDraft.SHARED).id)
        }
    }
    fun sample(name: String, onReady: (String) -> Unit) = viewModelScope.launch { onReady(drafts.createSample(name).id) }

}

/** Universal Scan: one button for bills, meters, receipts, price tags and papers. */
@Composable
fun ScanSheet(viewModel: ScanSheetViewModel, onDismiss: () -> Unit, onDraft: (String) -> Unit, onMeter: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val unavailable = SScan.scannerUnavailable.t()
    val launchers = rememberScanLaunchers(
        onImages = { uris -> viewModel.fromImages(uris, onDraft) },
        onScannerUnavailable = { scope.launch { snackbar.showSnackbar(unavailable) } },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(SScan.whatToScan.t(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            val ext = EasyTheme.colors
            ScanOption(Icons.Rounded.DocumentScanner, SScan.document.t(), SScan.documentHint.t(), ext.bills.accent, ext.bills.container) { launchers.scanDocument() }
            ScanOption(Icons.Rounded.Bolt, SScan.meter.t(), SScan.meterHint.t(), ext.limits.accent, ext.limits.container) { onMeter() }
            ScanOption(Icons.Rounded.PhotoLibrary, SScan.gallery.t(), SScan.galleryHint.t(), ext.urdu.accent, ext.urdu.container) { launchers.pickPhoto() }

            Spacer(Modifier.padding(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = ext.demo.accent)
                Spacer(Modifier.width(8.dp))
                Text(SScan.samples.t(), style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "sample_lesco_bill" to SScan.sampleBill,
                    "sample_circular" to SScan.sampleCircular,
                    "sample_receipt" to SScan.sampleReceipt,
                ).forEach { (name, label) ->
                    AssistChip(onClick = { viewModel.sample(name, onDraft) }, label = { Text(label.t()) })
                }
            }
        }
    }
}

@Composable
private fun ScanOption(icon: ImageVector, title: String, hint: String, accent: Color, container: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIcon(icon, accent, container, 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
