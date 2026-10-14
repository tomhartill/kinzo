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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Equipment
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Muscle
import com.tomhartill.kinzo.ui.components.ExerciseIcon
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/**
 * Search box, muscle and equipment filters, and the exercise list. With [selectedIds] the
 * rows show a tick for picking several exercises; without it a tap opens the exercise.
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
) {
    val listState = rememberLazyListState()
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
            SearchField(query = query, onQueryChange = onQueryChange)
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
                        exerciseRows(state.recent, "recent", selectedIds, onExerciseClick)
                        section("All exercises")
                    }
                    exerciseRows(state.results, "all", selectedIds, onExerciseClick)
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
    selectedIds: List<String>?,
    onClick: (ExerciseSummary) -> Unit,
) {
    items(exercises, key = { "$group-${it.id}" }) { exercise ->
        ExerciseRow(
            exercise = exercise,
            selected = selectedIds?.contains(exercise.id),
            onClick = { onClick(exercise) },
        )
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseSummary, selected: Boolean?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
                .padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
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
            modifier = Modifier.padding(start = KinzoTheme.spacing.screen + 44.dp + KinzoTheme.spacing.md),
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search exercises" },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = KinzoTheme.spacing.touchTarget)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerHigh)
                    .padding(start = KinzoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(horizontal = KinzoTheme.spacing.sm)) {
                    if (query.isEmpty()) {
                        Text("Search exercises", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = colors.onSurfaceVariant)
                    }
                }
            }
        },
    )
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
