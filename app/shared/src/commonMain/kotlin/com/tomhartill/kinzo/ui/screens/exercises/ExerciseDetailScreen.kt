package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.ExerciseDetail
import com.tomhartill.kinzo.domain.ExerciseSession
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.ProgressMetric
import com.tomhartill.kinzo.domain.ProgressPoint
import com.tomhartill.kinzo.domain.Progression
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Weight
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.ExerciseIcon
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.ProgressChart
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.SetSummaryRow
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlin.math.roundToLong
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One exercise: what it works, your best set and past sessions, and how to do it. */
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel { parametersOf(exerciseId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise
    val listState = rememberLazyListState()
    // The name moves into the top bar once its headline scrolls away.
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val spacing = KinzoTheme.spacing
    val zone = remember { TimeZone.currentSystemDefault() }
    val now by rememberNow(intervalMillis = 60_000)
    val navigationBars = WindowInsets.navigationBars.asPaddingValues()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KinzoTopBar(title = if (showTitle) exercise?.name.orEmpty() else "", onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.sm,
                bottom = spacing.xxl + navigationBars.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when {
                state.loading -> Unit
                exercise == null -> item(key = "missing") {
                    EmptyState(title = "Exercise not found", body = "It may have been removed from the library.")
                }
                else -> {
                    item(key = "header") { Header(exercise) }
                    val best = state.best
                    if (best != null) {
                        item(key = "stats") { Stats(exercise.measurementType, best, state.sessions.size, state.unit) }
                    }
                    if (state.sessions.size >= 2) {
                        item(key = "progress") { Progress(exercise.measurementType, state.sessions, state.unit) }
                    }
                    if (exercise.instructions.isNotEmpty()) {
                        item(key = "how-to-header") { SectionHeader("How to", Modifier.padding(top = spacing.sm)) }
                        itemsIndexed(exercise.instructions, key = { index, _ -> "step-$index" }) { index, step ->
                            Step(number = index + 1, text = step)
                        }
                    }
                    item(key = "history-header") { SectionHeader("History", Modifier.padding(top = spacing.sm)) }
                    if (state.sessions.isEmpty()) {
                        item(key = "history-empty") {
                            Text(
                                "Sets you log for this exercise will show here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.sessions, key = { "session-${it.workoutId}" }) { session ->
                        Session(session, exercise.measurementType, state.unit, now, zone)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(exercise: ExerciseDetail) {
    val colors = MaterialTheme.colorScheme
    val works = listOfNotNull(
        exercise.primaryMuscles.joinToString(", ") { it.label }.ifEmpty { null },
        exercise.equipmentName,
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
        ExerciseIcon(exercise.icon, size = 72.dp, modifier = Modifier.padding(bottom = KinzoTheme.spacing.sm))
        exercise.groupName?.let { group ->
            Text("A variation of ${group.lowercase()}", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        if (works.isNotEmpty()) {
            Text(works, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (exercise.secondaryMuscles.isNotEmpty()) {
            Text(
                "Also works " + exercise.secondaryMuscles.joinToString(", ") { it.label.lowercase() },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stats(type: MeasurementType, best: SetValues, sessions: Int, unit: WeightUnit) {
    val (value, valueUnit, label) = bestSetTile(type, best, unit)
    KinzoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.lg)) {
            StatTile(value = value, unit = valueUnit, label = label, modifier = Modifier.weight(1f))
            StatTile(value = sessions.toString(), label = if (sessions == 1) "Session" else "Sessions", modifier = Modifier.weight(1f))
        }
    }
}

/** The best set as a headline number, its unit and a label that carries the rest. */
private fun bestSetTile(type: MeasurementType, best: SetValues, unit: WeightUnit): Triple<String, String?, String> = when (type) {
    MeasurementType.WeightReps -> Triple(
        best.weightKg?.let { Weight.format(it, unit) } ?: "–",
        unit.label,
        "Heaviest · ${best.reps ?: 0} ${if (best.reps == 1) "rep" else "reps"}",
    )
    MeasurementType.Reps -> Triple(best.reps?.toString() ?: "–", "reps", "Most in a set")
    MeasurementType.Duration -> Triple(best.durationS?.let { Format.duration(it) } ?: "–", null, "Longest")
    MeasurementType.DistanceDuration -> if (best.distanceM != null) {
        Triple(Format.kilometres(best.distanceM), "km", listOfNotNull("Furthest", best.durationS?.let { Format.duration(it) }).joinToString(" · "))
    } else {
        Triple(best.durationS?.let { Format.duration(it) } ?: "–", null, "Longest")
    }
}

/** How this exercise has gone over time, with a choice of what to plot when there's more than one. */
@Composable
private fun Progress(type: MeasurementType, sessions: List<ExerciseSession>, unit: WeightUnit) {
    val metrics = remember(type) { ProgressMetric.forType(type) }
    var metric by remember(type) { mutableStateOf(metrics.first()) }
    val chart = remember(metric, sessions, unit) { progressChart(metric, sessions, unit) }
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
        SectionHeader("Progress", Modifier.padding(top = KinzoTheme.spacing.sm))
        if (metrics.size > 1) {
            ChoiceRow(options = metrics, selected = metric, optionLabel = { it.label }, onSelect = { metric = it })
        }
        if (chart.points.size >= 2) {
            ProgressChart(points = chart.points, formatValue = chart.format, unit = chart.unit)
        } else {
            Text(
                "Log this in another workout to see a trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class ProgressChartData(val points: List<ProgressPoint>, val format: (Double) -> String, val unit: String?)

/** A metric's points in the units the user reads them in, with how to write them. */
private fun progressChart(metric: ProgressMetric, sessions: List<ExerciseSession>, unit: WeightUnit): ProgressChartData {
    val points = Progression.series(metric, sessions)
    fun converted(convert: (Double) -> Double) = points.map { it.copy(value = convert(it.value)) }
    return when (metric) {
        ProgressMetric.EstimatedOneRepMax, ProgressMetric.Heaviest ->
            ProgressChartData(converted { Weight.fromKg(it, unit) }, { Format.decimal(it, maxDecimals = 1) }, unit.label)
        ProgressMetric.Volume ->
            ProgressChartData(converted { Weight.fromKg(it, unit) }, { Format.thousands(it.roundToLong()) }, unit.label)
        ProgressMetric.Reps -> ProgressChartData(points, { it.roundToLong().toString() }, "reps")
        ProgressMetric.Duration -> ProgressChartData(points, { Format.duration(it.roundToLong()) }, null)
        ProgressMetric.Distance -> ProgressChartData(converted { it / 1000.0 }, { Format.decimal(it, maxDecimals = 2) }, "km")
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Session(session: ExerciseSession, type: MeasurementType, unit: WeightUnit, now: Long, zone: TimeZone) {
    val labels = SetRules.labels(session.sets.map { it.isWarmup })
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
            Text(
                Dates.dayLabel(session.startedAt, now, zone),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                session.workoutName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        }
        session.sets.forEachIndexed { index, set ->
            SetSummaryRow(label = labels[index], text = Format.set(type, set.values, unit), isWarmup = set.isWarmup)
        }
    }
}
