package com.tomhartill.kinzo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tomhartill.kinzo.domain.Weight
import com.tomhartill.kinzo.domain.WeightChange
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.theme.NumericStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val BarHeight = 54.dp

/** Keeps the ends of the slider reachable without the thumb sliding off the bar. */
private val EdgePadding = 18.dp

/** How far above or below the bar the thumb can go before letting go cancels. */
private val CancelDistance = 40.dp

/** Holding still this long opens the slider; letting go sooner is a tap. */
private const val HoldMillis = 200L

private enum class Start { Tap, Scroll, Slide }

/**
 * The add set button. A tap adds a set with the last set's numbers filled in. Holding it, or
 * starting to slide sideways, opens a slider: the middle keeps the same weight, the left edge is
 * 50% lighter and the right edge 50% heavier, moving one loadable step at a time. Letting go
 * adds the set at the weight shown; sliding up or down off the bar first cancels. With no weight
 * to start from ([baseKg] null) it's a plain button.
 */
@Composable
fun AddSetBar(
    baseKg: Double?,
    unit: WeightUnit,
    onAdd: (percent: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val canSlide = baseKg != null
    var sliding by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    // -1 at the left edge, 0 in the middle, 1 at the right edge.
    var position by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableIntStateOf(0) }
    val currentBase by rememberUpdatedState(baseKg)
    val currentUnit by rememberUpdatedState(unit)
    val currentOnAdd by rememberUpdatedState(onAdd)

    val active = sliding && !cancelled
    val background by animateColorAsState(if (active) colors.primary else colors.surfaceContainerHigh)
    val content = if (active) colors.onPrimary else colors.onSurface
    val percent = WeightChange.sliderPercent(position)

    val gestures = if (!canSlide) {
        Modifier.clickable(onClickLabel = "Add set", role = Role.Button) { onAdd(0.0) }
    } else {
        Modifier
            .semantics {
                role = Role.Button
                onClick(label = "Add set") { currentOnAdd(0.0); true }
                customActions = listOf(
                    CustomAccessibilityAction("Add a set 10% lighter") { currentOnAdd(-10.0); true },
                    CustomAccessibilityAction("Add a set 10% heavier") { currentOnAdd(10.0); true },
                )
            }
            .pointerInput(Unit) {
                val pad = EdgePadding.toPx()
                val cancelAt = CancelDistance.toPx()
                fun positionAt(x: Float): Float = ((x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f) * 2 - 1
                fun weightAt(at: Float): Double? =
                    currentBase?.let { WeightChange.adjust(it, WeightChange.sliderPercent(at), currentUnit) }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    var x = down.position.x
                    // Work out whether this is a tap, a scroll of the list, or the start of a slide.
                    // Nothing is consumed until a slide starts, so the list can still scroll.
                    val start = withTimeoutOrNull(HoldMillis) {
                        var decided: Start? = null
                        while (decided == null) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            decided = when {
                                change == null || change.isConsumed -> Start.Scroll
                                !change.pressed -> {
                                    change.consume()
                                    Start.Tap
                                }
                                else -> {
                                    x = change.position.x
                                    val moved = change.position - down.position
                                    when {
                                        moved.getDistance() <= viewConfiguration.touchSlop -> null
                                        abs(moved.x) > abs(moved.y) -> Start.Slide
                                        else -> Start.Scroll
                                    }
                                }
                            }
                        }
                        decided
                    } ?: Start.Slide

                    when (start) {
                        Start.Scroll -> return@awaitEachGesture
                        Start.Tap -> {
                            currentOnAdd(0.0)
                            return@awaitEachGesture
                        }
                        Start.Slide -> Unit
                    }

                    sliding = true
                    cancelled = false
                    position = positionAt(x)
                    var lastWeight = weightAt(position)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    var released = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            released = true
                            break
                        }
                        change.consume()
                        position = positionAt(change.position.x)
                        val y = change.position.y
                        val off = y < -cancelAt || y > size.height + cancelAt
                        if (off != cancelled) {
                            cancelled = off
                            haptics.performHapticFeedback(if (off) HapticFeedbackType.Reject else HapticFeedbackType.GestureThresholdActivate)
                        }
                        val weight = weightAt(position)
                        if (!off && weight != lastWeight) {
                            lastWeight = weight
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                    val chosen = WeightChange.sliderPercent(position).takeIf { released && !cancelled }
                    sliding = false
                    cancelled = false
                    position = 0f
                    if (chosen != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        currentOnAdd(chosen)
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .onSizeChanged { barWidth = it.width }
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cancelled -> Text("Let go to cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            sliding -> SliderTrack(position = position, color = content)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("+ Add set", style = MaterialTheme.typography.titleSmall, color = content)
                if (canSlide) {
                    Text("Hold and slide to change the weight", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
        // Inside the bar, so the bubble is placed relative to it.
        if (active && baseKg != null) {
            val weightKg = WeightChange.adjust(baseKg, percent, unit)
            val pad = with(LocalDensity.current) { EdgePadding.toPx() }
            ValueBubble(
                value = Weight.format(weightKg, unit),
                unit = unit.label,
                caption = WeightChange.label(baseKg, weightKg) ?: "Same weight",
                anchorX = barWidth / 2f + WeightChange.sliderPosition(percent) * (barWidth / 2f - pad),
                keepWithin = barWidth,
            )
        }
    }
}

/** The open slider: a dot for the middle, a band from the middle to the thumb, and the limits at the ends. */
@Composable
private fun SliderTrack(position: Float, color: Color) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = EdgePadding.toPx()
            val centre = size.width / 2
            val inset = 8.dp.toPx()
            // Snap the drawing to the knob's step, so the knob and the bubble agree.
            val x = centre + WeightChange.sliderPosition(WeightChange.sliderPercent(position)) * (centre - pad)
            drawRoundRect(
                color = color.copy(alpha = 0.16f),
                topLeft = Offset(minOf(centre, x), inset),
                size = Size(abs(x - centre), size.height - inset * 2),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawCircle(color = color.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = Offset(centre, size.height / 2))
            val knob = 6.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(x - knob / 2, inset - 1.dp.toPx()),
                size = Size(knob, size.height - (inset - 1.dp.toPx()) * 2),
                cornerRadius = CornerRadius(knob / 2),
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = KinzoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val limit = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            Text("−50%", style = limit, color = color.copy(alpha = 0.4f))
            Text("+50%", style = limit, color = color.copy(alpha = 0.4f))
        }
    }
}

/**
 * The bubble above the thumb while sliding a number. The number is what matters, so it's large;
 * [caption] sits small underneath. It's centred on [anchorX] in the parent, and kept inside the
 * parent's first [keepWithin] pixels when given. Call it from inside the box it should sit above.
 */
@Composable
internal fun ValueBubble(value: String, unit: String, caption: String, anchorX: Float, keepWithin: Int? = null) {
    val colors = MaterialTheme.colorScheme
    var bubbleSize by remember { mutableStateOf(IntOffset(0, 0)) }
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    val centred = anchorX - bubbleSize.x / 2f
    val x = if (keepWithin == null) centred else centred.coerceIn(0f, (keepWithin - bubbleSize.x).coerceAtLeast(0).toFloat())
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), -(bubbleSize.y + gap).roundToInt()),
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier = Modifier.onSizeChanged { bubbleSize = IntOffset(it.width, it.height) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .background(colors.inverseSurface, RoundedCornerShape(16.dp))
                    .padding(horizontal = KinzoTheme.spacing.lg, vertical = KinzoTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    buildAnnotatedString {
                        append(value)
                        withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.inverseOnSurface.copy(alpha = 0.7f))) {
                            append(" $unit")
                        }
                    },
                    style = NumericStyle.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
                    color = colors.inverseOnSurface,
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = colors.inverseOnSurface.copy(alpha = 0.6f),
                )
            }
            // The point under the bubble, aimed at the thumb.
            Canvas(Modifier.size(width = 14.dp, height = 7.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
                drawPath(path, colors.inverseSurface)
            }
        }
    }
}
