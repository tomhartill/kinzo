package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.domain.Trend
import com.tomhartill.kinzo.domain.TrendDirection

/**
 * A tiny line of recent progress with an up, down or flat mark beside it, for rows in a list.
 * No axes or labels: it shows the shape, and the detail screen has the real chart.
 *
 * Drawn straight onto a canvas rather than with the chart library, and the path is built once
 * per size and trend ([drawWithCache]), so a list of hundreds of rows scrolls smoothly. Only
 * the rows on screen are ever drawn.
 */
@Composable
fun TrendSparkline(trend: Trend, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = when (trend.direction) {
        TrendDirection.Up -> "Trending up"
        TrendDirection.Down -> "Trending down"
        TrendDirection.Flat -> "Holding steady"
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 48.dp, height = 20.dp).sparkline(trend.values, colors.onSurfaceVariant))
        Box(Modifier.size(8.dp).directionMark(trend.direction, colors.onSurface))
    }
}

private fun Modifier.sparkline(values: List<Double>, color: Color): Modifier = drawWithCache {
    val stroke = 1.5.dp.toPx()
    val dot = 2.dp.toPx()
    // Inset by the dot so the line's ends and the last point aren't clipped.
    val left = dot
    val top = dot
    val width = size.width - dot * 2
    val height = size.height - dot * 2
    val low = values.min()
    val high = values.max()
    val span = high - low
    val points = values.mapIndexed { index, value ->
        val x = left + width * index / (values.size - 1)
        // A flat line sits in the middle rather than along the bottom.
        val y = if (span == 0.0) top + height / 2 else top + height * (1 - ((value - low) / span).toFloat())
        Offset(x, y)
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    onDrawBehind {
        drawPath(path, color, style = style)
        drawCircle(color, radius = dot, center = points.last())
    }
}

/** A small triangle pointing up or down, or a short dash when holding steady. */
private fun Modifier.directionMark(direction: TrendDirection, color: Color): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val triangle = when (direction) {
        TrendDirection.Up -> Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        TrendDirection.Down -> Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w / 2, h)
            close()
        }
        TrendDirection.Flat -> null
    }
    val dash = 1.5.dp.toPx()
    onDrawBehind {
        if (triangle != null) {
            drawPath(triangle, color)
        } else {
            drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = dash, cap = StrokeCap.Round)
        }
    }
}
