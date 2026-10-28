package com.tomhartill.kinzo.ui.screens.history

import com.tomhartill.kinzo.ui.tips.ScreenTipsEffect
import com.tomhartill.kinzo.ui.tips.PageTipCard
import com.tomhartill.kinzo.ui.tips.KinzoTips
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.ScreenTitle
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

/**
 * The History tab: every finished workout, newest first, grouped by month. Each can be repeated,
 * which starts a new workout with the same exercises and sets ([onStarted] opens it).
 */
@Composable
fun HistoryScreen(
    onOpenWorkout: (workoutId: String) -> Unit,
    onStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KinzoTheme.spacing
    val zone = remember { TimeZone.currentSystemDefault() }
    val now by rememberNow(intervalMillis = 60_000)
    var busy by remember { mutableStateOf(false) }
    val months = remember(state.workouts, zone) {
        state.workouts.groupBy { Dates.monthLabel(it.startedAt, zone) }.toList()
    }

    ScreenTipsEffect(KinzoTips.History)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "title") { ScreenTitle("History", modifier = Modifier.padding(bottom = spacing.xs), tips = KinzoTips.History) }
        item(key = "tip") { PageTipCard(KinzoTips.History) }
        if (!state.loading && state.workouts.isEmpty()) {
            item(key = "empty") {
                EmptyState(title = "No workouts yet", body = "Finished workouts appear here, newest first.")
            }
        }
        months.forEach { (month, workouts) ->
            item(key = "month-$month") { SectionHeader(month, Modifier.padding(top = spacing.sm)) }
            items(workouts, key = { it.id }) { workout ->
                WorkoutCard(
                    workout = workout,
                    unit = state.unit,
                    now = now,
                    zone = zone,
                    onClick = { onOpenWorkout(workout.id) },
                    onRepeat = { viewModel.repeat(workout.id, onStarted = onStarted, onBusy = { busy = true }) },
                )
            }
        }
    }

    if (busy) {
        ConfirmDialog(
            title = "A workout is in progress",
            message = "Finish or discard it before starting another.",
            confirmLabel = "Open it",
            onConfirm = {
                busy = false
                onStarted()
            },
            onDismiss = { busy = false },
        )
    }
}
