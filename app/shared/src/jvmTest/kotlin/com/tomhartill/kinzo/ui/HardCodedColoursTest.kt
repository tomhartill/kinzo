package com.tomhartill.kinzo.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Screens and components take every colour from the theme, so a theme can change in one file.
 * This fails the build when one names a colour by its hex value instead.
 */
class HardCodedColoursTest {
    @Test
    fun noHexColoursOutsideTheTheme() {
        val ui = File("src/commonMain/kotlin/com/tomhartill/kinzo/ui")
        assertTrue(ui.isDirectory, "Expected to run from the shared module, but ${ui.absolutePath} isn't there")
        val offenders = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/ui/theme/") }
            .filterNot { it.name in BrandColourFiles }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (HexColour.containsMatchIn(line)) "${file.relativeTo(ui).invariantSeparatorsPath}:${index + 1}" else null
                }
            }
            .toList()
        assertTrue(offenders.isEmpty(), "Use a colour role from KinzoTheme.colors or MaterialTheme instead: $offenders")
    }

    private companion object {
        val HexColour = Regex("""\bColor\(0x""")

        // Google and Facebook require their own brand colours on their sign-in buttons.
        val BrandColourFiles = setOf("BrandIcons.kt", "SignInPanel.kt")
    }
}
