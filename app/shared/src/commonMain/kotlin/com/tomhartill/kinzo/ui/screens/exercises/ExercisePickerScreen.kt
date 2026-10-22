package com.tomhartill.kinzo.ui.screens.exercises

import com.tomhartill.kinzo.ui.tips.TipsButton
import com.tomhartill.kinzo.ui.tips.ScreenTipsEffect
import com.tomhartill.kinzo.ui.tips.PageTipCard
import com.tomhartill.kinzo.ui.tips.KinzoTips
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Picks one or more exercises to add, in the order tapped, to [workoutId] or to the workout
 * in progress when that's null.
 */
@Composable
fun ExercisePickerScreen(
    workoutId: String?,
    onClose: () -> Unit,
    onAdded: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: ExerciseBrowserViewModel = koinViewModel(),
) {
    ExercisePicker(
        onClose = onClose,
        onConfirm = { picked -> viewModel.addToWorkout(workoutId, picked.map { it.id }, onAdded) },
        onSignIn = onSignIn,
        viewModel = viewModel,
    )
}

/**
 * The exercise library with a tick on each row. [onConfirm] gets the picked exercises in the
 * order they were tapped, once. "Scan a machine" picks from a photo; [onSignIn] opens sign-in
 * when scanning needs it, where the screen can.
 */
@Composable
fun ExercisePicker(
    onClose: () -> Unit,
    onConfirm: (List<ExerciseSummary>) -> Unit,
    modifier: Modifier = Modifier,
    onSignIn: (() -> Unit)? = null,
    viewModel: ExerciseBrowserViewModel = koinViewModel(),
    scanViewModel: MachineScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scan by scanViewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    // The variation a scan thinks it is comes ticked, once per scan.
    LaunchedEffect(scan) {
        val suggested = (scan as? MachineScanState.Found)?.suggestedExerciseId ?: return@LaunchedEffect
        if (suggested !in selected) selected = selected + suggested
    }
    var confirmed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    ScreenTipsEffect(KinzoTips.ExercisePicker)
    Column(modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(
            title = "Add exercises",
            onBack = onClose,
            backIcon = Icons.Filled.Close,
            backLabel = "Close",
            actions = {
                TipsButton(KinzoTips.ExercisePicker)
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptyList() }) {
                        Text("Clear", color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        )
        PageTipCard(
            KinzoTips.ExercisePicker,
            Modifier.padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.xs),
        )
        ExerciseBrowser(
            query = viewModel.query,
            state = state,
            onQueryChange = viewModel::search,
            onMuscleChange = viewModel::setMuscle,
            onEquipmentChange = viewModel::setEquipment,
            onExerciseClick = { exercise ->
                selected = if (exercise.id in selected) selected - exercise.id else selected + exercise.id
            },
            selectedIds = selected,
            modifier = Modifier.weight(1f),
            header = { ScanMachineButton(onClick = scanViewModel::scan) },
        )
        if (selected.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
            PrimaryButton(
                text = if (selected.size == 1) "Add 1 exercise" else "Add ${selected.size} exercises",
                enabled = !confirmed,
                onClick = {
                    confirmed = true
                    onConfirm(selected.mapNotNull { state.byId[it] })
                },
                modifier = Modifier.padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    MachineScanSheet(
        state = scan,
        exercises = state.byId,
        selectedIds = selected,
        onToggle = { exercise ->
            selected = if (exercise.id in selected) selected - exercise.id else selected + exercise.id
        },
        onRetry = {
            scanViewModel.dismiss()
            scanViewModel.scan()
        },
        onSignIn = onSignIn?.let { signIn ->
            {
                scanViewModel.dismiss()
                signIn()
            }
        },
        onDismiss = scanViewModel::dismiss,
    )
}
