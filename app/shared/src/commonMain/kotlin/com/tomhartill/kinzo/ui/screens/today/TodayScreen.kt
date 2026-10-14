package com.tomhartill.kinzo.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Weight
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.TitledScreen
import com.tomhartill.kinzo.ui.screens.history.WorkoutCard
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToLong

/** The Today tab: start or resume a workout, this week's totals and the last workout. */
@Composable
fun TodayScreen(
    onOpenWorkout: () -> Unit,
    onOpenFinishedWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { TimeZone.currentSystemDefault() }
    val now by rememberNow(intervalMillis = 60_000)

    TitledScreen(title = "Today", subtitle = Dates.longDayLabel(now, zone), modifier = modifier) {
        if (state.hasActiveWorkout) {
            PrimaryButton(text = "Resume workout", onClick = onOpenWorkout)
        } else {
            PrimaryButton(text = "Start empty workout", onClick = { viewModel.startWorkout(onOpenWorkout) }, enabled = !state.loading)
        }

        SectionHeader("This week")
        KinzoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xl)) {
                StatTile(
                    value = state.weekWorkouts.toString(),
                    label = if (state.weekWorkouts == 1) "Workout" else "Workouts",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = Format.thousands(Weight.fromKg(state.weekVolumeKg, state.unit).roundToLong()),
                    unit = state.unit.label,
                    label = "Volume",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val last = state.lastWorkout
        if (last != null) {
            SectionHeader("Last workout")
            WorkoutCard(workout = last, unit = state.unit, now = now, zone = zone, onClick = { onOpenFinishedWorkout(last.id) })
        } else if (!state.loading && !state.hasActiveWorkout) {
            EmptyState(
                title = "Your first workout",
                body = "Start an empty workout, add exercises, and tick off each set as you go. Kinzo remembers what you lifted for next time.",
            )
        }
    }
}
