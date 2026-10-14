package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.ui.theme.KinzoTheme

private fun remainingSeconds(state: RestTimer.State, now: Long): Long = ((state.endsAtMillis - now).coerceAtLeast(0) + 999) / 1000

/** The rest countdown along the bottom of the workout screen. */
@Composable
fun RestTimerBar(
    state: RestTimer.State,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val now by rememberNow(intervalMillis = 250)
    val remaining = (state.endsAtMillis - now).coerceAtLeast(0)
    val fraction = (remaining / (state.totalSeconds * 1000f)).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().background(colors.inverseSurface).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.inverseOnSurface.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.inverseOnSurface))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
        ) {
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("Rest", style = MaterialTheme.typography.labelMedium, color = colors.inverseOnSurface.copy(alpha = 0.7f))
                Text(
                    Format.duration(remainingSeconds(state, now)),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = colors.inverseOnSurface,
                )
            }
            TimerButton("−15", "Remove 15 seconds") { onAdjust(-15) }
            TimerButton("+15", "Add 15 seconds") { onAdjust(15) }
            TimerButton("Skip", "Skip rest", onClick = onSkip)
        }
    }
}

@Composable
private fun TimerButton(text: String, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = KinzoTheme.spacing.touchTarget)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.inverseOnSurface.copy(alpha = 0.12f))
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = KinzoTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.inverseOnSurface)
    }
}

/** Sits above the tab bar while a workout is in progress; tap to go back to it. */
@Composable
fun ActiveWorkoutBanner(
    name: String,
    startedAt: Long,
    rest: RestTimer.State?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val now by rememberNow()
    val status = if (rest != null) {
        "Resting · ${Format.duration(remainingSeconds(rest, now))}"
    } else {
        "In progress · ${Format.elapsed(now - startedAt)}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KinzoTheme.spacing.md, vertical = KinzoTheme.spacing.sm)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.inverseSurface)
            .clickable(onClickLabel = "Open workout", onClick = onClick)
            .padding(horizontal = KinzoTheme.spacing.lg, vertical = KinzoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = colors.inverseOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = colors.inverseOnSurface.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.width(KinzoTheme.spacing.sm))
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = colors.inverseOnSurface)
    }
}
