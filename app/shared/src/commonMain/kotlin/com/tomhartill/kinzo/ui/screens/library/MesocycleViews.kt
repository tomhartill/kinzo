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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** "Week 3 of 12", "Starts 20 Oct" or "Ended 5 Jan". */
internal fun mesocycleStatus(mesocycle: Mesocycle, today: LocalDate): String = when {
    today < mesocycle.start -> "Starts ${Dates.dateLabel(mesocycle.start, today)}"
    today <= mesocycle.end -> "Week ${mesocycle.weekOf(today)} of ${mesocycle.weeks}"
    else -> "Ended ${Dates.dateLabel(mesocycle.end, today)}"
}

/** "1 week" or "12 weeks". */
internal fun weeksLabel(weeks: Int): String = if (weeks == 1) "1 week" else "$weeks weeks"

/**
 * What to say about how far along a mesocycle is: the week for the current one, the start for
 * one coming up, or its length once it's over.
 */
internal fun mesocycleProgress(mesocycle: Mesocycle, isCurrent: Boolean, today: LocalDate): String =
    if (isCurrent || today < mesocycle.start) mesocycleStatus(mesocycle, today) else weeksLabel(mesocycle.weeks)

/** The Mesocycles part of the Library tab: any coming up, the current block, and earlier ones. */
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
        val sections = listOf(
            "Up next" to state.upcoming,
            "Current" to state.mesocycles.filter { it.mesocycle == state.current },
            "Earlier" to state.earlier,
        )
        sections.forEach { (title, blocks) ->
            if (blocks.isNotEmpty()) {
                item(key = "header-$title") { SectionHeader(title, Modifier.padding(top = spacing.md)) }
                items(blocks, key = { it.mesocycle.id }) { block ->
                    val isCurrent = block.mesocycle == state.current
                    MesocycleCard(
                        block,
                        isCurrent = isCurrent,
                        upNext = if (isCurrent) state.upNext?.mesocycle else null,
                        today = today,
                        onClick = { onOpenMesocycle(block.mesocycle.id) },
                    )
                }
            }
        }
    }
}

/** [upNext] is the one planned to take over from the current one, if there is one yet. */
@Composable
private fun MesocycleCard(
    block: MesocycleWorkouts,
    isCurrent: Boolean,
    upNext: Mesocycle?,
    today: LocalDate,
    onClick: () -> Unit,
) {
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
        Text("${mesocycleProgress(mesocycle, isCurrent, today)} · $workouts", style = MaterialTheme.typography.bodyMedium)
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
                    if (upNext != null) {
                        "${upNext.name} starts ${Dates.dateLabel(upNext.start, today)}."
                    } else {
                        "Time for a change: start the next mesocycle with new exercises."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The name and dates of a mesocycle, to start the next one or change one. Its start has to stay
 * after [startAfter]'s and before [startBefore]'s, which keeps the mesocycles in order. When
 * [startingNext], [startAfter] is the latest mesocycle: the form says when it will end if the
 * new one cuts it short, and offers to copy its [copyCount] workouts across.
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
    startBefore: Mesocycle? = null,
    copyCount: Int = 0,
    startingNext: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var copyWorkouts by remember { mutableStateOf(true) }
    val startProblem = when {
        startAfter != null && start <= startAfter.start ->
            "It has to start after ${startAfter.name} does (${Dates.dateLabel(startAfter.start, today)})."
        startBefore != null && start >= startBefore.start ->
            "It has to start before ${startBefore.name} does (${Dates.dateLabel(startBefore.start, today)})."
        else -> null
    }
    val problem = if (end < start) "It ends before it starts." else startProblem
    // Starting the next one ends the latest the day before, when it would otherwise overlap.
    val cutShort = startAfter?.takeIf { startingNext && problem == null && start <= it.end }
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
                    isError = startProblem != null,
                )
                DateField(
                    label = "Ends",
                    date = end,
                    dateText = Dates.dateLabel(end, today),
                    onDateChange = { end = it },
                    isError = end < start,
                )
                Text(
                    problem ?: weeksLabel(Mesocycle("", "", start, end).weeks),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) colors.error else colors.onSurfaceVariant,
                )
                if (cutShort != null) {
                    Text(
                        "${cutShort.name} will end on ${Dates.dateLabel(start.minus(1, DateTimeUnit.DAY), today)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (startingNext && startAfter != null && copyCount > 0) {
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
