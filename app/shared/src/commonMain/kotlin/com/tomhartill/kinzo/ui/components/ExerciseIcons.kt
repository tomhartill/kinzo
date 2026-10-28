package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The movement pictograms bundled in files/exercise_icons.json (drawn by tools/exercise-icons):
 * one per kind of movement, like "squat" or "row". Each exercise names its own in the catalogue.
 * The drawings are black; [Icon] tints them like any other icon.
 */
class ExerciseIconSet private constructor(private val file: IconFile) {
    private val built = HashMap<String, ImageVector>()

    /** The pictogram called [key], the generic one for a key it doesn't know, or null for none. */
    fun icon(key: String?): ImageVector? {
        key ?: return null
        val known = if (key in file.icons) key else GENERIC
        return built[known] ?: build(known)?.also { built[known] = it }
    }

    private fun build(key: String): ImageVector? {
        val paths = file.icons[key] ?: return null
        val builder = ImageVector.Builder(
            name = "exercise-$key",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = file.viewport,
            viewportHeight = file.viewport,
        )
        paths.forEach { path ->
            val nodes = PathParser().parsePathString(path.d).toNodes()
            if (path.fill) {
                builder.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            } else {
                builder.addPath(
                    pathData = nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = path.stroke ?: DEFAULT_STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return builder.build()
    }

    @Serializable
    private class IconFile(val viewport: Float, val icons: Map<String, List<IconPath>>)

    /** A filled shape, or a line [stroke] units wide. */
    @Serializable
    private class IconPath(val d: String, val stroke: Float? = null, val fill: Boolean = false)

    companion object {
        const val RESOURCE_PATH = "files/exercise_icons.json"
        const val GENERIC = "generic"
        private const val DEFAULT_STROKE = 3f
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExerciseIconSet = ExerciseIconSet(json.decodeFromString(IconFile.serializer(), text))
    }
}

/** The app's pictograms, once loaded. Null until then, or if they couldn't be read. */
val LocalExerciseIcons = staticCompositionLocalOf<ExerciseIconSet?> { null }

/**
 * An exercise's pictogram on a filled square, [size] across. Keeps its space while the
 * pictograms load, so lists don't shift.
 */
@Composable
fun ExerciseIcon(key: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .background(colors.surfaceContainerHigh, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        val icon = LocalExerciseIcons.current?.icon(key ?: ExerciseIconSet.GENERIC)
        if (icon != null) {
            // Decorative: the exercise's name is right beside it.
            Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(size * 3 / 4))
        }
    }
}
