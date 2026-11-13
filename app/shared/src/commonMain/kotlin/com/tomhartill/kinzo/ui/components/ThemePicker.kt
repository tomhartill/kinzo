package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.ThemeName
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.theme.colors

/**
 * Every theme as a small preview: its background, a card on it and an accent button, in the
 * mode showing now. Tapping one applies it straight away. Used in Profile and onboarding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(selected: ThemeName, onSelect: (ThemeName) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm),
    ) {
        ThemeName.entries.forEach { theme ->
            ThemeTile(theme, isSelected = theme == selected, onClick = { onSelect(theme) })
        }
    }
}

@Composable
private fun ThemeTile(theme: ThemeName, isSelected: Boolean, onClick: () -> Unit) {
    val current = KinzoTheme.colors
    val preview = theme.colors(KinzoTheme.isDark)
    val spacing = KinzoTheme.spacing
    Column(
        modifier = Modifier
            .width(TileWidth)
            .clip(MaterialTheme.shapes.medium)
            // The tile's own fill keeps a white preview visible on a white screen.
            .background(if (isSelected) current.accentSoft else current.surfaceHigh)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(preview.background)
                .padding(spacing.sm),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(preview.surface)
                    .padding(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(preview.text))
                Box(Modifier.width(22.dp).height(4.dp).clip(CircleShape).background(preview.textMuted))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(preview.accent))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                theme.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = current.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = current.text, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val TileWidth = 96.dp
