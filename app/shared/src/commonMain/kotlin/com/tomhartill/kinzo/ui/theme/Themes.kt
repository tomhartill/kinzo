package com.tomhartill.kinzo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tomhartill.kinzo.domain.ThemeName

/**
 * The only colours screens may use. Every theme fills in these roles for light and dark mode,
 * and nothing else in the app names a colour, so retuning or adding a theme is an edit to this
 * file alone. There are no borders: cards and chips stand apart from the background by fill.
 */
@Immutable
data class KinzoColors(
    /** The screen behind everything. A pastel tint in light mode, tinted near-black in dark. */
    val background: Color,
    /** Cards, list groups, the bottom bar and sheets. */
    val surface: Color,
    /** Things on a card that need separating: fields, unselected chips, table headers. */
    val surfaceHigh: Color,
    /** Primary buttons, the set-done tick, switches, progress, the chart line, the selected tab. */
    val accent: Color,
    /** Text and icons on [accent]. */
    val onAccent: Color,
    /** Selected chips, highlighted rows, tip cards. Text on it uses [text]. */
    val accentSoft: Color,
    val text: Color,
    /** Labels, dates and other secondary text. */
    val textMuted: Color,
    /** Destructive actions and errors only. The same red family in every theme. */
    val danger: Color,
)

/** A theme's two modes. */
class ThemeColors(val light: KinzoColors, val dark: KinzoColors)

fun ThemeName.colors(dark: Boolean): KinzoColors = with(themeColors()) { if (dark) this.dark else light }

/**
 * Light mode: pastel backgrounds, white cards and a deeper accent with white text. Dark mode:
 * tinted near-black with a pastel accent and dark text. `ThemeContrastTest` checks every pair
 * here meets WCAG AA, so a badly chosen value fails the build rather than shipping.
 */
fun ThemeName.themeColors(): ThemeColors = when (this) {
    ThemeName.Mono -> ThemeColors(
        light = palette(0xFFFFFF, 0xF4F4F5, 0xE9E9EB, 0x111111, 0xFFFFFF, 0xE9E9EB, 0x111111, 0x636363, LIGHT_DANGER),
        dark = palette(0x000000, 0x141414, 0x1F1F1F, 0xFFFFFF, 0x000000, 0x2A2A2A, 0xF5F5F5, 0xA3A3A3, DARK_DANGER),
    )
    ThemeName.Sage -> ThemeColors(
        light = palette(0xF3F7F2, 0xFFFFFF, 0xE4EEE2, 0x3F7D5A, 0xFFFFFF, 0xD5E8D9, 0x17201A, 0x5E6B61, LIGHT_DANGER),
        dark = palette(0x0F1411, 0x18201B, 0x223027, 0x9FD3B0, 0x0F1411, 0x2A3E31, 0xEEF3EF, 0xA3B0A6, DARK_DANGER),
    )
    ThemeName.Sky -> ThemeColors(
        light = palette(0xF1F6FB, 0xFFFFFF, 0xE1ECF7, 0x2F6FB0, 0xFFFFFF, 0xD6E6F6, 0x141B23, 0x5B6672, LIGHT_DANGER),
        dark = palette(0x0E131A, 0x172029, 0x213040, 0x9CC5F0, 0x0E131A, 0x243A52, 0xEDF2F7, 0xA0ACB8, DARK_DANGER),
    )
    ThemeName.Lilac -> ThemeColors(
        light = palette(0xF6F3FB, 0xFFFFFF, 0xECE5F6, 0x6D4FB3, 0xFFFFFF, 0xE3D9F5, 0x1C1724, 0x655E70, LIGHT_DANGER),
        dark = palette(0x13111A, 0x1D1A27, 0x2A2538, 0xC7B5F2, 0x13111A, 0x352C4C, 0xF1EEF7, 0xABA4B8, DARK_DANGER),
    )
    ThemeName.Peach -> ThemeColors(
        light = palette(0xFDF5F0, 0xFFFFFF, 0xF8E7DC, 0xB5562E, 0xFFFFFF, 0xF7DCCB, 0x241812, 0x6E5F57, LIGHT_DANGER),
        dark = palette(0x18120F, 0x231A16, 0x33251E, 0xF2B394, 0x18120F, 0x4A3226, 0xF7F0EC, 0xB5A79F, DARK_DANGER),
    )
    ThemeName.Rose -> ThemeColors(
        light = palette(0xFCF3F5, 0xFFFFFF, 0xF5E2E7, 0xA8436A, 0xFFFFFF, 0xF3D5DF, 0x24161B, 0x6F5C63, LIGHT_DANGER),
        dark = palette(0x180F13, 0x24181D, 0x34232A, 0xF0A9C2, 0x180F13, 0x4A2C38, 0xF7EEF1, 0xB7A3AA, DARK_DANGER),
    )
    // A combination: the accent is a different hue from the background.
    ThemeName.SandTeal -> ThemeColors(
        light = palette(0xF8F5EE, 0xFFFFFF, 0xEEE8DA, 0x1F7A72, 0xFFFFFF, 0xD3EAE6, 0x1F1B14, 0x6A6458, LIGHT_DANGER),
        dark = palette(0x15130F, 0x201D17, 0x2E2A21, 0x8FD6CC, 0x15130F, 0x24413D, 0xF4F1EA, 0xB0AA9C, DARK_DANGER),
    )
}

private const val LIGHT_DANGER = 0xDC2626
private const val DARK_DANGER = 0xF87171

/** Takes plain 0xRRGGBB values, in [KinzoColors] order, so each theme reads as one line. */
private fun palette(
    background: Int,
    surface: Int,
    surfaceHigh: Int,
    accent: Int,
    onAccent: Int,
    accentSoft: Int,
    text: Int,
    textMuted: Int,
    danger: Int,
) = KinzoColors(
    background = rgb(background),
    surface = rgb(surface),
    surfaceHigh = rgb(surfaceHigh),
    accent = rgb(accent),
    onAccent = rgb(onAccent),
    accentSoft = rgb(accentSoft),
    text = rgb(text),
    textMuted = rgb(textMuted),
    danger = rgb(danger),
)

private fun rgb(value: Int) = Color(0xFF000000.toInt() or value)
