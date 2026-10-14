package com.tomhartill.kinzo.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.Weight
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.Workout
import com.tomhartill.kinzo.domain.WorkoutExercise
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.SetSummaryRow
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToLong

/**
 * A finished workout, set by set. Straight after finishing ([justFinished]) it doubles as the
 * end-of-workout summary, with a Done button back to the tabs.
 */
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    justFinished: Boolean,
    onClose: () -> Unit,
    viewModel: WorkoutDetailViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workout = state.workout
    val colors = MaterialTheme.colorScheme
    val spacing = KinzoTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Deleted, here or elsewhere: nothing left to show.
    LaunchedEffect(state.loading, workout == null) {
        if (!state.loading && workout == null) onClose()
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(
            title = if (justFinished) "Summary" else "Workout",
            onBack = onClose,
            backIcon = if (justFinished) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
            backLabel = if (justFinished) "Close" else "Back",
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Delete workout", color = colors.error) }, onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                    }
                }
            },
        )
        if (workout != null) {
            val navigationBars = WindowInsets.navigationBars.asPaddingValues()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = spacing.screen,
                    end = spacing.screen,
                    top = spacing.sm,
                    bottom = spacing.xxl + navigationBars.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                item(key = "header") { Header(workout, justFinished) }
                item(key = "stats") { Stats(workout, state.unit) }
                items(workout.exercises, key = { it.id }) { exercise -> ExerciseSummary(exercise, state.unit) }
                if (justFinished) {
                    item(key = "done") { PrimaryButton(text = "Done", onClick = onClose) }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete workout?",
            message = "It will be removed from your history and stats.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onClose)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Header(workout: Workout, justFinished: Boolean) {
    val zone = remember { TimeZone.currentSystemDefault() }
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
        if (justFinished) SectionHeader("Workout complete")
        Text(workout.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${Dates.longDayLabel(workout.startedAt, zone)} · ${Dates.timeLabel(workout.startedAt, zone)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stats(workout: Workout, unit: WeightUnit) {
    val minutes = ((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 60_000
    val (length, lengthUnit) = when {
        minutes < 1 -> "<1" to "min"
        minutes < 60 -> minutes.toString() to "min"
        else -> "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}" to "h"
    }
    KinzoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
            StatTile(value = length, unit = lengthUnit, label = "Time", compact = true, modifier = Modifier.weight(1f))
            StatTile(value = workout.completedSetCount.toString(), label = "Sets", compact = true, modifier = Modifier.weight(1f))
            StatTile(
                value = Format.thousands(Weight.fromKg(workout.volumeKg, unit).roundToLong()),
                unit = unit.label,
                label = "Volume",
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExerciseSummary(exercise: WorkoutExercise, unit: WeightUnit) {
    val labels = SetRules.labels(exercise.sets.map { it.isWarmup })
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
        Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = KinzoTheme.spacing.xs))
        exercise.sets.forEachIndexed { index, set ->
            SetSummaryRow(
                label = labels[index],
                text = Format.set(exercise.measurementType, set.values, unit),
                isWarmup = set.isWarmup,
            )
        }
    }
}
