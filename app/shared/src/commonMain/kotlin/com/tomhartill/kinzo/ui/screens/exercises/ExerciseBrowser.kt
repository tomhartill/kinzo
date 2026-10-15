package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Equipment
import com.tomhartill.kinzo.domain.ExerciseListRow
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Muscle
import com.tomhartill.kinzo.domain.Trend
import com.tomhartill.kinzo.ui.components.ExerciseIcon
import com.tomhartill.kinzo.ui.components.SearchField
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.TrendSparkline
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/**
 * Search box, muscle and equipment filters, and the exercise list. With [selectedIds] the
 * rows show a tick for picking several exercises; without it a tap opens the exercise.
 * Exercises with two or more sessions show a sparkline of their recent progress. Variations of
 * one movement fold into a group row that opens in place; a search lists them directly.
 * [header] goes above the list, under the filters, for example a button to scan a machine.
 */
@Composable
fun ExerciseBrowser(
    query: String,
    state: ExerciseBrowserState,
    onQueryChange: (String) -> Unit,
    onMuscleChange: (Muscle?) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onExerciseClick: (ExerciseSummary) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: List<String>? = null,
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // Groups the user has opened. A list rather than a set, so it saves on every platform.
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    // A new search or filter starts back at the top, so the best matches aren't scrolled past.
    // Only on a change: coming back to the list keeps its place.
    val filters = listOf(state.query, state.muscle?.key, state.equipment?.id)
    var resultsFor by remember { mutableStateOf(filters) }
    LaunchedEffect(filters) {
        if (filters != resultsFor) {
            resultsFor = filters
            listState.scrollToItem(0)
        }
    }
    Column(modifier) {
        Column(
            modifier = Modifier.padding(horizontal = KinzoTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
        ) {
            SearchField(query = query, onQueryChange = onQueryChange, placeholder = "Search exercises")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
            ) {
                FilterMenu(
                    label = state.muscle?.label ?: "Any muscle",
                    active = state.muscle != null,
                    options = Muscle.entries.sortedBy { it.label },
                    optionLabel = { it.label },
                    selected = state.muscle,
                    clearLabel = "Any muscle",
                    onSelect = onMuscleChange,
                )
                FilterMenu(
                    label = state.equipment?.name ?: "Any equipment",
                    active = state.equipment != null,
                    options = state.equipmentOptions,
                    optionLabel = { it.name },
                    selected = state.equipment,
                    clearLabel = "Any equipment",
                    onSelect = onEquipmentChange,
                )
            }
            header?.invoke()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(top = KinzoTheme.spacing.sm),
            contentPadding = contentPadding,
        ) {
            when {
                state.loading -> item { Message("Loading exercises…") }
                state.results.isEmpty() -> item { Message("No exercises match. Try fewer words or clear a filter.") }
                else -> {
                    if (!state.isFiltering && state.recent.isNotEmpty()) {
                        section("Recent")
                        exerciseRows(state.recent, "recent", state.trends, selectedIds, onExerciseClick)
                        section("All exercises")
                    }
                    if (state.query.isBlank()) {
                        groupedRows(
                            rows = state.rows,
                            expanded = expanded,
                            onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
                            trends = state.trends,
                            selectedIds = selectedIds,
                            onClick = onExerciseClick,
                        )
                    } else {
                        exerciseRows(state.results, "all", state.trends, selectedIds, onExerciseClick)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String) {
    item(key = "header-$title") {
        SectionHeader(
            title,
            modifier = Modifier.padding(
                start = KinzoTheme.spacing.screen,
                end = KinzoTheme.spacing.screen,
                top = KinzoTheme.spacing.lg,
                bottom = KinzoTheme.spacing.xs,
            ),
        )
    }
}

private fun LazyListScope.exerciseRows(
    exercises: List<ExerciseSummary>,
    group: String,
    trends: Map<String, Trend>,
    selectedIds: List<String>?,
    onClick: (ExerciseSummary) -> Unit,
) {
    items(exercises, key = { "$group-${it.id}" }) { exercise ->
        ExerciseRow(
            exercise = exercise,
            trend = trends[exercise.id],
            selected = selectedIds?.contains(exercise.id),
            onClick = { onClick(exercise) },
        )
    }
}

/** The list with groups of variations as rows that open in place to show their exercises. */
private fun LazyListScope.groupedRows(
    rows: List<ExerciseListRow>,
    expanded: List<String>,
    onToggle: (String) -> Unit,
    trends: Map<String, Trend>,
    selectedIds: List<String>?,
    onClick: (ExerciseSummary) -> Unit,
) {
    rows.forEach { row ->
        when (row) {
            is ExerciseListRow.Single -> item(key = "all-${row.exercise.id}") {
                ExerciseRow(
                    exercise = row.exercise,
                    trend = trends[row.exercise.id],
                    selected = selectedIds?.contains(row.exercise.id),
                    onClick = { onClick(row.exercise) },
                )
            }
            is ExerciseListRow.Group -> {
                val open = row.id in expanded
                item(key = "all-${row.key}") {
                    GroupRow(
                        group = row,
                        open = open,
                        selectedCount = selectedIds?.count { id -> row.exercises.any { it.id == id } } ?: 0,
                        onClick = { onToggle(row.id) },
                    )
                }
                if (open) {
                    items(row.exercises, key = { "all-${row.key}-${it.id}" }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            trend = trends[exercise.id],
                            selected = selectedIds?.contains(exercise.id),
                            onClick = { onClick(exercise) },
                            indent = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: ExerciseListRow.Group, open: Boolean, selectedCount: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val details = buildString {
        append("${group.exercises.size} variations")
        if (selectedCount > 0) append(" · $selectedCount picked")
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = if (open) "Hide variations" else "Show variations", onClick = onClick)
                .background(colors.background)
                .padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
        ) {
            ExerciseIcon(group.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedCount > 0) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.outlineVariant,
            modifier = Modifier.padding(start = KinzoTheme.spacing.screen + 44.dp + KinzoTheme.spacing.md),
        )
    }
}

@Composable
internal fun ExerciseRow(exercise: ExerciseSummary, trend: Trend?, selected: Boolean?, onClick: () -> Unit, indent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Variations inside an open group sit in from the edge, under their group.
    val start = if (indent) KinzoTheme.spacing.screen + KinzoTheme.spacing.lg else KinzoTheme.spacing.screen
    val details = listOfNotNull(
        exercise.primaryMuscles.joinToString(", ") { it.label }.ifEmpty { null },
        exercise.equipmentName,
    ).joinToString(" · ")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    // In the picker each row is a checkbox, so screen readers say whether it's ticked.
                    if (selected != null) {
                        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    },
                )
                .background(if (selected == true) colors.surfaceContainerHigh else colors.background)
                .padding(start = start, end = KinzoTheme.spacing.screen, top = KinzoTheme.spacing.md, bottom = KinzoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
        ) {
            ExerciseIcon(exercise.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trend != null) TrendSparkline(trend)
            if (selected != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (selected) colors.primary else colors.background)
                        .border(1.5.dp, if (selected) colors.primary else colors.outline, MaterialTheme.shapes.extraSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // Starts under the name, past the pictogram.
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.outlineVariant,
            modifier = Modifier.padding(start = start + 44.dp + KinzoTheme.spacing.md),
        )
    }
}

@Composable
private fun <T> FilterMenu(
    label: String,
    active: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T?,
    clearLabel: String,
    onSelect: (T?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(if (active) colors.primary else colors.surfaceContainerHigh)
                .clickable(onClickLabel = "Change filter") { open = true }
                .padding(start = KinzoTheme.spacing.md, end = KinzoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) colors.onPrimary else colors.onSurface)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = if (active) colors.onPrimary else colors.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(clearLabel) },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.xl),
    )
}
