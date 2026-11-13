package com.tomhartill.kinzo.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tomhartill.kinzo.data.DemoProfile
import com.tomhartill.kinzo.ui.components.kinzoSwitchColors
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The switch between your own workouts and the demo profile's made-up ones. */
@Composable
internal fun DemoProfileSetting(demo: DemoProfile = koinInject()) {
    val scope = rememberCoroutineScope()
    // Set once the switch is saved, where the app can't restart itself.
    var reopenToFinish by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
            Text("Demo profile", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (reopenToFinish) {
                    "Close Kinzo and open it again to switch."
                } else {
                    "Four months of made-up workouts to try the progress charts on. Kinzo restarts to switch, and your own workouts are kept apart and come back when you switch off."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(
            checked = demo.isActive != reopenToFinish,
            enabled = !switching && !reopenToFinish,
            onCheckedChange = { on ->
                switching = true
                scope.launch {
                    if (!demo.switchTo(on)) reopenToFinish = true
                    switching = false
                }
            },
            colors = kinzoSwitchColors(),
        )
    }
}

/** A reminder at the top of Profile that what's showing isn't real. */
@Composable
internal fun DemoProfileNotice(demo: DemoProfile = koinInject()) {
    if (!demo.isActive) return
    Text(
        "You're in the demo profile: every workout here is made up. Switch back under Developer at the bottom.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
