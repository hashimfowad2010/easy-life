package com.easylife.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easylife.app.core.ai.Provenance
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.SNav
import com.easylife.app.core.i18n.SProvenance
import com.easylife.app.core.i18n.t
import com.easylife.app.core.limits.Risk

val Module.icon: ImageVector
    get() = when (this) {
        Module.LIMITS -> Icons.Rounded.Bolt
        Module.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
        Module.BUDGET -> Icons.Rounded.AccountBalanceWallet
        Module.URDU -> Icons.Rounded.Translate
        Module.SHOPPING -> Icons.Rounded.ShoppingCart
    }

val Module.title get() = when (this) {
    Module.LIMITS -> SNav.limits
    Module.BILLS -> SNav.bills
    Module.BUDGET -> SNav.budget
    Module.URDU -> SNav.urdu
    Module.SHOPPING -> SNav.shopping
}

/** Standard top bar with an optional back arrow (auto-mirrored for Urdu). */
@Composable
fun EasyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = SCommon.back.t())
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun EasyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Round tinted icon used for modules and list items. */
@Composable
fun TintedIcon(icon: ImageVector, accent: Color, container: Color, size: Dp = 44.dp, contentDescription: String? = null) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun ModuleIcon(module: Module, size: Dp = 44.dp) {
    val c = EasyTheme.colors.module(module)
    TintedIcon(module.icon, c.accent, c.container, size)
}

/** Small rounded label. */
@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

val Provenance.icon: ImageVector
    get() = when (this) {
        Provenance.ON_DEVICE -> Icons.Rounded.PhoneAndroid
        Provenance.GEMINI_FREE, Provenance.CLAUDE -> Icons.Rounded.AutoAwesome
        Provenance.DEMO -> Icons.Rounded.Science
        Provenance.MANUAL -> Icons.Rounded.EditNote
        Provenance.IMPORTED -> Icons.Rounded.UploadFile
        Provenance.COMMUNITY -> Icons.Rounded.Groups
    }

/** "Read on your phone" / "Smart AI (Gemini)" / "Demo data" — shown on every AI result (spec rule 7). */
@Composable
fun SourceChip(provenance: Provenance, modifier: Modifier = Modifier) {
    val ext = EasyTheme.colors
    val (container, content) = when {
        provenance == Provenance.DEMO -> ext.demo.container to ext.demo.onContainer
        provenance.isSmartAi -> ext.smartAi.container to ext.smartAi.onContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Pill(provenance.label.t(), container, content, provenance.icon, modifier)
}

@Composable
fun DemoChip(modifier: Modifier = Modifier) = SourceChip(Provenance.DEMO, modifier)

@Composable
fun SmartAiChip(modifier: Modifier = Modifier) {
    val c = EasyTheme.colors.smartAi
    Pill(SProvenance.smartAiBadge.t(), c.container, c.onContainer, Icons.Rounded.AutoAwesome, modifier)
}

@Composable
fun RiskPill(risk: Risk, label: String, modifier: Modifier = Modifier) {
    val color = EasyTheme.colors.risk(risk)
    Pill(label, color.copy(alpha = 0.16f), color, if (risk == Risk.SAFE) null else Icons.Rounded.WarningAmber, modifier)
}

enum class BannerKind { INFO, WARNING, DANGER, SUCCESS }

@Composable
fun InfoBanner(
    text: String,
    kind: BannerKind = BannerKind.INFO,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val ext = EasyTheme.colors
    val accent = when (kind) {
        BannerKind.INFO -> MaterialTheme.colorScheme.primary
        BannerKind.WARNING -> ext.watch
        BannerKind.DANGER -> ext.crossed
        BannerKind.SUCCESS -> ext.safe
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                icon ?: if (kind == BannerKind.INFO) Icons.Rounded.Info else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (action != null) {
                    Spacer(Modifier.size(6.dp))
                    action()
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TintedIcon(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Primary action with a large touch target. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, containerColor: Color? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (containerColor != null) ButtonDefaults.buttonColors(containerColor = containerColor) else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Spacer(Modifier.size(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = accent, maxLines = 1)
    }
}

/**
 * Field on a confirm screen. Uncertain fields (low OCR/AI confidence) are highlighted so the user checks them
 * before saving (spec rule 6).
 */
@Composable
fun ConfirmField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    uncertain: Boolean = false,
    numeric: Boolean = false,
    supporting: String? = null,
    singleLine: Boolean = true,
) {
    val ext = EasyTheme.colors
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (uncertain) Modifier.border(2.dp, ext.watch, MaterialTheme.shapes.extraSmall) else Modifier)
                .semantics { if (uncertain) stateDescription = SCommon.checkThis.en },
            colors = if (uncertain) {
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = ext.uncertain.copy(alpha = 0.35f),
                    focusedContainerColor = ext.uncertain.copy(alpha = 0.2f),
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
            supportingText = when {
                uncertain -> ({ Text(SCommon.checkThis.t(), color = ext.watch) })
                supporting != null -> ({ Text(supporting) })
                else -> null
            },
        )
    }
}

@Composable
fun LoadingBox(text: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text(text ?: SCommon.loading.t(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun SettingsSwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SettingsNavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, trailing: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
