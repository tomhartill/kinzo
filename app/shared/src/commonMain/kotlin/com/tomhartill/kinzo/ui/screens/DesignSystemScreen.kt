package com.tomhartill.kinzo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Weight
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.SetRow
import com.tomhartill.kinzo.ui.components.SetTableHeader
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/** Living reference for the design system: every token and component in one place. */
@Composable
fun DesignSystemScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    TitledScreen(title = "Design system", subtitle = "Tokens and components", modifier = modifier) {
        SecondaryButton(text = "Back", onClick = onBack)

        SectionHeader("Colour")
        val colors = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
            listOf(colors.primary, colors.onSurfaceVariant, colors.outline, colors.surfaceContainerHigh, colors.surface, colors.error)
                .forEach { Swatch(it) }
        }

        SectionHeader("Type")
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
            Text("Display", style = MaterialTheme.typography.displayMedium)
            Text("Headline", style = MaterialTheme.typography.headlineMedium)
            Text("Title", style = MaterialTheme.typography.titleMedium)
            Text("Body text for descriptions and instructions.", style = MaterialTheme.typography.bodyMedium)
            Text("Label", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }

        SectionHeader("Buttons")
        PrimaryButton(text = "Primary", onClick = {})
        SecondaryButton(text = "Secondary", onClick = {})
        PrimaryButton(text = "Disabled", onClick = {}, enabled = false)

        SectionHeader("Stats")
        KinzoCard {
            Row(Modifier.fillMaxWidth()) {
                StatTile(value = "100", unit = "kg", label = "Best bench", modifier = Modifier.weight(1f))
                StatTile(value = "12", label = "Workouts", modifier = Modifier.weight(1f))
            }
        }

        SectionHeader("Logging")
        val done = remember { mutableStateListOf(true, false, false) }
        val unit = WeightUnit.Kilograms
        KinzoCard {
            Text("Bench press", style = MaterialTheme.typography.titleMedium)
            SetTableHeader(weightUnit = unit.label)
            done.forEachIndexed { index, isDone ->
                SetRow(
                    setNumber = index + 1,
                    previous = "${Weight.format(80.0, unit)} × 8",
                    weight = Weight.format(82.5, unit),
                    reps = "8",
                    done = isDone,
                    onToggleDone = { done[index] = !isDone },
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(40.dp)
            .background(color, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    )
}
