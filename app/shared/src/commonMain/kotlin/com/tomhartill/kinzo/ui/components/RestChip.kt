package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.ui.theme.KinzoTheme

/** "Rest 1:30" under an exercise's name; tap to pick another rest time, or none. */
@Composable
fun RestChip(seconds: Int, onSelect: (seconds: Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val label = if (seconds > 0) "Rest ${restLabel(seconds)}" else "Rest off"
    Box(modifier) {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Change rest time", role = Role.DropdownList) { open = true }
                .heightIn(min = 32.dp)
                .padding(start = KinzoTheme.spacing.sm, end = KinzoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs),
        ) {
            Icon(KinzoIcons.Timer, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = colors.onSurface)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Settings.REST_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(restLabel(option)) },
                    trailingIcon = if (option == seconds) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
