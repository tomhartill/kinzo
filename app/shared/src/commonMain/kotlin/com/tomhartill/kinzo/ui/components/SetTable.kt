package com.tomhartill.kinzo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Parse
import com.tomhartill.kinzo.domain.PreviousSet
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Weight as Weights
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.theme.NumericStyle
import kotlinx.coroutines.delay
import kotlin.math.round
import kotlin.math.roundToInt

private val SetColumnWidth = 32.dp
private val DoneColumnWidth = 40.dp

/** One number the user types into a set row. Which ones appear depends on the exercise type. */
private enum class SetField(val decimal: Boolean) {
    Weight(decimal = true),
    Reps(decimal = false),
    Seconds(decimal = false),
    Kilometres(decimal = true),
    Minutes(decimal = true);

    fun header(unit: WeightUnit): String = when (this) {
        Weight -> unit.label.uppercase()
        Reps -> "REPS"
        Seconds -> "SEC"
        Kilometres -> "KM"
        Minutes -> "MIN"
    }

    /** What screen readers call this field. */
    val spokenName: String
        get() = when (this) {
            Weight -> "weight"
            Reps -> "reps"
            Seconds -> "seconds"
            Kilometres -> "distance"
            Minutes -> "time"
        }

    /** The stored value as the user would type it, or "" when there is none. */
    fun format(values: SetValues, unit: WeightUnit): String = when (this) {
        Weight -> values.weightKg?.let { Weights.format(it, unit) }
        Reps -> values.reps?.toString()
        Seconds -> values.durationS?.toString()
        Kilometres -> values.distanceM?.let { Format.kilometres(it) }
        Minutes -> values.durationS?.let { Format.decimal(it / 60.0, maxDecimals = 2) }
    }.orEmpty()

    /** [values] with this field replaced by what [text] says, in storage units. */
    fun applyTo(values: SetValues, text: String, unit: WeightUnit): SetValues = when (this) {
        Weight -> values.copy(weightKg = Parse.decimal(text)?.let { SetRules.roundKg(Weights.toKg(it, unit)) })
        Reps -> values.copy(reps = Parse.wholeNumber(text))
        Seconds -> values.copy(durationS = Parse.wholeNumber(text))
        Kilometres -> values.copy(distanceM = Parse.decimal(text)?.let { round(it * 10_000) / 10 })
        Minutes -> values.copy(durationS = Parse.decimal(text)?.let { (it * 60).roundToInt() })
    }

    /** Allows only text that [Parse] can read, plus a trailing decimal mark while typing. */
    fun accepts(text: String): Boolean = (if (decimal) DecimalInput else WholeInput).matches(text)

    /** Whether this field is what keeps a set with these values (blanks already filled) from counting. */
    fun isMissing(values: SetValues): Boolean = when (this) {
        Weight -> values.weightKg == null
        Reps -> (values.reps ?: 0) <= 0
        Seconds -> (values.durationS ?: 0) <= 0
        Kilometres, Minutes -> (values.distanceM ?: 0.0) <= 0.0 && (values.durationS ?: 0) <= 0
    }

    val keyboardType: KeyboardType get() = if (decimal) KeyboardType.Decimal else KeyboardType.Number

    companion object {
        private val DecimalInput = Regex("""^\d{0,5}([.,]\d{0,3})?$""")
        private val WholeInput = Regex("""^\d{0,5}$""")

        fun forType(type: MeasurementType): List<SetField> = when (type) {
            MeasurementType.WeightReps -> listOf(Weight, Reps)
            MeasurementType.Reps -> listOf(Reps)
            MeasurementType.Duration -> listOf(Seconds)
            MeasurementType.DistanceDuration -> listOf(Kilometres, Minutes)
        }
    }
}

private fun fieldWidth(count: Int) = if (count == 1) 96.dp else 68.dp

/** Column labels above a list of [SetEditorRow]s or [SetSummaryRow]s. */
@Composable
fun SetTableHeader(type: MeasurementType, unit: WeightUnit, modifier: Modifier = Modifier, showDone: Boolean = true) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val fields = SetField.forType(type)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = KinzoTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
    ) {
        Text("SET", style = style, color = color, modifier = Modifier.width(SetColumnWidth), textAlign = TextAlign.Center)
        Text("PREVIOUS", style = style, color = color, modifier = Modifier.weight(1f))
        fields.forEach { field ->
            Text(field.header(unit), style = style, color = color, modifier = Modifier.width(fieldWidth(fields.size)), textAlign = TextAlign.Center)
        }
        if (showDone) Box(Modifier.width(DoneColumnWidth))
    }
}

/**
 * One set while logging: its number (tap for warm-up and delete), last time's numbers (tap to
 * copy them), the inputs, and the done toggle. Blank inputs show last time's values as hints,
 * and ticking a set with blanks uses those hints. Every keystroke is saved straight away.
 */
@Composable
fun SetEditorRow(
    label: String,
    set: LoggedSet,
    previous: PreviousSet?,
    type: MeasurementType,
    unit: WeightUnit,
    onValuesChange: (SetValues) -> Unit,
    onToggleDone: (entered: SetValues) -> Boolean,
    onToggleWarmup: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fields = SetField.forType(type)
    val texts = remember(set.id, unit) {
        mutableStateMapOf<SetField, String>().apply { fields.forEach { put(it, it.format(set.values, unit)) } }
    }
    var focusedField by remember(set.id) { mutableStateOf<SetField?>(null) }
    // Fields that stopped the last tick, outlined in red for a moment.
    var missing by remember(set.id) { mutableStateOf(emptySet<SetField>()) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    // What the fields say, in storage units. A field still showing the stored value keeps the
    // exact stored number, so 42.195 km shown as "42.2" isn't saved back as 42.2.
    fun entered(): SetValues = fields.fold(set.values.keepOnly(type)) { values, field ->
        val text = texts[field].orEmpty()
        if (text == field.format(set.values, unit)) values else field.applyTo(values, text, unit)
    }

    // Pick up changes that didn't come from typing here (copying last time, ticking with
    // blanks), but never rewrite the field the user is typing in.
    LaunchedEffect(set.values, unit, focusedField) {
        fields.forEach { field ->
            if (field == focusedField) return@forEach
            val stored = field.format(set.values, unit)
            val typed = field.format(field.applyTo(SetValues(), texts[field].orEmpty(), unit), unit)
            if (stored != typed) texts[field] = stored
        }
    }
    LaunchedEffect(missing) {
        if (missing.isNotEmpty()) {
            delay(2_000)
            missing = emptySet()
        }
    }

    val background by animateColorAsState(if (set.isCompleted) colors.surfaceContainerHigh else Color.Transparent)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = KinzoTheme.spacing.touchTarget)
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = KinzoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
    ) {
        SetLabel(label = label, isWarmup = set.isWarmup, onToggleWarmup = onToggleWarmup, onRemove = onRemove)
        Text(
            text = previous?.let { Format.set(type, it.values, unit, compact = true) } ?: "–",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (previous != null && !set.isCompleted) {
                        Modifier.clickable(onClickLabel = "Copy last time's numbers") {
                            fields.forEach { texts[it] = it.format(previous.values, unit) }
                            onValuesChange(previous.values.keepOnly(type))
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = KinzoTheme.spacing.sm),
        )
        fields.forEach { field ->
            SetInput(
                value = texts[field].orEmpty(),
                placeholder = previous?.let { field.format(it.values, unit) }.orEmpty(),
                onValueChange = { text ->
                    if (field.accepts(text)) {
                        texts[field] = text
                        onValuesChange(entered())
                    }
                },
                keyboardType = field.keyboardType,
                isError = field in missing,
                isCompleted = set.isCompleted,
                onFocusChange = { focused ->
                    if (focused) {
                        focusedField = field
                    } else if (focusedField == field) {
                        focusedField = null
                    }
                },
                description = "Set $label ${field.spokenName}",
                modifier = Modifier.width(fieldWidth(fields.size)),
            )
        }
        DoneToggle(
            label = label,
            done = set.isCompleted,
            problem = missing.takeIf { it.isNotEmpty() }?.let { fields -> "Needs " + fields.joinToString(" and ") { it.spokenName } },
            onToggle = {
                focusManager.clearFocus()
                val values = entered()
                if (onToggleDone(values)) {
                    missing = emptySet()
                    if (!set.isCompleted) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    val filled = SetRules.fillBlanks(type, values, previous?.values)
                    missing = fields.filter { it.isMissing(filled) }.toSet().ifEmpty { fields.toSet() }
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
            },
        )
    }
}

@Composable
private fun SetLabel(label: String, isWarmup: Boolean, onToggleWarmup: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Text(
            text = label,
            style = NumericStyle,
            color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(SetColumnWidth)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Set options") { menuOpen = true }
                .padding(vertical = KinzoTheme.spacing.sm),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isWarmup) "Mark as working set" else "Mark as warm-up") },
                onClick = {
                    menuOpen = false
                    onToggleWarmup()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete set", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun SetInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isError: Boolean,
    isCompleted: Boolean,
    onFocusChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = NumericStyle.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        // Next moves along the row and down the sets; after the last field it closes the keyboard.
        keyboardActions = KeyboardActions(onNext = { if (!focusManager.moveFocus(FocusDirection.Next)) focusManager.clearFocus() }),
        cursorBrush = SolidColor(colors.onSurface),
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .semantics {
                contentDescription = description
                if (isError) error("Enter a number")
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isCompleted) Color.Transparent else colors.surfaceContainerHigh, MaterialTheme.shapes.small)
                    .border(1.dp, if (isError) colors.error else Color.Transparent, MaterialTheme.shapes.small)
                    .padding(horizontal = KinzoTheme.spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = NumericStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}

/** The tick box. [problem] says why the last tick didn't count, and is read out by screen readers. */
@Composable
private fun DoneToggle(label: String, done: Boolean, problem: String?, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (done) colors.primary else Color.Transparent)
    Box(
        modifier = Modifier
            .size(DoneColumnWidth)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .border(1.dp, if (done) colors.primary else if (problem != null) colors.error else colors.outline, MaterialTheme.shapes.small)
            // toggleable exposes the checked state to TalkBack and VoiceOver.
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = "Set $label done"
                if (problem != null) stateDescription = problem
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** A finished set, read-only: its number and what was done. */
@Composable
fun SetSummaryRow(label: String, text: String, isWarmup: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = KinzoTheme.spacing.xs, vertical = KinzoTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = NumericStyle,
            color = if (isWarmup) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier.width(SetColumnWidth),
            textAlign = TextAlign.Center,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
    }
}
