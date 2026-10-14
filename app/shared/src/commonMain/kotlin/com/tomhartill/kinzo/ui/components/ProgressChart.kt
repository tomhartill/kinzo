package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.tomhartill.kinzo.domain.ChartScale
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.ProgressPoint
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone

/**
 * A line of one value per session over time, for any progression: a lift's estimated 1RM,
 * heaviest set, volume, reps or time. [points] are oldest first and already in display units.
 * [formatValue] writes a value for the axis; pressing a point shows it with [unit] and the date.
 *
 * Sessions are evenly spaced rather than placed by date, so a gap in training doesn't squash
 * the rest of the line. Long histories scroll sideways and open on the most recent sessions.
 */
@Composable
fun ProgressChart(
    points: List<ProgressPoint>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    if (points.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val zone = remember { TimeZone.currentSystemDefault() }
    val scale = remember(points) { ChartScale.around(points.minOf { it.value }, points.maxOf { it.value }) }

    val model = remember(points) {
        CartesianChartModel(LineCartesianLayerModel.build { series(x = points.indices.toList(), y = points.map { it.value }) })
    }
    val line = LineCartesianLayer.rememberLine(
        fill = remember(colors.onSurface) { LineCartesianLayer.LineFill.single(Fill(colors.onSurface)) },
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp, cap = StrokeCap.Round),
        areaFill = remember(colors.onSurface) {
            LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(colors.onSurface.copy(alpha = 0.12f), colors.onSurface.copy(alpha = 0f)))),
            )
        },
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                rememberShapeComponent(
                    fill = Fill(colors.surface),
                    shape = CircleShape,
                    strokeFill = Fill(colors.onSurface),
                    strokeThickness = 2.dp,
                ),
                size = 8.dp,
            ),
        ),
        interpolator = LineCartesianLayer.Interpolator.catmullRom(),
    )
    val layer = rememberLineCartesianLayer(
        lineProvider = remember(line) { LineCartesianLayer.LineProvider.series(line) },
        pointSpacing = 40.dp,
        rangeProvider = remember(scale) { CartesianLayerRangeProvider.fixed(minY = scale.min, maxY = scale.max) },
    )

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val axisLabel = rememberAxisLabelComponent(style = labelStyle)
    val guideline = rememberAxisGuidelineComponent(fill = Fill(colors.outlineVariant), shape = remember { DashedShape() })
    val valueAxis = VerticalAxis.rememberStart(
        line = null,
        label = axisLabel,
        valueFormatter = remember(formatValue) { CartesianValueFormatter { _, value, _ -> formatValue(value) } },
        tick = null,
        guideline = guideline,
        itemPlacer = remember(scale) { VerticalAxis.ItemPlacer.step({ scale.step }) },
    )
    val dateAxis = HorizontalAxis.rememberBottom(
        line = rememberLineComponent(fill = Fill(colors.outlineVariant), thickness = 1.dp),
        label = axisLabel,
        valueFormatter = remember(points) {
            CartesianValueFormatter { _, x, _ -> points.getOrNull(x.toInt())?.let { Dates.shortDayLabel(it.atMillis, zone) } ?: "" }
        },
        tick = null,
        guideline = null,
    )

    val markerLabel = rememberTextComponent(
        style = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontFeatureSettings = "tnum"),
        padding = Insets(horizontal = KinzoTheme.spacing.sm, vertical = KinzoTheme.spacing.xs),
        background = rememberShapeComponent(
            fill = Fill(colors.surface),
            shape = MaterialTheme.shapes.small,
            strokeFill = Fill(colors.outline),
            strokeThickness = 1.dp,
        ),
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = remember(points, formatValue, unit) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val target = targets.firstOrNull() as? LineCartesianLayerMarkerTarget
                val point = target?.let { points.getOrNull(it.x.toInt()) }
                if (point == null) "" else listOfNotNull(formatValue(point.value), unit).joinToString(" ") + " · " + Dates.shortDayLabel(point.atMillis, zone)
            }
        },
        labelPosition = DefaultCartesianMarker.LabelPosition.Top,
        guideline = rememberLineComponent(fill = Fill(colors.outline), thickness = 1.dp),
    )

    CartesianChartHost(
        chart = rememberCartesianChart(layer, startAxis = valueAxis, bottomAxis = dateAxis, marker = marker),
        model = model,
        modifier = modifier.fillMaxWidth().height(200.dp),
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
    )
}
