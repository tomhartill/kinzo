package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.theme.NumericStyle

/**
 * One row of the logging table: set number, last session's numbers, this set's weight and reps,
 * and a large done toggle. Completed rows invert to solid so progress reads at a glance.
 */
@Composable
fun SetRow(
    setNumber: Int,
    previous: String,
    weight: String,
    reps: String,
    done: Boolean,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (done) colors.surfaceContainerHigh else colors.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = KinzoTheme.spacing.touchTarget)
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = KinzoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
    ) {
        Text("$setNumber", style = NumericStyle, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Text(
            previous,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(weight, style = NumericStyle, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        Text(reps, style = NumericStyle, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        DoneToggle(setNumber = setNumber, done = done, onToggle = onToggleDone)
    }
}

@Composable
private fun DoneToggle(setNumber: Int, done: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(if (done) colors.primary else colors.surface, MaterialTheme.shapes.small)
            .border(1.dp, if (done) colors.primary else colors.outline, MaterialTheme.shapes.small)
            // toggleable exposes the checked state to TalkBack and VoiceOver.
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Set $setNumber done" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Column labels that sit above a list of [SetRow]s. */
@Composable
fun SetTableHeader(weightUnit: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = KinzoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
    ) {
        Text("SET", style = style, color = color, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Text("PREVIOUS", style = style, color = color, modifier = Modifier.weight(1f))
        Text(weightUnit.uppercase(), style = style, color = color, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        Text("REPS", style = style, color = color, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        Box(Modifier.width(36.dp))
    }
}
