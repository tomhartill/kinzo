package com.tomhartill.kinzo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few Material Symbols the app needs that aren't in material-icons-core. The paths are
 * copied from Material's icon set (Apache 2.0), which keeps the extended icon library and its
 * size out of the app.
 */
object KinzoIcons {
    /** Two bars: grab here to drag. */
    val DragHandle: ImageVector by lazy { icon("DragHandle", "M20,9H4v2h16V9zM4,15h16v-2H4v2z") }

    /** A stopwatch, for rest times. */
    val Timer: ImageVector by lazy {
        icon(
            "Timer",
            "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42" +
                "C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61z" +
                "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    /** A dumbbell, for the Library tab. */
    val Dumbbell: ImageVector by lazy {
        icon(
            "Dumbbell",
            "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 " +
                "2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43" +
                "L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29z",
        )
    }

    /** Three sparkles, for anything the AI coach makes. */
    val Sparkle: ImageVector by lazy {
        icon(
            "Sparkle",
            "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z" +
                "M11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12L11.5,9.5z" +
                "M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z",
        )
    }

    /** A minus sign, the other half of Add in a stepper. */
    val Remove: ImageVector by lazy { icon("Remove", "M19,13H5v-2h14v2z") }

    /** A camera, for scanning a machine and taking progress photos. */
    val Camera: ImageVector by lazy {
        icon(
            "Camera",
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
