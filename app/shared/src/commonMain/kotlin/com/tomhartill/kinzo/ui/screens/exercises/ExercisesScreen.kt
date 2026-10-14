package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.screens.ScreenTitle
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** The Exercises tab: the whole library, searchable. Tapping an exercise opens it. */
@Composable
fun ExercisesScreen(
    onOpenExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseBrowserViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KinzoTheme.spacing
    // The list stops at the keyboard so its last results stay reachable.
    Column(modifier.fillMaxSize().imePadding()) {
        ScreenTitle(
            title = "Exercises",
            modifier = Modifier.padding(start = spacing.screen, end = spacing.screen, top = spacing.xl, bottom = spacing.lg),
        )
        ExerciseBrowser(
            query = viewModel.query,
            state = state,
            onQueryChange = viewModel::search,
            onMuscleChange = viewModel::setMuscle,
            onEquipmentChange = viewModel::setEquipment,
            onExerciseClick = { onOpenExercise(it.id) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = spacing.lg),
        )
    }
}
