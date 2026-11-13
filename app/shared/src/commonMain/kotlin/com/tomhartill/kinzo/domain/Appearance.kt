package com.tomhartill.kinzo.domain

/**
 * The colour themes someone can pick in Profile. The colours themselves live in
 * `ui/theme/Themes.kt`; this is just the choice, so settings and tests don't need Compose.
 * [key] is what's stored, so it never changes once shipped, even if [label] does.
 */
enum class ThemeName(val key: String, val label: String) {
    Mono("mono", "Mono"),
    Sage("sage", "Sage"),
    Sky("sky", "Sky"),
    Lilac("lilac", "Lilac"),
    Peach("peach", "Peach"),
    Rose("rose", "Rose"),
    SandTeal("sand_teal", "Sand & Teal");

    companion object {
        /** Unknown or removed themes fall back to Mono, so a rename never breaks anyone. */
        fun fromKey(key: String?): ThemeName = entries.firstOrNull { it.key == key } ?: Mono
    }
}

/** Light or dark: follow the phone, or always one. */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}
