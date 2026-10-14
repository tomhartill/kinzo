package com.tomhartill.kinzo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.StatTile
import com.tomhartill.kinzo.ui.theme.KinzoTheme

// Placeholder screens. Each one is filled in by its phase 1 milestone.

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    TitledScreen(title = "Today", subtitle = "No workout yet", modifier = modifier) {
        PrimaryButton(text = "Start empty workout", onClick = {}, enabled = false)
        SecondaryButton(text = "Start from a routine", onClick = {}, enabled = false)
        SectionHeader("This week")
        KinzoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xl)) {
                StatTile(value = "0", label = "Workouts", modifier = Modifier.weight(1f))
                StatTile(value = "0", unit = "kg", label = "Volume", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    TitledScreen(title = "History", modifier = modifier) {
        EmptyState(title = "No workouts logged", body = "Finished workouts appear here, newest first.")
    }
}

@Composable
fun ExercisesScreen(modifier: Modifier = Modifier) {
    TitledScreen(title = "Exercises", modifier = modifier) {
        EmptyState(title = "Library coming next", body = "Search exercises and machines by muscle and equipment.")
    }
}

@Composable
fun ProfileScreen(onOpenDesignSystem: () -> Unit, modifier: Modifier = Modifier) {
    TitledScreen(title = "Profile", modifier = modifier) {
        EmptyState(title = "Body stats", body = "Height, weight and body fat tracking arrive with progression.")
        SectionHeader("Developer")
        SecondaryButton(text = "Design system", onClick = onOpenDesignSystem)
    }
}
