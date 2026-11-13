package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.ui.theme.KinzoTheme

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/** Solid button for the one main action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = KinzoTheme.spacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Filled, quieter button for secondary actions. No outline: the fill sets it apart. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = KinzoTheme.spacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        colors = secondaryButtonColors(),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Colours for secondary buttons anywhere: a filled track in the card colour, plain text. */
@Composable
fun secondaryButtonColors(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        disabledContainerColor = colors.surfaceContainerHigh,
        disabledContentColor = colors.onSurfaceVariant,
    )
}
