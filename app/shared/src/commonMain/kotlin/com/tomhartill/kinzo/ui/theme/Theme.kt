package com.tomhartill.kinzo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.ThemeName

private val LocalKinzoColors = staticCompositionLocalOf { ThemeName.Mono.colors(dark = false) }
private val LocalIsDark = staticCompositionLocalOf { false }

/** How long switching theme or mode takes, so the change feels intentional rather than a flash. */
private const val THEME_CHANGE_MILLIS = 200

@Composable
fun KinzoTheme(
    theme: ThemeName = ThemeName.Mono,
    mode: ThemeMode = ThemeMode.System,
    /** False for a preview inside the app, so it doesn't take over the phone's status bar. */
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = animateColors(theme.colors(dark))
    val scheme = remember(colors, dark) { colors.toColorScheme(dark) }
    if (applySystemBars) SystemBarsAppearance(dark)
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalKinzoColors provides colors,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KinzoTypography,
            shapes = KinzoShapes,
        ) {
            // Screens paint their own backgrounds rather than sitting on a Surface, so icons and
            // text without an explicit colour would otherwise fall back to black in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

@Composable
private fun animateColors(target: KinzoColors): KinzoColors {
    @Composable
    fun animate(color: Color): Color = animateColorAsState(color, tween(THEME_CHANGE_MILLIS)).value
    return KinzoColors(
        background = animate(target.background),
        surface = animate(target.surface),
        surfaceHigh = animate(target.surfaceHigh),
        accent = animate(target.accent),
        onAccent = animate(target.onAccent),
        accentSoft = animate(target.accentSoft),
        text = animate(target.text),
        textMuted = animate(target.textMuted),
        danger = animate(target.danger),
    )
}

/**
 * Sets the status and navigation bar icons to dark or light to match the app rather than the
 * phone, since someone can pick Light while their phone is in dark mode.
 */
@Composable
internal expect fun SystemBarsAppearance(dark: Boolean)

/** Kinzo-specific tokens that MaterialTheme does not carry. */
object KinzoTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    /** The current theme's colours, by role. Prefer these to MaterialTheme's in our own components. */
    val colors: KinzoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKinzoColors.current

    /** Whether the app is showing its dark mode, which may differ from the phone's. */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDark.current
}
