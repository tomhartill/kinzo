package com.tomhartill.kinzo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.ThemeName
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every theme stays readable: a colour added or changed later that isn't fails here, not in the app. */
class ThemeContrastTest {
    @Test
    fun bodyTextIsEasyToReadOnEveryBackground() = eachTheme { name, colors ->
        listOf("background" to colors.background, "surface" to colors.surface, "surfaceHigh" to colors.surfaceHigh, "accentSoft" to colors.accentSoft)
            .forEach { (role, background) -> assertContrast(name, "text on $role", colors.text, background, AAA) }
    }

    @Test
    fun mutedTextMeetsAA() = eachTheme { name, colors ->
        listOf("background" to colors.background, "surface" to colors.surface, "surfaceHigh" to colors.surfaceHigh)
            .forEach { (role, background) -> assertContrast(name, "textMuted on $role", colors.textMuted, background, AA) }
    }

    @Test
    fun buttonTextMeetsAA() = eachTheme { name, colors ->
        assertContrast(name, "onAccent on accent", colors.onAccent, colors.accent, AA)
    }

    @Test
    fun keysAreUniqueAndUnknownOnesFallBack() {
        assertEquals(ThemeName.entries.size, ThemeName.entries.map { it.key }.toSet().size)
        assertEquals(ThemeName.Mono, ThemeName.fromKey("retired-theme"))
        assertEquals(ThemeName.Mono, ThemeName.fromKey(null))
        assertEquals(ThemeName.SandTeal, ThemeName.fromKey("sand_teal"))
        assertEquals(ThemeMode.System, ThemeMode.fromKey("bogus"))
    }

    private fun eachTheme(check: (String, KinzoColors) -> Unit) {
        ThemeName.entries.forEach { theme ->
            check("${theme.label} light", theme.colors(dark = false))
            check("${theme.label} dark", theme.colors(dark = true))
        }
    }

    private fun assertContrast(theme: String, what: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$theme: $what is ${(ratio * 100).toInt() / 100.0}:1, needs $minimum:1")
    }

    // WCAG 2 contrast ratio.
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private companion object {
        const val AA = 4.5
        const val AAA = 7.0
    }
}
