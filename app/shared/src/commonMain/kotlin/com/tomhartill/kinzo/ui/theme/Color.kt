package com.tomhartill.kinzo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Maps the theme's roles onto Material's, so stock components (switches, progress bars,
 * menus, dialogs, the date picker) follow the theme with no per-screen work. Outlines are
 * only used for faint guide lines in charts and lists, never around things.
 */
internal fun KinzoColors.toColorScheme(dark: Boolean): ColorScheme {
    val outline = lerp(surfaceHigh, textMuted, 0.4f)
    val onDanger = if (danger.luminance() > 0.4f) Color.Black else Color.White
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        // The bottom bar's selected-tab pill uses secondaryContainer.
        secondaryContainer = accentSoft,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textMuted,
        // No tonal tint: elevation never changes a surface's colour.
        surfaceTint = surface,
        inverseSurface = text,
        inverseOnSurface = background,
        error = danger,
        onError = onDanger,
        errorContainer = danger.copy(alpha = 0.15f),
        onErrorContainer = text,
        outline = outline,
        outlineVariant = surfaceHigh,
        surfaceBright = surfaceHigh,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
    )
}
