package com.tomhartill.kinzo.ui.screens.exercises

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** Picks one or more exercises to add to the workout in progress, in the order tapped. */
@Composable
fun ExercisePickerScreen(
    onClose: () -> Unit,
    onAdded: () -> Unit,
    viewModel: ExerciseBrowserViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adding by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(
            title = "Add exercises",
            onBack = onClose,
            backIcon = Icons.Filled.Close,
            backLabel = "Close",
            actions = {
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptyList() }) {
                        Text("Clear", color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
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
        )
        if (selected.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
            PrimaryButton(
                text = if (selected.size == 1) "Add 1 exercise" else "Add ${selected.size} exercises",
                enabled = !adding,
                onClick = {
                    adding = true
                    viewModel.addToActiveWorkout(selected, onAdded)
                },
                modifier = Modifier.padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
