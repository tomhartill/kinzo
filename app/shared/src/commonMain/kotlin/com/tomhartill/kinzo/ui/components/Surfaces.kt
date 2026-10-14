package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/** Flat card with a hairline border; no shadows anywhere in the app. */
@Composable
fun KinzoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(KinzoTheme.spacing.xxs / 2, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(KinzoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
            content = content,
        )
    }
}

/** Small uppercase label above a group of content. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A headline number with its label, e.g. "12,450 kg  Volume this week". */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
            if (unit != null) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = KinzoTheme.spacing.xxs),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
