package com.tomhartill.kinzo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Google and Facebook marks for the sign-in buttons, drawn the way each brand's sign-in
 * guidelines ask: Google's four-colour G, and Facebook's white f on Facebook blue.
 */
object BrandIcons {
    val Google: ImageVector by lazy {
        ImageVector.Builder(name = "Google", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 48f, viewportHeight = 48f)
            .path(
                Color(0xFFEA4335),
                "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22" +
                    "l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            )
            .path(
                Color(0xFF4285F4),
                "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6" +
                    "c4.51-4.18 7.09-10.36 7.09-17.65z",
            )
            .path(
                Color(0xFFFBBC05),
                "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24" +
                    "c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            )
            .path(
                Color(0xFF34A853),
                "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91" +
                    "l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
            )
            .build()
    }

    /** The f alone, to draw in white on [FacebookBlue]. */
    val FacebookF: ImageVector by lazy {
        ImageVector.Builder(name = "Facebook", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                Color.White,
                "M13.5 21v-7.5h2.5l.4-3h-2.9V8.6c0-.87.24-1.46 1.49-1.46H16.6V4.46A21 21 0 0 0 14.32 4.34" +
                    "c-2.26 0-3.82 1.38-3.82 3.92v2.24H8v3h2.5V21h3z",
            )
            .build()
    }

    val FacebookBlue = Color(0xFF1877F2)

    private fun ImageVector.Builder.path(color: Color, data: String): ImageVector.Builder =
        addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(color))
}
