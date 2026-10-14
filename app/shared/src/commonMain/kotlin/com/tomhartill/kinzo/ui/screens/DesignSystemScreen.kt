package com.tomhartill.kinzo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.PreviousSet
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.SetEditorRow
import com.tomhartill.kinzo.ui.components.SetSummaryRow
import com.tomhartill.kinzo.ui.components.SetTableHeader
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/** Living reference for the design system: every token and component in one place. */
@Composable
fun DesignSystemScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = KinzoTheme.spacing
    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(title = "Design system", subtitle = "Tokens and components", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionHeader("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                listOf(colors.primary, colors.onSurfaceVariant, colors.outline, colors.surfaceContainerHigh, colors.surface, colors.error)
                    .forEach { Swatch(it) }
            }

            SectionHeader("Type")
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
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
            var unit by remember { mutableStateOf(WeightUnit.Kilograms) }
            ChoiceRow(options = WeightUnit.entries, selected = unit, optionLabel = { it.label }, onSelect = { unit = it })

            SectionHeader("Stats")
            KinzoCard {
                Row(Modifier.fillMaxWidth()) {
                    StatTile(value = "100", unit = unit.label, label = "Best bench", modifier = Modifier.weight(1f))
                    StatTile(value = "12", label = "Workouts", modifier = Modifier.weight(1f))
                }
            }

            SectionHeader("Logging")
            LoggingSample(unit)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** A working set table backed by local state, to try the logging controls without a workout. */
@Composable
private fun LoggingSample(unit: WeightUnit) {
    val sets = remember {
        mutableStateListOf(
            LoggedSet("a", SetValues(reps = 10, weightKg = 40.0), isWarmup = true, completedAt = 1L),
            LoggedSet("b", SetValues(reps = 8, weightKg = 80.0), isWarmup = false, completedAt = 1L),
            LoggedSet("c", SetValues(), isWarmup = false, completedAt = null),
        )
    }
    val previous = listOf(
        PreviousSet(SetValues(reps = 10, weightKg = 40.0), isWarmup = true),
        PreviousSet(SetValues(reps = 8, weightKg = 77.5), isWarmup = false),
        PreviousSet(SetValues(reps = 8, weightKg = 77.5), isWarmup = false),
    )
    val type = MeasurementType.WeightReps
    val labels = SetRules.labels(sets.map { it.isWarmup })
    KinzoCard {
        Text("Bench press", style = MaterialTheme.typography.titleMedium)
        Column {
            SetTableHeader(type = type, unit = unit)
            sets.forEachIndexed { index, set ->
                SetEditorRow(
                    label = labels[index],
                    set = set,
                    previous = previous.getOrNull(index),
                    type = type,
                    unit = unit,
                    onValuesChange = { sets[index] = set.copy(values = it) },
                    onToggleDone = { entered ->
                        if (set.isCompleted) {
                            sets[index] = set.copy(completedAt = null)
                            true
                        } else {
                            val values = SetRules.resolveForCompletion(type, entered, previous.getOrNull(index)?.values)
                            if (values != null) sets[index] = set.copy(values = values, completedAt = 1L)
                            values != null
                        }
                    },
                    onToggleWarmup = { sets[index] = set.copy(isWarmup = !set.isWarmup) },
                    onRemove = { sets.removeAt(index) },
                )
            }
        }
    }
    val previousLabels = SetRules.labels(previous.map { it.isWarmup })
    KinzoCard {
        Text("Finished sets", style = MaterialTheme.typography.titleMedium)
        Column {
            previous.forEachIndexed { index, set ->
                SetSummaryRow(
                    label = previousLabels[index],
                    text = Format.set(type, set.values, unit),
                    isWarmup = set.isWarmup,
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
