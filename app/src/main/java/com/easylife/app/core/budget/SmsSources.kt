package com.easylife.app.core.budget

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.data.repo.BudgetRepository
import com.easylife.app.core.designsystem.Module
import com.easylife.app.core.devtools.ErrorLog
import com.easylife.app.core.di.ApplicationScope
import com.easylife.app.core.notify.WorkScheduler
import com.easylife.app.core.parse.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class InboxMessage(val sender: String, val body: String, val at: Instant)

data class SenderSuggestion(val sender: String, val bankId: String?, val transactionCount: Int)

/** Reads the SMS inbox (only after the user allows it) and suggests which senders are their bank. */
@Singleton
class SmsImporter @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun readInbox(days: Long = 90): List<InboxMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val since = Instant.now().minus(days, ChronoUnit.DAYS).toEpochMilli()
        val out = mutableListOf<InboxMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                out += InboxMessage(sender, body, Instant.ofEpochMilli(c.getLong(2)))
            }
        }
        out
    }

    /** Senders whose messages look like bank transactions (OTPs are not counted and never kept). */
    fun suggestSenders(messages: List<InboxMessage>): List<SenderSuggestion> =
        messages.groupBy { it.sender }
            .map { (sender, msgs) ->
                val tx = msgs.count { !SmsParser.isSecurityMessage(it.body) && SmsParser.parse(sender, it.body).kind == SmsParser.Kind.TRANSACTION }
                SenderSuggestion(sender, BankCatalog.bankForSender(sender)?.id, tx)
            }
            .filter { it.transactionCount > 0 }
            .sortedWith(compareByDescending<SenderSuggestion> { it.bankId != null }.thenByDescending { it.transactionCount })
}

/** New bank SMS are processed the moment they arrive (only if Budget is on and SMS setup is done). */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var budget: BudgetRepository
    @Inject lateinit var prefs: PrefsRepository
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var errorLog: ErrorLog
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        // Multi-part SMS arrive as several pieces from the same sender.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        scope.launch {
            try {
                val p = prefs.current()
                if (p.smsSetupDone && Module.BUDGET in p.enabledModules) {
                    for ((sender, parts) in bySender) {
                        if (sender.isBlank()) continue
                        val body = parts.joinToString("") { it.messageBody ?: "" }
                        val at = Instant.ofEpochMilli(parts.first().timestampMillis)
                        budget.ingest(sender, body, at, "SMS")
                    }
                    scheduler.checkSoon()
                }
            } catch (e: Exception) {
                errorLog.record("sms-receive", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Optional (off by default): wallets that send app notifications instead of SMS. Only apps whose name matches a bank
 * or wallet the user added are read; OTP notifications are discarded like SMS.
 */
@AndroidEntryPoint
class WalletNotificationListener : NotificationListenerService() {
    @Inject lateinit var budget: BudgetRepository
    @Inject lateinit var prefs: PrefsRepository
    @Inject lateinit var errorLog: ErrorLog
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        ).joinToString(". ")
        if (text.isBlank()) return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull() ?: return
        BankCatalog.bankForSender(label) ?: return
        scope.launch {
            runCatching {
                val p = prefs.current()
                if (p.notificationListenerEnabled && Module.BUDGET in p.enabledModules) {
                    budget.ingest(label, text, Instant.ofEpochMilli(sbn.postTime), "NOTIFICATION")
                }
            }.onFailure { errorLog.record("notification-listener", it) }
        }
    }
}
