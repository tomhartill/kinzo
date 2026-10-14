package com.tomhartill.kinzo.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutSummary
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone

private const val MAX_EXERCISE_LINES = 4

/** A finished workout in a list: name, day, totals and the first few exercises. */
@Composable
internal fun WorkoutCard(
    workout: WorkoutSummary,
    unit: WeightUnit,
    now: Long,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val totals = listOfNotNull(
        Format.workoutLength(workout.endedAt - workout.startedAt),
        if (workout.setCount == 1) "1 set" else "${workout.setCount} sets",
        if (workout.volumeKg > 0) Format.volume(workout.volumeKg, unit) else null,
    ).joinToString(" · ")
    KinzoCard(modifier = modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Text(
                    Dates.dayLabel(workout.startedAt, now, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(totals, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
        }
        if (workout.exercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
                workout.exercises.take(MAX_EXERCISE_LINES).forEach { line ->
                    Text(
                        "${line.setCount} × ${line.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val more = workout.exercises.size - MAX_EXERCISE_LINES
                if (more > 0) {
                    Text(
                        if (more == 1) "1 more exercise" else "$more more exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
