package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.restLabel
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class DetailDialog { Busy, Delete }

/** A saved workout: who made it, each exercise's plan, and the button to start it. */
@Composable
fun RoutineDetailScreen(
    routineId: String,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    viewModel: RoutineDetailViewModel = koinViewModel { parametersOf(routineId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routine = state.routine
    val colors = MaterialTheme.colorScheme
    val spacing = KinzoTheme.spacing
    val listState = rememberLazyListState()
    // The name moves into the top bar once its headline scrolls away.
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // Deleted here or elsewhere: there's nothing left to show.
    LaunchedEffect(state.loading, routine == null) {
        if (!state.loading && routine == null) onBack()
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(
            title = if (showTitle) routine?.name.orEmpty() else "",
            onBack = onBack,
            actions = {
                if (routine?.isEditable == true) {
                    TextButton(onClick = onEdit) {
                        Text("Edit", color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (routine != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (routine.isEditable) {
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                    menuOpen = false
                                    onCopy()
                                })
                                DropdownMenuItem(text = { Text("Delete workout", color = colors.error) }, onClick = {
                                    menuOpen = false
                                    dialog = DetailDialog.Delete
                                })
                            } else {
                                DropdownMenuItem(text = { Text("Copy to my workouts") }, onClick = {
                                    menuOpen = false
                                    onCopy()
                                })
                            }
                        }
                    }
                }
            },
        )
        if (routine != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item(key = "header") { Header(routine, state.mesocycleName) }
                item(key = "exercises-header") { SectionHeader("Exercises", Modifier.padding(top = spacing.md)) }
                if (routine.exercises.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(title = "No exercises yet", body = "Edit this workout to add the exercises you want to do.")
                    }
                }
                items(routine.exercises, key = { it.id }) { exercise ->
                    PlanRow(
                        exercise = exercise,
                        unit = state.settings.weightUnit,
                        restSeconds = exercise.restSeconds ?: state.settings.restSeconds,
                        onOpenInfo = { onOpenExercise(exercise.exerciseId) },
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PrimaryButton(
                    text = "Start workout",
                    onClick = { viewModel.start(onStarted = onStarted, onBusy = { dialog = DetailDialog.Busy }) },
                )
                if (!routine.isEditable) {
                    SecondaryButton(text = "Copy to my workouts", onClick = onCopy)
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    when (dialog) {
        null -> Unit
        DetailDialog.Busy -> ConfirmDialog(
            title = "A workout is in progress",
            message = "Finish or discard it before starting another.",
            confirmLabel = "Open it",
            onConfirm = {
                dialog = null
                onStarted()
            },
            onDismiss = { dialog = null },
        )
        DetailDialog.Delete -> ConfirmDialog(
            title = "Delete ${routine?.name.orEmpty()}?",
            message = "Workouts you've already logged from it stay in your history.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.delete()
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Header(routine: Routine, mesocycleName: String?) {
    val colors = MaterialTheme.colorScheme
    val madeBy = when (routine.source) {
        RoutineSource.User -> mesocycleName ?: "Your workout"
        RoutineSource.Kinzo -> "Ready-made"
        RoutineSource.Ai -> "Made by the AI coach"
    }
    val count = routine.exercises.size
    val summary = listOf(
        madeBy,
        if (count == 1) "1 exercise" else "$count exercises",
        if (routine.totalSets == 1) "1 set" else "${routine.totalSets} sets",
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
        Text(routine.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(routine.type.label, routine.splitName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        val notes = routine.notes
        if (notes != null) {
            Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = KinzoTheme.spacing.xs))
        }
    }
}

/** One exercise's plan: "3 × 8 · 60 kg", its rest, and a way to read how to do it. */
@Composable
private fun PlanRow(exercise: RoutineExercise, unit: WeightUnit, restSeconds: Int, onOpenInfo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val plan = listOf(
        Format.target(exercise.measurementType, exercise.targetSets, exercise.target, unit),
        if (restSeconds > 0) "Rest ${restLabel(restSeconds)}" else "No rest timer",
    ).joinToString(" · ")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "How to do ${exercise.name}", onClick = onOpenInfo)
                .padding(vertical = KinzoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(plan, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
    }
}
