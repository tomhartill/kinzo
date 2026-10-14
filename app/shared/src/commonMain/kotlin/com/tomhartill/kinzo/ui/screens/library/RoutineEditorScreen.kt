package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Split
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutType
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.ReorderItem
import com.tomhartill.kinzo.ui.components.ReorderSheet
import com.tomhartill.kinzo.ui.components.RestChip
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.TargetInputs
import com.tomhartill.kinzo.ui.components.TextInputDialog
import com.tomhartill.kinzo.ui.components.kinzoTextFieldColors
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.exercises.ExercisePicker
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.theme.NumericStyle
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Creates or edits one of the user's workouts: its type, split, name, mesocycle and notes, and
 * for each exercise the sets, the reps, weight or time to aim for, and the rest. Nothing is
 * saved until Save.
 */
@Composable
fun RoutineEditorScreen(
    args: RoutineEditorViewModel.Args,
    onClose: () -> Unit,
    onSaved: (routineId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    viewModel: RoutineEditorViewModel = koinViewModel { parametersOf(args) },
) {
    // The unsaved draft is also kept in this screen's saved state, which outlives the app being
    // killed in the background; a view model made afresh after that picks it back up.
    var savedDraft by rememberSaveable { mutableStateOf<String?>(null) }
    remember(viewModel) { viewModel.attach(savedDraft) }
    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.savedState() }.collect { savedDraft = it }
    }

    val draft = viewModel.draft
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val mesocycles by viewModel.mesocycles.collectAsStateWithLifecycle()
    val today = remember(viewModel) { viewModel.today() }
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var picking by rememberSaveable { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var addingSplit by remember { mutableStateOf(false) }
    var splitToDelete by remember { mutableStateOf<Split?>(null) }
    // Set when exercises are added, to scroll down to them once the editor is back.
    var scrollToEnd by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.missing) {
        if (viewModel.missing) onClose()
    }

    fun requestClose() {
        if (viewModel.hasChanges) confirmDiscard = true else onClose()
    }

    // Back asks before throwing work away; while picking exercises it just closes the picker.
    SystemBackHandler(enabled = !picking && viewModel.hasChanges) { confirmDiscard = true }
    SystemBackHandler(enabled = picking) { picking = false }

    if (picking) {
        ExercisePicker(
            onClose = { picking = false },
            onConfirm = { picked ->
                viewModel.addExercises(picked)
                scrollToEnd = true
                picking = false
            },
        )
    } else {
        Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
            KinzoTopBar(
                title = if (viewModel.isNew) "New workout" else "Edit workout",
                onBack = ::requestClose,
                backIcon = Icons.Filled.Close,
                backLabel = "Close",
                actions = {
                    val canSave = draft != null && (draft.name.isNotBlank() || draft.exercises.isNotEmpty())
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = canSave) {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canSave) colors.onSurface else colors.onSurfaceVariant,
                        )
                    }
                },
            )
            if (draft != null) {
                EditorContent(
                    draft = draft,
                    listState = listState,
                    unit = settings.weightUnit,
                    defaultRest = settings.restSeconds,
                    splits = splits.filter { it.type == draft.type },
                    mesocycles = mesocycles,
                    today = today,
                    onTypeChange = viewModel::setType,
                    onSplitChange = viewModel::setSplit,
                    onAddSplit = { addingSplit = true },
                    onDeleteSplit = { splitToDelete = it },
                    onMesocycleChange = viewModel::setMesocycle,
                    onNameChange = viewModel::setName,
                    onNotesChange = viewModel::setNotes,
                    onReorder = { reordering = true },
                    onAddExercises = { picking = true },
                    onOpenExercise = onOpenExercise,
                    onRemove = viewModel::removeExercise,
                    onSetsChange = viewModel::setSets,
                    onTargetChange = viewModel::setTarget,
                    onRestChange = viewModel::setRest,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    LaunchedEffect(picking) {
        if (!picking && scrollToEnd) {
            // Waits for the list to be laid out again after the picker.
            val count = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            listState.animateScrollToItem(count - 1)
            scrollToEnd = false
        }
    }

    if (reordering && draft != null) {
        ReorderSheet(
            title = "Reorder exercises",
            items = draft.exercises.map { exercise ->
                ReorderItem(
                    id = exercise.id,
                    title = exercise.name,
                    subtitle = Format.target(exercise.measurementType, exercise.targetSets, exercise.target, settings.weightUnit),
                )
            },
            onReorder = viewModel::reorder,
            onDismiss = { reordering = false },
        )
    }

    if (addingSplit) {
        TextInputDialog(
            title = "New split",
            initialValue = "",
            confirmLabel = "Add",
            onConfirm = { name ->
                addingSplit = false
                viewModel.addSplit(name)
            },
            onDismiss = { addingSplit = false },
        )
    }

    splitToDelete?.let { split ->
        ConfirmDialog(
            title = "Remove ${split.name}?",
            message = "It won't be offered for new workouts. Workouts already filed under it keep the name.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                splitToDelete = null
                viewModel.deleteSplit(split)
            },
            onDismiss = { splitToDelete = null },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = if (viewModel.isNew) "Discard this workout?" else "Discard changes?",
            message = if (viewModel.isNew) "It hasn't been saved yet." else "Your changes to this workout won't be saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun EditorContent(
    draft: Routine,
    listState: LazyListState,
    unit: WeightUnit,
    defaultRest: Int,
    splits: List<Split>,
    mesocycles: List<Mesocycle>,
    today: LocalDate,
    onTypeChange: (WorkoutType) -> Unit,
    onSplitChange: (Split?) -> Unit,
    onAddSplit: () -> Unit,
    onDeleteSplit: (Split) -> Unit,
    onMesocycleChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onReorder: () -> Unit,
    onAddExercises: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onRemove: (id: String) -> Unit,
    onSetsChange: (id: String, sets: Int) -> Unit,
    onTargetChange: (id: String, SetValues) -> Unit,
    onRestChange: (id: String, seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = KinzoTheme.spacing
    val focusManager = LocalFocusManager.current
    val canReorder = draft.exercises.size > 1
    LazyColumn(
        state = listState,
        modifier = modifier
            // Number pads on iPhone have no return key: tapping anywhere else closes them.
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "type") { WorkoutTypeField(type = draft.type, onChange = onTypeChange) }
        // Only types with splits (for now, resistance training) offer them.
        if (splits.isNotEmpty() || draft.splitId != null) {
            item(key = "split") {
                SplitField(
                    selectedId = draft.splitId,
                    selectedName = draft.splitName,
                    splits = splits,
                    onChange = onSplitChange,
                    onAdd = onAddSplit,
                    onDelete = onDeleteSplit,
                )
            }
        }
        item(key = "name") {
            EditorTextField(
                value = draft.name,
                onValueChange = onNameChange,
                label = "Name",
                placeholder = "Push day, Legs, Full body…",
                singleLine = true,
            )
        }
        if (mesocycles.isNotEmpty()) {
            item(key = "mesocycle") {
                MesocycleField(selectedId = draft.mesocycleId, mesocycles = mesocycles, today = today, onChange = onMesocycleChange)
            }
        }
        item(key = "notes") {
            EditorTextField(
                value = draft.notes.orEmpty(),
                onValueChange = onNotesChange,
                label = "Notes (optional)",
                placeholder = "What it's for, how to progress",
                singleLine = false,
            )
        }
        item(key = "exercises-header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = spacing.sm)) {
                SectionHeader("Exercises", Modifier.weight(1f))
                if (canReorder) {
                    TextButton(onClick = onReorder) {
                        Icon(KinzoIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Text(" Reorder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (draft.exercises.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No exercises yet",
                    body = "Add the exercises for this workout, then set the sets, reps or weight to aim for, and the rest.",
                )
            }
        }
        items(draft.exercises, key = { it.id }) { exercise ->
            ExerciseEditor(
                exercise = exercise,
                unit = unit,
                defaultRest = defaultRest,
                canReorder = canReorder,
                onOpenInfo = { onOpenExercise(exercise.exerciseId) },
                onReorder = onReorder,
                onRemove = { onRemove(exercise.id) },
                onSetsChange = { onSetsChange(exercise.id, it) },
                onTargetChange = { onTargetChange(exercise.id, it) },
                onRestChange = { onRestChange(exercise.id, it) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add") {
            SecondaryButton(text = "Add exercises", onClick = onAddExercises)
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = colors.onSurfaceVariant) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 5,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        shape = MaterialTheme.shapes.small,
        colors = kinzoTextFieldColors(),
    )
}

/** One exercise in the editor: its name, how many sets, what to aim for, and the rest. */
@Composable
private fun ExerciseEditor(
    exercise: RoutineExercise,
    unit: WeightUnit,
    defaultRest: Int,
    canReorder: Boolean,
    onOpenInfo: () -> Unit,
    onReorder: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onTargetChange: (SetValues) -> Unit,
    onRestChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
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
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${exercise.name}", tint = colors.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.lg)) {
            SetsStepper(sets = exercise.targetSets, exerciseName = exercise.name, onChange = onSetsChange)
            TargetInputs(
                type = exercise.measurementType,
                unit = unit,
                values = exercise.target,
                onValuesChange = onTargetChange,
                exerciseName = exercise.name,
            )
        }
        RestChip(seconds = exercise.restSeconds ?: defaultRest, onSelect = onRestChange)
        HorizontalDivider(color = colors.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(top = KinzoTheme.spacing.xs))
    }
}

/** Minus, the number of sets, plus. */
@Composable
private fun SetsStepper(sets: Int, exerciseName: String, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
        Text("SETS", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(sets - 1) }, enabled = sets > RoutineExercise.MIN_SETS) {
                Icon(KinzoIcons.Remove, contentDescription = "One set fewer of $exerciseName")
            }
            Text(
                sets.toString(),
                style = NumericStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            IconButton(onClick = { onChange(sets + 1) }, enabled = sets < RoutineExercise.MAX_SETS) {
                Icon(Icons.Filled.Add, contentDescription = "One more set of $exerciseName")
            }
        }
    }
}
