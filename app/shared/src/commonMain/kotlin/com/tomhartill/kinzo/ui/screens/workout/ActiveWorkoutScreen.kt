package com.tomhartill.kinzo.ui.screens.workout

import com.tomhartill.kinzo.ui.tips.tipAnchor
import com.tomhartill.kinzo.ui.tips.TipsButton
import com.tomhartill.kinzo.ui.tips.ScreenTipsEffect
import com.tomhartill.kinzo.ui.tips.KinzoTips
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutExercise
import com.tomhartill.kinzo.ui.components.AddSetBar
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.ReorderItem
import com.tomhartill.kinzo.ui.components.ReorderSheet
import com.tomhartill.kinzo.ui.components.RestBar
import com.tomhartill.kinzo.ui.components.RestChip
import com.tomhartill.kinzo.ui.components.SetChangeMarker
import com.tomhartill.kinzo.ui.components.SetEditorRow
import com.tomhartill.kinzo.ui.components.SetTableHeader
import com.tomhartill.kinzo.ui.components.TextInputDialog
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private sealed interface WorkoutDialog {
    data object Rename : WorkoutDialog
    data object Discard : WorkoutDialog
    data object NothingCompleted : WorkoutDialog
    data class ConfirmFinish(val unticked: Int) : WorkoutDialog
    data class RemoveExercise(val exercise: WorkoutExercise) : WorkoutDialog
    data class ReplaceExercise(val exercise: WorkoutExercise, val ticked: Int) : WorkoutDialog
}

/**
 * The workout in progress: one section per exercise with its sets, and the rest timer. With
 * [editWorkoutId] it edits a finished workout instead: no timers, and Done in place of Finish.
 */
@Composable
fun ActiveWorkoutScreen(
    onMinimise: () -> Unit,
    onAddExercises: () -> Unit,
    onReplaceExercise: (workoutExerciseId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onFinished: (workoutId: String) -> Unit,
    onClosed: () -> Unit,
    editWorkoutId: String? = null,
    viewModel: ActiveWorkoutViewModel = koinViewModel {
        if (editWorkoutId == null) parametersOf() else parametersOf(editWorkoutId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val workout = state.workout
    val colors = MaterialTheme.colorScheme
    val editing = viewModel.editing

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
    var reordering by remember { mutableStateOf(false) }
    val now by rememberNow()
    val zone = remember { TimeZone.currentSystemDefault() }
    val discardLabel = if (editing) "Delete workout" else "Discard workout"
    val unit = state.settings.weightUnit
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Deleting a set (swipe or menu) can be undone for a few seconds.
    fun removeSet(set: LoggedSet) {
        viewModel.removeSet(set)
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbar.showSnackbar(message = "Set deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSet(set)
        }
    }

    // The bar at the bottom shows the rest for the exercise being worked on: the first with
    // a set still to tick, or the last one once everything is ticked.
    val currentExercise = workout.exercises.firstOrNull { exercise -> exercise.sets.any { !it.isCompleted } }
        ?: workout.exercises.lastOrNull()

    fun requestFinish() {
        val completed = workout.completedSetCount
        val unticked = workout.exercises.sumOf { exercise -> exercise.sets.count { !it.isCompleted } }
        dialog = when {
            completed == 0 -> WorkoutDialog.NothingCompleted
            unticked > 0 -> WorkoutDialog.ConfirmFinish(unticked)
            else -> null.also { viewModel.finish(onFinished) }
        }
    }

    // Edits save as they're made; leaving just drops the sets that weren't ticked.
    fun requestDone() {
        if (workout.completedSetCount == 0) {
            dialog = WorkoutDialog.NothingCompleted
        } else {
            viewModel.saveEdits { onFinished(workout.id) }
        }
    }

    // Tips only before the first set is ticked, so nothing interrupts a workout under way.
    ScreenTipsEffect(
        KinzoTips.ActiveWorkout,
        enabled = !editing && workout.exercises.isNotEmpty() && workout.completedSetCount == 0,
    )

    // Back while editing tidies up on the way out, the same as Done.
    SystemBackHandler(enabled = editing) { requestDone() }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(
            title = workout.name,
            subtitle = if (editing) {
                "Editing · ${Dates.longDayLabel(workout.startedAt, zone)}"
            } else {
                Format.elapsed(now - workout.startedAt)
            },
            onBack = { if (editing) requestDone() else onMinimise() },
            backIcon = if (editing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.KeyboardArrowDown,
            backLabel = if (editing) "Done" else "Minimise workout",
            onTitleClick = { dialog = WorkoutDialog.Rename },
            actions = {
                if (!editing) TipsButton(KinzoTips.ActiveWorkout)
                Button(
                    onClick = { if (editing) requestDone() else requestFinish() },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = KinzoTheme.spacing.lg),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text(if (editing) "Done" else "Finish", style = MaterialTheme.typography.labelLarge)
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
                        if (workout.exercises.size > 1) {
                            DropdownMenuItem(text = { Text("Reorder exercises") }, onClick = {
                                menuOpen = false
                                reordering = true
                            })
                        }
                        DropdownMenuItem(text = { Text(discardLabel, color = colors.error) }, onClick = {
                            menuOpen = false
                            dialog = WorkoutDialog.Discard
                        })
                    }
                }
            },
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                items(workout.exercises, key = { exercise -> exercise.id }) { exercise ->
                    ExerciseSection(
                        exercise = exercise,
                        unit = unit,
                        restSeconds = viewModel.restFor(exercise).takeUnless { editing },
                        canReorder = workout.exercises.size > 1,
                        onOpenInfo = { onOpenExercise(exercise.exerciseId) },
                        onRestChange = { viewModel.setRest(exercise, it) },
                        onNotesChange = { viewModel.setNotes(exercise, it) },
                        onReorder = { reordering = true },
                        onReplace = {
                            // Ticked sets were for the old exercise, so they go: ask first.
                            val ticked = exercise.sets.count { it.isCompleted }
                            if (ticked > 0) {
                                dialog = WorkoutDialog.ReplaceExercise(exercise, ticked)
                            } else {
                                onReplaceExercise(exercise.id)
                            }
                        },
                        onRemove = {
                            if (exercise.sets.any { it.isCompleted }) {
                                dialog = WorkoutDialog.RemoveExercise(exercise)
                            } else {
                                viewModel.removeExercise(exercise)
                            }
                        },
                        onAddSet = { percent -> viewModel.addSet(exercise, percent) },
                        onValuesChange = { set, values -> viewModel.updateSet(exercise, set, values) },
                        onToggleDone = { set, entered -> viewModel.toggleDone(exercise, set, entered) },
                        onToggleWarmup = viewModel::toggleWarmup,
                        onRemoveSet = ::removeSet,
                        addSetModifier = if (exercise.id == workout.exercises.first().id) Modifier.tipAnchor(KinzoTips.AddSet) else Modifier,
                        modifier = Modifier
                            .animateItem()
                            .then(if (exercise.id == workout.exercises.first().id) Modifier.tipAnchor(KinzoTips.LogSet) else Modifier),
                    )
                }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                        PrimaryButton(text = "Add exercises", onClick = onAddExercises)
                        TextButton(onClick = { dialog = WorkoutDialog.Discard }, modifier = Modifier.fillMaxWidth()) {
                            Text(discardLabel, color = colors.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = KinzoTheme.spacing.md))
        }

        if (!editing && (currentExercise != null || rest != null)) {
            val currentRest = currentExercise?.let(viewModel::restFor) ?: state.settings.restSeconds
            RestBar(
                running = rest,
                exerciseName = currentExercise?.name,
                restSeconds = currentRest,
                onAdjustRunning = viewModel::adjustRest,
                onSkip = viewModel::skipRest,
                onChangeRest = { seconds -> currentExercise?.let { viewModel.setRest(it, seconds) } },
                onStart = { viewModel.startRest(currentRest) },
                modifier = Modifier.tipAnchor(KinzoTips.RestBar),
            )
        } else {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (reordering) {
        ReorderSheet(
            title = "Reorder exercises",
            items = workout.exercises.map { exercise ->
                ReorderItem(
                    id = exercise.id,
                    title = exercise.name,
                    subtitle = if (exercise.sets.size == 1) "1 set" else "${exercise.sets.size} sets",
                )
            },
            onReorder = viewModel::reorderExercises,
            onDismiss = { reordering = false },
        )
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
            title = if (editing) "Delete workout?" else "Discard workout?",
            message = if (editing) {
                "It will be removed from your history and stats."
            } else {
                "Everything logged in this workout will be deleted."
            },
            confirmLabel = if (editing) "Delete" else "Discard",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.discard(onClosed)
            },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.NothingCompleted -> ConfirmDialog(
            title = if (editing) "No sets ticked" else "No sets ticked yet",
            message = if (editing) {
                "Tick the sets you did, or delete this workout."
            } else {
                "Tick the sets you've done before finishing, or discard this workout."
            },
            confirmLabel = discardLabel,
            dismissLabel = if (editing) "Keep editing" else "Keep going",
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
        is WorkoutDialog.ReplaceExercise -> ConfirmDialog(
            title = "Replace ${current.exercise.name}?",
            message = if (current.ticked == 1) {
                "Its ticked set will be deleted."
            } else {
                "Its ${current.ticked} ticked sets will be deleted."
            },
            confirmLabel = "Replace",
            destructive = true,
            onConfirm = {
                dialog = null
                onReplaceExercise(current.exercise.id)
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ExerciseSection(
    exercise: WorkoutExercise,
    unit: WeightUnit,
    // Null hides the rest choice, as when editing a finished workout.
    restSeconds: Int?,
    canReorder: Boolean,
    onOpenInfo: () -> Unit,
    onRestChange: (seconds: Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onReorder: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: (percent: Double) -> Unit,
    onValuesChange: (LoggedSet, SetValues) -> Unit,
    onToggleDone: (LoggedSet, SetValues) -> Boolean,
    onToggleWarmup: (LoggedSet) -> Unit,
    onRemoveSet: (LoggedSet) -> Unit,
    modifier: Modifier = Modifier,
    addSetModifier: Modifier = Modifier,
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
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "How to do ${exercise.name}", onClick = onOpenInfo)
                    .padding(vertical = KinzoTheme.spacing.xs),
            )
            IconButton(onClick = onOpenInfo) {
                Icon(Icons.Outlined.Info, contentDescription = "How to do ${exercise.name}", tint = colors.onSurfaceVariant)
            }
            if (canReorder) {
                IconButton(onClick = onReorder) {
                    Icon(KinzoIcons.DragHandle, contentDescription = "Reorder exercises", tint = colors.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "${exercise.name} options", tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canReorder) {
                        DropdownMenuItem(text = { Text("Reorder exercises") }, onClick = {
                            menuOpen = false
                            onReorder()
                        })
                    }
                    DropdownMenuItem(text = { Text("Replace exercise") }, onClick = {
                        menuOpen = false
                        onReplace()
                    })
                    DropdownMenuItem(text = { Text("Remove exercise", color = colors.error) }, onClick = {
                        menuOpen = false
                        onRemove()
                    })
                }
            }
        }
        ExerciseNotesField(
            exerciseName = exercise.name,
            notes = exercise.notes,
            previousNotes = exercise.previousNotes,
            onSave = onNotesChange,
        )
        if (restSeconds != null) RestChip(seconds = restSeconds, onSelect = onRestChange)
        SetTableHeader(type = exercise.measurementType, unit = unit)
        // A wider gap between sets than the rest of the section, to fit the weight change marker.
        Column(verticalArrangement = Arrangement.spacedBy(SetGap)) {
            exercise.sets.forEachIndexed { index, set ->
                key(set.id) {
                    Box {
                        SwipeToDelete(label = "Delete set ${labels[index]}", onDelete = { onRemoveSet(set) }) {
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
                        exercise.weightChange(index)?.let { change ->
                            SetChangeMarker(
                                text = change,
                                type = exercise.measurementType,
                                modifier = Modifier.centredOnGapAbove(SetGap),
                            )
                        }
                    }
                }
            }
        }
        val lastIndex = exercise.sets.lastIndex
        AddSetBar(
            baseKg = exercise.effectiveValues(lastIndex)?.weightKg.takeIf { exercise.measurementType == MeasurementType.WeightReps },
            unit = unit,
            onAdd = onAddSet,
            modifier = addSetModifier.padding(top = KinzoTheme.spacing.xs),
        )
        HorizontalDivider(color = colors.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(top = KinzoTheme.spacing.sm))
    }
}

private val SetGap = 12.dp

/**
 * Draws this centred on the gap of [gap] above it, taking no space, so the rows don't move when
 * it appears. Set rows inset their inputs equally top and bottom, so this is also the middle of
 * the space between two rows' inputs.
 */
private fun Modifier.centredOnGapAbove(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) {
        placeable.place(0, -placeable.height / 2 - (gap / 2).roundToPx())
    }
}

/** Swipe left to delete. The row underneath turns red as it slides away. */
@Composable
private fun SwipeToDelete(label: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true })
        },
        backgroundContent = {
            val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (swiping) colors.error else Color.Transparent, MaterialTheme.shapes.small)
                    .padding(horizontal = KinzoTheme.spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (swiping) Text("Delete", style = MaterialTheme.typography.labelLarge, color = colors.onError)
            }
        },
    ) {
        // Opaque, so the red only shows where the row has moved away.
        Box(Modifier.background(colors.background)) { content() }
    }
}
