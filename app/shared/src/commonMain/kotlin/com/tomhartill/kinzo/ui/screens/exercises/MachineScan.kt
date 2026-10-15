package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/** The button above the exercise list that opens the camera. */
@Composable
fun ScanMachineButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = KinzoTheme.spacing.touchTarget),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(KinzoIcons.Camera, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.size(KinzoTheme.spacing.sm))
        Text("Scan a machine", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * What a scan found, as a sheet over the picker. Each machine lists its exercises with the
 * same ticks as the picker, so ticking one here picks it there; the likeliest variation comes
 * ticked already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineScanSheet(
    state: MachineScanState,
    exercises: Map<String, ExerciseSummary>,
    selectedIds: List<String>,
    onToggle: (ExerciseSummary) -> Unit,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (state == MachineScanState.Idle) return
    // Waiting on the camera shows nothing: the camera is on screen.
    if (state is MachineScanState.Scanning && !state.uploading) return
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        val padded = Modifier.padding(horizontal = KinzoTheme.spacing.screen)
        when (state) {
            is MachineScanState.Scanning -> Row(
                modifier = padded.fillMaxWidth().padding(vertical = KinzoTheme.spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text("Working out which machine this is…", style = MaterialTheme.typography.bodyLarge)
            }

            is MachineScanState.Failed -> Column(
                modifier = padded.padding(bottom = KinzoTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
            ) {
                Text(if (state.signIn) "Sign in to scan" else "That didn't work", style = MaterialTheme.typography.titleMedium)
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (state.signIn && onSignIn != null) {
                    PrimaryButton(text = "Sign in", onClick = onSignIn)
                } else if (!state.signIn) {
                    PrimaryButton(text = "Try again", onClick = onRetry)
                }
                SecondaryButton(text = "Search instead", onClick = onDismiss)
            }

            is MachineScanState.Found -> FoundContent(state, exercises, selectedIds, onToggle, onRetry, onDismiss)

            MachineScanState.Idle -> Unit
        }
    }
}

@Composable
private fun FoundContent(
    state: MachineScanState.Found,
    exercises: Map<String, ExerciseSummary>,
    selectedIds: List<String>,
    onToggle: (ExerciseSummary) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val padded = Modifier.padding(horizontal = KinzoTheme.spacing.screen)
    val best = state.matches.firstOrNull()
    if (best == null) {
        Column(
            modifier = padded.padding(bottom = KinzoTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
        ) {
            Text("Kinzo doesn't know this one yet", style = MaterialTheme.typography.titleMedium)
            if (state.description.isNotBlank()) {
                Text(state.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Text(
                "Search for the closest exercise instead, or try again with the whole machine in view.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            PrimaryButton(text = "Search instead", onClick = onDismiss)
            SecondaryButton(text = "Scan again", onClick = onRetry)
        }
        return
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "title") {
            Column(padded.padding(bottom = KinzoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
                Text(
                    text = when (best.confidence) {
                        "high" -> "This looks like a ${best.machine.name.lowercase()}"
                        "medium" -> "Probably a ${best.machine.name.lowercase()}"
                        else -> "Could be a ${best.machine.name.lowercase()}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.description.isNotBlank()) {
                    Text(state.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Text(
                    if (state.suggestedExerciseId != null) "The likeliest exercise is ticked. Change it if it's wrong." else "Tick the exercise you're doing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        state.matches.forEachIndexed { index, match ->
            if (index > 0) {
                item(key = "header-${match.machine.id}") {
                    SectionHeader(
                        text = "Or a ${match.machine.name.lowercase()}",
                        modifier = padded.padding(top = KinzoTheme.spacing.lg, bottom = KinzoTheme.spacing.xs),
                    )
                }
            }
            val rows = match.machine.exerciseIds.mapNotNull { exercises[it] }
            items(rows, key = { "${match.machine.id}-${it.id}" }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    trend = null,
                    selected = exercise.id in selectedIds,
                    onClick = { onToggle(exercise) },
                )
            }
        }
        item(key = "done") {
            Column(
                padded.padding(top = KinzoTheme.spacing.lg, bottom = KinzoTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
            ) {
                PrimaryButton(text = "Done", onClick = onDismiss)
                SecondaryButton(text = "Scan again", onClick = onRetry)
            }
        }
    }
}
