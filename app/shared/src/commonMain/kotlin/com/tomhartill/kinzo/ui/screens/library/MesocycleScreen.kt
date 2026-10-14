package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One mesocycle: its dates, how far through it is, and its workouts. Its name and dates can be changed. */
@Composable
fun MesocycleScreen(
    mesocycleId: String,
    onBack: () -> Unit,
    onOpenWorkout: (routineId: String) -> Unit,
    onCreateWorkout: () -> Unit,
    viewModel: MesocycleViewModel = koinViewModel { parametersOf(mesocycleId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mesocycle = state.mesocycle
    val today = state.today
    val colors = MaterialTheme.colorScheme
    val spacing = KinzoTheme.spacing
    val zone = remember { TimeZone.currentSystemDefault() }
    val now by rememberNow(intervalMillis = 60_000)
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, mesocycle == null) {
        if (!state.loading && mesocycle == null) onBack()
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(
            title = "",
            onBack = onBack,
            actions = {
                if (mesocycle != null) {
                    TextButton(onClick = { editing = true }) {
                        Text("Edit", color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        )
        if (mesocycle != null && today != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(mesocycle.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            mesocycleLine(mesocycle, state.isCurrent, today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Text(mesocycleProgress(mesocycle, state.isCurrent, today), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "workouts-header") { SectionHeader("Workouts", Modifier.padding(top = spacing.md)) }
                if (state.workouts.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No workouts in this mesocycle",
                            body = if (state.isCurrent) {
                                "Create the workouts for this block of training."
                            } else {
                                "Workouts you put in it show here."
                            },
                        )
                    }
                }
                items(state.workouts, key = { it.id }) { routine ->
                    RoutineCard(routine = routine, now = now, zone = zone, onClick = { onOpenWorkout(routine.id) })
                }
                if (state.isCurrent) {
                    item(key = "create") { SecondaryButton(text = "Create workout", onClick = onCreateWorkout) }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    if (editing && mesocycle != null && today != null) {
        MesocycleFormDialog(
            title = "Edit mesocycle",
            confirmLabel = "Save",
            initial = MesocycleDraft(mesocycle.name, mesocycle.start, mesocycle.end),
            today = today,
            defaultEnd = viewModel::defaultEnd,
            startAfter = state.startAfter,
            startBefore = state.startBefore,
            onConfirm = { draft, _ ->
                editing = false
                viewModel.update(draft)
            },
            onDismiss = { editing = false },
        )
    }
}
