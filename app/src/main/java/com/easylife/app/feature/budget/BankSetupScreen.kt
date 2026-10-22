package com.easylife.app.feature.budget

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.easylife.app.core.budget.BankCatalog
import com.easylife.app.core.budget.SenderSuggestion
import com.easylife.app.core.budget.SmsImporter
import com.easylife.app.core.data.db.AccountEntity
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.data.repo.IngestResult
import com.easylife.app.core.designsystem.BannerKind
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.BigOutlinedButton
import com.easylife.app.core.designsystem.ConfirmField
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTopBar
import com.easylife.app.core.designsystem.InfoBanner
import com.easylife.app.core.designsystem.SectionHeader
import com.easylife.app.core.designsystem.SettingsSwitchRow
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import com.easylife.app.core.notify.WorkScheduler
import com.easylife.app.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportState(
    val scanning: Boolean = false,
    val suggestions: List<SenderSuggestion>? = null,
    val imported: Int? = null,
    val discardedOtps: Int = 0,
)

@HiltViewModel
class BankSetupViewModel @Inject constructor(
    private val repo: BudgetRepository,
    private val importer: SmsImporter,
    private val prefs: PrefsRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val prefsState = prefs.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _import = MutableStateFlow(ImportState())
    val import = _import.asStateFlow()

    fun hasSms() = importer.hasPermission()

    fun addBank(bankId: String, nickname: String, last4: String) = viewModelScope.launch { repo.addAccount(bankId, nickname, last4) }
    fun deleteBank(id: Long) = viewModelScope.launch { repo.deleteAccount(id) }

    fun scan() = viewModelScope.launch {
        _import.value = ImportState(scanning = true)
        val msgs = importer.readInbox(90)
        _import.value = ImportState(scanning = false, suggestions = importer.suggestSenders(msgs))
    }

    /** Allow the chosen senders, then process their last-90-days messages (OTPs are dropped, not stored). */
    fun importFrom(senders: List<SenderSuggestion>) = viewModelScope.launch {
        _import.value = _import.value.copy(scanning = true)
        repo.allowSenders(senders.map { it.sender to (it.bankId ?: "OTHER") })
        val chosen = senders.map { it.sender }.toSet()
        var saved = 0
        var otps = 0
        importer.readInbox(90).filter { it.sender in chosen }.sortedBy { it.at }.forEach { m ->
            when (repo.ingest(m.sender, m.body, m.at, "SMS")) {
                IngestResult.SAVED, IngestResult.REVIEW -> saved++
                IngestResult.DISCARDED_SECURITY -> otps++
                else -> Unit
            }
        }
        prefs.setSmsSetupDone(true)
        scheduler.checkSoon()
        _import.value = ImportState(scanning = false, suggestions = null, imported = saved, discardedOtps = otps)
    }

    fun setSmsSetupDone() = viewModelScope.launch { prefs.setSmsSetupDone(true) }
    fun setNotificationListener(on: Boolean) = viewModelScope.launch { prefs.setNotificationListenerEnabled(on) }
    fun setKeepRaw(on: Boolean) = viewModelScope.launch { prefs.setKeepRawSms(on) }
    fun purge(onDone: () -> Unit) = viewModelScope.launch {
        repo.purgeRawMessages()
        onDone()
    }
}

@Composable
fun BankSetupScreen(nav: NavHostController, vm: BankSetupViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val imp by vm.import.collectAsStateWithLifecycle()
    val p by vm.prefsState.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var hasSms by remember { mutableStateOf(vm.hasSms()) }
    var denied by remember { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasSms = r[Manifest.permission.READ_SMS] == true
        denied = !hasSms
        if (hasSms) {
            vm.setSmsSetupDone()
            vm.scan()
        }
    }
    val purgedMsg = SBudget.purged.t()

    SecureScreen()
    Scaffold(topBar = { EasyTopBar(SBudget.banksTitle.t(), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accounts.forEach { a ->
                val bank = BankCatalog.byId(a.bankId)
                ListItem(
                    headlineContent = { Text(a.nickname) },
                    supportingContent = { Text((if (lang == AppLanguage.UR) bank.nameUr else bank.nameEn) + (a.last4?.let { " ··$it" } ?: "")) },
                    leadingContent = { Icon(Icons.Rounded.AccountBalance, null) },
                    trailingContent = { IconButton(onClick = { vm.deleteBank(a.id) }) { Icon(Icons.Rounded.Delete, SCommon.delete.t()) } },
                )
            }
            BigOutlinedButton(SBudget.addBank.t(), icon = Icons.Rounded.Add, onClick = { adding = true })

            SectionHeader(SBudget.smsPermissionTitle.t())
            if (!hasSms) {
                EasyCard {
                    Text(SBudget.smsPermissionBody.t(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }) {
                        Icon(Icons.Rounded.Sms, null)
                        Text(" " + SBudget.allowSms.t())
                    }
                }
                if (denied) InfoBanner(SBudget.smsDenied.t(), BannerKind.INFO)
            } else {
                BigButton(SBudget.scan90.t(), onClick = vm::scan, enabled = !imp.scanning)
            }
            if (imp.scanning) {
                Text(SBudget.scanning.t())
                LinearProgressIndicator(Modifier.padding(vertical = 4.dp))
            }
            imp.imported?.let { n ->
                InfoBanner(SBudget.imported.t(n), BannerKind.SUCCESS)
                if (imp.discardedOtps > 0) InfoBanner(SBudget.otpDiscarded.t(imp.discardedOtps), BannerKind.INFO)
            }
            imp.suggestions?.let { list ->
                if (list.isEmpty()) {
                    InfoBanner(SBudget.noSenders.t())
                } else {
                    var chosen by remember(list) { mutableStateOf(list.filter { it.bankId != null }.map { it.sender }.toSet()) }
                    Text(SBudget.suggestedSenders.t(), style = MaterialTheme.typography.bodyMedium)
                    list.forEach { s ->
                        ListItem(
                            headlineContent = { Text(s.sender) },
                            supportingContent = { Text(listOfNotNull(s.bankId?.let { BankCatalog.byId(it).nameEn }, SBudget.messagesCount.t(s.transactionCount)).joinToString(" · ")) },
                            leadingContent = { Checkbox(checked = s.sender in chosen, onCheckedChange = { c -> chosen = if (c) chosen + s.sender else chosen - s.sender }) },
                        )
                    }
                    BigButton(SBudget.importSelected.t(), enabled = chosen.isNotEmpty() && !imp.scanning, onClick = { vm.importFrom(list.filter { it.sender in chosen }) })
                }
            }

            SectionHeader(SBudget.notificationAccess.t())
            SettingsSwitchRow(
                SBudget.notificationAccess.t(),
                subtitle = SBudget.notificationAccessNote.t(),
                checked = p?.notificationListenerEnabled == true,
                onCheckedChange = { on ->
                    vm.setNotificationListener(on)
                    if (on) runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            )
            SettingsSwitchRow(SBudget.keepRaw.t(), subtitle = SBudget.keepRawNote.t(), checked = p?.keepRawSms != false, onCheckedChange = vm::setKeepRaw)
            OutlinedButton(onClick = { vm.purge { scope.launch { snackbar.showSnackbar(purgedMsg) } } }) { Text(SBudget.purgeRaw.t()) }
            Text(SBudget.purgeRawNote.t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (adding) AddBankDialog(onDismiss = { adding = false }, onAdd = { b, n, l -> vm.addBank(b, n, l); adding = false })
}

@Composable
private fun AddBankDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var bank by remember { mutableStateOf("HBL") }
    var nickname by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    val lang = currentLanguage()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SBudget.pickBank.t()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BankCatalog.all.forEach { b -> FilterChip(bank == b.id, { bank = b.id }, { Text(if (lang == AppLanguage.UR) b.nameUr else b.nameEn) }) }
                }
                ConfirmField(SBudget.nickname.t(), nickname, { nickname = it.take(30) })
                ConfirmField(SBudget.last4.t(), last4, { last4 = it.filter(Char::isDigit).take(4) }, numeric = true, supporting = SBudget.last4Note.t())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(bank, nickname, last4) }) { Text(SCommon.add.t()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SCommon.cancel.t()) } },
    )
}
