package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.Split
import com.tomhartill.kinzo.domain.WorkoutType
import com.tomhartill.kinzo.ui.components.DropdownChoice
import com.tomhartill.kinzo.ui.components.DropdownField
import kotlinx.datetime.LocalDate

/** Resistance training, swimming and so on. */
@Composable
internal fun WorkoutTypeField(type: WorkoutType, onChange: (WorkoutType) -> Unit, modifier: Modifier = Modifier) {
    DropdownField(label = "Type", value = type.label, modifier = modifier) { close ->
        WorkoutType.entries.forEach { option ->
            DropdownChoice(text = option.label, selected = option == type, onClick = {
                close()
                onChange(option)
            })
        }
    }
}

/**
 * Which split the workout trains, from [splits] (the ones for its type), or none. The user's
 * own splits can be removed here, and new ones added.
 */
@Composable
internal fun SplitField(
    selectedId: String?,
    selectedName: String?,
    splits: List<Split>,
    onChange: (Split?) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Split) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    DropdownField(label = "Split", value = selectedName ?: "None", modifier = modifier) { close ->
        DropdownChoice(text = "None", selected = selectedId == null, onClick = {
            close()
            onChange(null)
        })
        splits.forEach { split ->
            DropdownChoice(
                text = split.name,
                selected = split.id == selectedId,
                onClick = {
                    close()
                    onChange(split)
                },
                trailing = if (split.isBuiltIn) {
                    null
                } else {
                    {
                        IconButton(onClick = {
                            close()
                            onDelete(split)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${split.name}", tint = colors.onSurfaceVariant)
                        }
                    }
                },
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
        DropdownMenuItem(
            text = { Text("Add a split") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onSurface) },
            onClick = {
                close()
                onAdd()
            },
        )
    }
}

/** The mesocycle the workout belongs to, the current one unless the user picks another. */
@Composable
internal fun MesocycleField(
    selectedId: String?,
    mesocycles: List<Mesocycle>,
    today: LocalDate,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = mesocycles.firstOrNull()
    val selected = mesocycles.firstOrNull { it.id == selectedId } ?: current ?: return
    DropdownField(
        label = "Mesocycle",
        value = selected.name,
        supportingText = mesocycleLine(selected, isCurrent = selected == current, today = today),
        modifier = modifier,
    ) { close ->
        mesocycles.forEach { mesocycle ->
            DropdownChoice(
                text = mesocycle.name,
                supportingText = mesocycleLine(mesocycle, isCurrent = mesocycle == current, today = today),
                selected = mesocycle == selected,
                onClick = {
                    close()
                    onChange(mesocycle.id)
                },
            )
        }
    }
}

/** "14 Oct – 5 Jan 2027 · Current". */
internal fun mesocycleLine(mesocycle: Mesocycle, isCurrent: Boolean, today: LocalDate): String {
    val dates = Dates.dateRangeLabel(mesocycle.start, mesocycle.end, today)
    return if (isCurrent) "$dates · Current" else dates
}
