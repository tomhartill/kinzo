package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.ui.components.DateField
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.kinzoTextFieldColors
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.LocalDate

/** "Week 3 of 12", "Starts 20 Oct" or "Ended 5 Jan". */
internal fun mesocycleStatus(mesocycle: Mesocycle, today: LocalDate): String = when {
    today < mesocycle.start -> "Starts ${Dates.dateLabel(mesocycle.start, today)}"
    today <= mesocycle.end -> "Week ${mesocycle.weekOf(today)} of ${mesocycle.weeks}"
    else -> "Ended ${Dates.dateLabel(mesocycle.end, today)}"
}

/** The Mesocycles half of the Library tab: the current block, the next one, and the past ones. */
@Composable
internal fun MesocycleList(
    state: LibraryState,
    onStartNext: () -> Unit,
    onOpenMesocycle: (mesocycleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = KinzoTheme.spacing
    val today = state.today ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "about") {
            Text(
                "A mesocycle is a block of training, usually a few months, with its own workouts. When your body " +
                    "has got used to them, start the next one and change the exercises.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "start-next") { PrimaryButton(text = "Start next mesocycle", onClick = onStartNext) }
        val current = state.mesocycles.firstOrNull()
        if (current != null) {
            item(key = "current-header") { SectionHeader("Current", Modifier.padding(top = spacing.md)) }
            item(key = current.mesocycle.id) {
                MesocycleCard(current, isCurrent = true, today = today, onClick = { onOpenMesocycle(current.mesocycle.id) })
            }
        }
        val past = state.mesocycles.drop(1)
        if (past.isNotEmpty()) {
            item(key = "past-header") { SectionHeader("Earlier", Modifier.padding(top = spacing.md)) }
            items(past, key = { it.mesocycle.id }) { block ->
                MesocycleCard(block, isCurrent = false, today = today, onClick = { onOpenMesocycle(block.mesocycle.id) })
            }
        }
    }
}

@Composable
private fun MesocycleCard(block: MesocycleWorkouts, isCurrent: Boolean, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val mesocycle = block.mesocycle
    val count = block.workouts.size
    KinzoCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mesocycle.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) {
                    Text(
                        "CURRENT",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .border(1.dp, colors.onSurface, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                Dates.dateRangeLabel(mesocycle.start, mesocycle.end, today),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        val workouts = if (count == 1) "1 workout" else "$count workouts"
        val status = if (isCurrent) mesocycleStatus(mesocycle, today) else "${mesocycle.weeks} weeks"
        Text("$status · $workouts", style = MaterialTheme.typography.bodyMedium)
        if (isCurrent) {
            val week = mesocycle.weekOf(today)
            if (week != null && today <= mesocycle.end) {
                LinearProgressIndicator(
                    progress = { week.toFloat() / mesocycle.weeks },
                    modifier = Modifier.fillMaxWidth().semantics { stateDescription = "Week $week of ${mesocycle.weeks}" },
                    color = colors.onSurface,
                    trackColor = colors.outlineVariant,
                    drawStopIndicator = {},
                )
            } else if (today > mesocycle.end) {
                Text(
                    "Time for a change: start the next mesocycle with new exercises.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The name and dates of a mesocycle, to start the next one or change one. When starting the
 * next, [startAfter] is the current one, which it must start after, and [copyCount] is how many
 * workouts it has to copy across.
 */
@Composable
internal fun MesocycleFormDialog(
    title: String,
    confirmLabel: String,
    initial: MesocycleDraft,
    today: LocalDate,
    defaultEnd: (LocalDate) -> LocalDate,
    onConfirm: (MesocycleDraft, copyWorkouts: Boolean) -> Unit,
    onDismiss: () -> Unit,
    startAfter: Mesocycle? = null,
    copyCount: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var copyWorkouts by remember { mutableStateOf(true) }
    val problem = when {
        end < start -> "It ends before it starts."
        startAfter != null && start <= startAfter.start ->
            "It has to start after ${startAfter.name} does (${Dates.dateLabel(startAfter.start, today)})."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.small,
                    colors = kinzoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "Starts",
                    date = start,
                    dateText = Dates.dateLabel(start, today),
                    onDateChange = { picked ->
                        // An end date left at its default moves with the start.
                        if (end == defaultEnd(start)) end = defaultEnd(picked)
                        start = picked
                    },
                    isError = startAfter != null && start <= startAfter.start,
                )
                DateField(
                    label = "Ends",
                    date = end,
                    dateText = Dates.dateLabel(end, today),
                    onDateChange = { end = it },
                    isError = end < start,
                )
                Text(
                    problem ?: Mesocycle("", "", start, end).weeks.let { if (it == 1) "1 week" else "$it weeks" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) colors.error else colors.onSurfaceVariant,
                )
                if (startAfter != null && copyCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Copy my workouts", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (copyCount == 1) "Brings the workout" else "Brings the $copyCount workouts") +
                                    " from ${startAfter.name} across, ready to change their exercises.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = copyWorkouts,
                            onCheckedChange = { copyWorkouts = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surface,
                                checkedTrackColor = colors.onSurface,
                                uncheckedThumbColor = colors.outline,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.outline,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(MesocycleDraft(name.trim(), start, end), copyWorkouts) }, enabled = problem == null) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (problem == null) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surface,
        shape = MaterialTheme.shapes.large,
    )
}
