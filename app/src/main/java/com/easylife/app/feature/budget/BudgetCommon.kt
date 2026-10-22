package com.easylife.app.feature.budget

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.data.db.CategoryEntity
import com.easylife.app.core.data.db.TransactionEntity
import com.easylife.app.core.designsystem.BigButton
import com.easylife.app.core.designsystem.DemoChip
import com.easylife.app.core.designsystem.EasyCard
import com.easylife.app.core.designsystem.EasyTheme
import com.easylife.app.core.designsystem.EmptyState
import com.easylife.app.core.designsystem.NumberStyle
import com.easylife.app.core.designsystem.TintedIcon
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SBudget
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import java.time.ZoneId

val categoryIcons: Map<String, ImageVector> = mapOf(
    "ShoppingBasket" to Icons.Rounded.ShoppingBasket, "Restaurant" to Icons.Rounded.Restaurant, "LocalGasStation" to Icons.Rounded.LocalGasStation,
    "ReceiptLong" to Icons.AutoMirrored.Rounded.ReceiptLong, "PhoneAndroid" to Icons.Rounded.PhoneAndroid, "School" to Icons.Rounded.School,
    "LocalHospital" to Icons.Rounded.LocalHospital, "ShoppingBag" to Icons.Rounded.ShoppingBag, "CardGiftcard" to Icons.Rounded.CardGiftcard,
    "VolunteerActivism" to Icons.Rounded.VolunteerActivism, "Home" to Icons.Rounded.Home, "SwapHoriz" to Icons.Rounded.SwapHoriz,
    "LocalAtm" to Icons.Rounded.LocalAtm, "Savings" to Icons.Rounded.Savings, "Category" to Icons.Rounded.Category,
)

fun CategoryEntity.name(lang: AppLanguage) = if (lang == AppLanguage.UR) nameUr else nameEn
fun CategoryEntity.icon(): ImageVector = categoryIcons[icon] ?: Icons.Rounded.Category
fun CategoryEntity.colorValue(): Color = Color(color.toInt())

@Composable
fun CategoryIcon(c: CategoryEntity?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val color = c?.colorValue() ?: MaterialTheme.colorScheme.outline
    TintedIcon(c?.icon() ?: Icons.Rounded.Category, color, color.copy(alpha = 0.15f), size)
}

/** Budget screens can't be screenshotted or shown in recent apps (FLAG_SECURE). */
@Composable
fun SecureScreen() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Fingerprint / face / device PIN gate for Budget (Settings -> Lock Budget). Unlocked once per app session. */
object BudgetSession {
    var unlocked = false
}

@Composable
fun BudgetLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    SecureScreen()
    if (!enabled || BudgetSession.unlocked) {
        content()
        return
    }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current as? FragmentActivity
    val reason = SBudget.unlockReason.t()
    fun prompt() {
        val a = activity ?: return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(a).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No lock screen set up: nothing to check against, so don't block the user.
            BudgetSession.unlocked = true
            unlocked = true
            return
        }
        BiometricPrompt(a, androidx.core.content.ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                BudgetSession.unlocked = true
                unlocked = true
            }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(reason).setAllowedAuthenticators(authenticators).build())
    }
    LaunchedEffect(Unit) { prompt() }
    if (unlocked) {
        content()
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(Icons.Rounded.Lock, SBudget.locked.t(), "")
            BigButton(SBudget.unlock.t(), onClick = { prompt() })
        }
    }
}

/** One transaction. Swipe to exclude/include; long-press to select. Excluded ones are greyed and struck through. */
@Composable
fun TransactionRow(
    tx: TransactionEntity,
    category: CategoryEntity?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleExclude: () -> Unit,
) {
    val lang = currentLanguage()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled) onToggleExclude()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (tx.excluded) Icons.Rounded.CheckCircle else Icons.Rounded.VisibilityOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (tx.excluded) SBudget.include.t() else SBudget.exclude.t())
                }
            }
        },
    ) {
        EasyCard(
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (tx.excluded) 0.5f else 1f)) {
                if (selecting) {
                    androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
                } else {
                    CategoryIcon(category)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tx.merchant ?: tx.counterparty ?: SBudget.channel(tx.channel).t(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (tx.excluded) TextDecoration.LineThrough else null,
                    )
                    Text(
                        listOfNotNull(
                            category?.name(lang),
                            Fmt.dateTime(tx.timestamp.atZone(ZoneId.systemDefault()).toLocalDateTime(), lang),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (tx.excluded) Text(SBudget.excludedNote.t(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val debit = tx.direction == "DEBIT"
                    Text(
                        (if (debit) "−" else "+") + Fmt.rupees(tx.amount, lang),
                        style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                        color = if (debit) MaterialTheme.colorScheme.onSurface else EasyTheme.colors.safe,
                    )
                    if (tx.isDemo || tx.source == Provenance.DEMO.name) DemoChip()
                }
            }
        }
    }
}
