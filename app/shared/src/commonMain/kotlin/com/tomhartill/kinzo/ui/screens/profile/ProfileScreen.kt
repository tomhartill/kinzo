package com.tomhartill.kinzo.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.MenuSettingRow
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.TitledScreen
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** The Profile tab: units and workout settings for now; body stats come with progression. */
@Composable
fun ProfileScreen(
    onOpenDesignSystem: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    TitledScreen(title = "Profile", modifier = modifier) {
        val current = settings
        if (current != null) {
            SectionHeader("Units")
            Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                ChoiceRow(
                    options = WeightUnit.entries,
                    selected = current.weightUnit,
                    optionLabel = { if (it == WeightUnit.Kilograms) "Kilograms" else "Pounds" },
                    onSelect = viewModel::setWeightUnit,
                )
                Text(
                    "Changes how weights show everywhere. What you've logged stays the same.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            SectionHeader("Workouts")
            MenuSettingRow(
                title = "Rest timer",
                description = "Starts when you tick a set",
                options = Settings.REST_OPTIONS,
                selected = current.restSeconds,
                optionLabel = { if (it == 0) "Off" else Format.duration(it) },
                onSelect = viewModel::setRestSeconds,
            )
        }

        SectionHeader("Body")
        EmptyState(title = "Body stats", body = "Height, weight and body fat tracking arrive with progression.")

        SectionHeader("About")
        Text(
            "Exercise library from free-exercise-db, released into the public domain.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        SectionHeader("Developer")
        SecondaryButton(text = "Design system", onClick = onOpenDesignSystem)
    }
}
