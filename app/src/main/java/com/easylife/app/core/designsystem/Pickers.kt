package com.easylife.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.easylife.app.core.i18n.Fmt
import com.easylife.app.core.i18n.SCommon
import com.easylife.app.core.i18n.currentLanguage
import com.easylife.app.core.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.pickerMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Read-only field that opens a date picker. Highlighted when [uncertain] (spec rule 6). */
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    uncertain: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val lang = currentLanguage()
    val interaction = remember { MutableInteractionSource() }
    Box0(modifier, onClick = { open = true }, interaction = interaction) {
        ConfirmField(
            label = label,
            value = value?.let { Fmt.date(it, lang) } ?: "",
            onValueChange = {},
            uncertain = uncertain,
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: LocalDate.now()).toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.pickerMillisToDate())
                    open = false
                }) { Text(SCommon.ok.t()) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(SCommon.cancel.t()) } },
        ) { DatePicker(state = state) }
    }
}

/** A box whose whole area (including the read-only text field inside) opens the picker. */
@Composable
private fun Box0(modifier: Modifier, onClick: () -> Unit, interaction: MutableInteractionSource, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
        content()
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        )
    }
}

