package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The text field colours used across the app: a filled field with no outline, standing apart by
 * fill alone like everything else. Focus shows as an accent underline, errors as a red one.
 */
@Composable
fun kinzoTextFieldColors(): TextFieldColors {
    val colors = KinzoTheme.colors
    return TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceHigh,
        unfocusedContainerColor = colors.surfaceHigh,
        disabledContainerColor = colors.surfaceHigh,
        errorContainerColor = colors.surfaceHigh,
        focusedIndicatorColor = colors.accent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = colors.danger,
        focusedLabelColor = colors.text,
        unfocusedLabelColor = colors.textMuted,
        cursorColor = colors.text,
    )
}

/**
 * A labelled field that opens a menu of choices, styled like the text fields beside it. The
 * [menu] gets a function that closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.small,
            colors = kinzoTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            menu { expanded = false }
        }
    }
}

/** One choice in a [DropdownField], ticked when it's the current one. */
@Composable
fun DropdownChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = {
            if (supportingText == null) {
                Text(text)
            } else {
                Column {
                    Text(text)
                    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        },
        onClick = onClick,
        leadingIcon = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.onSurface)
        },
        trailingIcon = trailing,
    )
}

/** A date in a field like the text fields beside it. Tapping it opens a calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    dateText: String,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // A read-only field doesn't take clicks, so a transparent button sits over it.
    Box(modifier) {
        TextField(
            value = dateText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = isError,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = kinzoTextFieldColors(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Change ${label.lowercase()}", role = Role.Button) { picking = true },
        )
    }
    if (picking) {
        // The calendar works in UTC midnights.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    }
                    picking = false
                }) {
                    Text("OK", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = colors.surface),
        ) {
            DatePicker(state = state, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = colors.surface))
        }
    }
}
