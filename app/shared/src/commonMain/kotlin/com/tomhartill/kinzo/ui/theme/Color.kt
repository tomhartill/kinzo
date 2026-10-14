package com.tomhartill.kinzo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Neutral, monochrome palette. Branding later means changing this file and Type.kt only.
internal object Palette {
    val Black = Color(0xFF000000)
    val Gray950 = Color(0xFF0A0A0A)
    val Gray900 = Color(0xFF171717)
    val Gray800 = Color(0xFF262626)
    val Gray700 = Color(0xFF404040)
    val Gray500 = Color(0xFF737373)
    val Gray400 = Color(0xFFA3A3A3)
    val Gray300 = Color(0xFFD4D4D4)
    val Gray200 = Color(0xFFE5E5E5)
    val Gray100 = Color(0xFFF5F5F5)
    val Gray50 = Color(0xFFFAFAFA)
    val White = Color(0xFFFFFFFF)

    // The only non-neutral colour, reserved for destructive actions and errors.
    val Red = Color(0xFFDC2626)
    val RedDark = Color(0xFFF87171)
}

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.Black,
    onPrimary = Palette.White,
    primaryContainer = Palette.Gray100,
    onPrimaryContainer = Palette.Black,
    secondary = Palette.Gray700,
    onSecondary = Palette.White,
    secondaryContainer = Palette.Gray100,
    onSecondaryContainer = Palette.Black,
    tertiary = Palette.Gray700,
    onTertiary = Palette.White,
    background = Palette.White,
    onBackground = Palette.Black,
    surface = Palette.White,
    onSurface = Palette.Black,
    surfaceVariant = Palette.Gray100,
    onSurfaceVariant = Palette.Gray500,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Palette.Gray50,
    surfaceContainer = Palette.Gray50,
    surfaceContainerHigh = Palette.Gray100,
    surfaceContainerHighest = Palette.Gray200,
    outline = Palette.Gray300,
    outlineVariant = Palette.Gray200,
    error = Palette.Red,
    onError = Palette.White,
    inverseSurface = Palette.Black,
    inverseOnSurface = Palette.White,
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.White,
    onPrimary = Palette.Black,
    primaryContainer = Palette.Gray900,
    onPrimaryContainer = Palette.White,
    secondary = Palette.Gray300,
    onSecondary = Palette.Black,
    secondaryContainer = Palette.Gray900,
    onSecondaryContainer = Palette.White,
    tertiary = Palette.Gray300,
    onTertiary = Palette.Black,
    background = Palette.Black,
    onBackground = Palette.White,
    surface = Palette.Black,
    onSurface = Palette.White,
    surfaceVariant = Palette.Gray900,
    onSurfaceVariant = Palette.Gray400,
    surfaceContainerLowest = Palette.Black,
    surfaceContainerLow = Palette.Gray950,
    surfaceContainer = Palette.Gray950,
    surfaceContainerHigh = Palette.Gray900,
    surfaceContainerHighest = Palette.Gray800,
    outline = Palette.Gray700,
    outlineVariant = Palette.Gray800,
    error = Palette.RedDark,
    onError = Palette.Black,
    inverseSurface = Palette.White,
    inverseOnSurface = Palette.Black,
)
