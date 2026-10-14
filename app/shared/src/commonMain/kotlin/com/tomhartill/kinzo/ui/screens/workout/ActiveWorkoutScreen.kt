package com.tomhartill.kinzo.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutExercise
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.RestTimerBar
import com.tomhartill.kinzo.ui.components.SetEditorRow
import com.tomhartill.kinzo.ui.components.SetTableHeader
import com.tomhartill.kinzo.ui.components.TextInputDialog
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

private sealed interface WorkoutDialog {
    data object Rename : WorkoutDialog
    data object Discard : WorkoutDialog
    data object NothingCompleted : WorkoutDialog
    data class ConfirmFinish(val unticked: Int) : WorkoutDialog
    data class RemoveExercise(val exercise: WorkoutExercise) : WorkoutDialog
}

/** The workout in progress: one section per exercise with its sets, and the rest timer. */
@Composable
fun ActiveWorkoutScreen(
    onMinimise: () -> Unit,
    onAddExercises: () -> Unit,
    onFinished: (workoutId: String) -> Unit,
    onClosed: () -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val workout = state.workout
    val colors = MaterialTheme.colorScheme

    // The workout can end elsewhere (finished, discarded): there is nothing left to show.
    LaunchedEffect(state.loading, workout == null) {
        if (!state.loading && workout == null) onClosed()
    }
    if (workout == null) {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }

    var dialog by remember { mutableStateOf<WorkoutDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val now by rememberNow()
    val unit = state.settings.weightUnit
    val focusManager = LocalFocusManager.current

    fun requestFinish() {
        val completed = workout.completedSetCount
        val unticked = workout.exercises.sumOf { exercise -> exercise.sets.count { !it.isCompleted } }
        dialog = when {
            completed == 0 -> WorkoutDialog.NothingCompleted
            unticked > 0 -> WorkoutDialog.ConfirmFinish(unticked)
            else -> null.also { viewModel.finish(onFinished) }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(
            title = workout.name,
            subtitle = Format.elapsed(now - workout.startedAt),
            onBack = onMinimise,
            backIcon = Icons.Filled.KeyboardArrowDown,
            backLabel = "Minimise workout",
            onTitleClick = { dialog = WorkoutDialog.Rename },
            actions = {
                Button(
                    onClick = ::requestFinish,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = KinzoTheme.spacing.lg),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text("Finish", style = MaterialTheme.typography.labelLarge)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename workout") }, onClick = {
                            menuOpen = false
                            dialog = WorkoutDialog.Rename
                        })
                        DropdownMenuItem(text = { Text("Discard workout", color = colors.error) }, onClick = {
                            menuOpen = false
                            dialog = WorkoutDialog.Discard
                        })
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                // Number pads on iPhone have no return key: tapping anywhere else closes them.
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            contentPadding = PaddingValues(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xl),
        ) {
            if (workout.exercises.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
                        Text("No exercises yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add an exercise to start logging sets.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(workout.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                ExerciseSection(
                    exercise = exercise,
                    unit = unit,
                    canMoveUp = index > 0,
                    canMoveDown = index < workout.exercises.lastIndex,
                    onMove = { offset -> viewModel.moveExercise(exercise, offset) },
                    onRemove = {
                        if (exercise.sets.any { it.isCompleted }) {
                            dialog = WorkoutDialog.RemoveExercise(exercise)
                        } else {
                            viewModel.removeExercise(exercise)
                        }
                    },
                    onAddSet = { viewModel.addSet(exercise) },
                    onValuesChange = { set, values -> viewModel.updateSet(exercise, set, values) },
                    onToggleDone = { set, entered -> viewModel.toggleDone(exercise, set, entered) },
                    onToggleWarmup = viewModel::toggleWarmup,
                    onRemoveSet = viewModel::removeSet,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                    PrimaryButton(text = "Add exercises", onClick = onAddExercises)
                    TextButton(onClick = { dialog = WorkoutDialog.Discard }, modifier = Modifier.fillMaxWidth()) {
                        Text("Discard workout", color = colors.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        val restState = rest
        if (restState != null) {
            RestTimerBar(state = restState, onAdjust = viewModel::adjustRest, onSkip = viewModel::skipRest)
        } else {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    when (val current = dialog) {
        null -> Unit
        WorkoutDialog.Rename -> TextInputDialog(
            title = "Rename workout",
            initialValue = workout.name,
            confirmLabel = "Save",
            onConfirm = {
                viewModel.rename(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.Discard -> ConfirmDialog(
            title = "Discard workout?",
            message = "Everything logged in this workout will be deleted.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.discard(onClosed)
            },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.NothingCompleted -> ConfirmDialog(
            title = "No sets ticked yet",
            message = "Tick the sets you've done before finishing, or discard this workout.",
            confirmLabel = "Discard workout",
            dismissLabel = "Keep going",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.discard(onClosed)
            },
            onDismiss = { dialog = null },
        )
        is WorkoutDialog.ConfirmFinish -> ConfirmDialog(
            title = "Finish workout?",
            message = if (current.unticked == 1) {
                "1 set isn't ticked and will be removed."
            } else {
                "${current.unticked} sets aren't ticked and will be removed."
            },
            confirmLabel = "Finish",
            onConfirm = {
                dialog = null
                viewModel.finish(onFinished)
            },
            onDismiss = { dialog = null },
        )
        is WorkoutDialog.RemoveExercise -> ConfirmDialog(
            title = "Remove ${current.exercise.name}?",
            message = "Its sets in this workout will be deleted.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.removeExercise(current.exercise)
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ExerciseSection(
    exercise: WorkoutExercise,
    unit: WeightUnit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (offset: Int) -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onValuesChange: (LoggedSet, SetValues) -> Unit,
    onToggleDone: (LoggedSet, SetValues) -> Boolean,
    onToggleWarmup: (LoggedSet) -> Unit,
    onRemoveSet: (LoggedSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val labels = SetRules.labels(exercise.sets.map { it.isWarmup })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "${exercise.name} options", tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canMoveUp) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = {
                            menuOpen = false
                            onMove(-1)
                        })
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(text = { Text("Move down") }, onClick = {
                            menuOpen = false
                            onMove(1)
                        })
                    }
                    DropdownMenuItem(text = { Text("Remove exercise", color = colors.error) }, onClick = {
                        menuOpen = false
                        onRemove()
                    })
                }
            }
        }
        SetTableHeader(type = exercise.measurementType, unit = unit)
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                SetEditorRow(
                    label = labels[index],
                    set = set,
                    previous = exercise.previous.getOrNull(index),
                    type = exercise.measurementType,
                    unit = unit,
                    onValuesChange = { onValuesChange(set, it) },
                    onToggleDone = { onToggleDone(set, it) },
                    onToggleWarmup = { onToggleWarmup(set) },
                    onRemove = { onRemoveSet(set) },
                )
            }
        }
        TextButton(
            onClick = onAddSet,
            modifier = Modifier.fillMaxWidth().heightIn(min = KinzoTheme.spacing.touchTarget),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
            Text(" Add set", color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider(color = colors.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(top = KinzoTheme.spacing.sm))
    }
}
