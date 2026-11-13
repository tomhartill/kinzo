package com.tomhartill.kinzo.ui.tips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.ScreenTips
import com.tomhartill.kinzo.domain.Tip
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** How long a screen settles before its tips show, so they don't land mid-animation. */
private const val SETTLE_MILLIS = 500L

/** How long a spotlight waits for its control to appear before letting the next tip go. */
private const val ANCHOR_WAIT_MILLIS = 1_500L

/**
 * Asks for a screen's tips once per visit. While [enabled] is false (for example once a set is
 * logged, so nothing interrupts a workout) they don't show, and any still waiting are dropped
 * for this visit.
 */
@Composable
fun ScreenTipsEffect(tips: ScreenTips, enabled: Boolean = true) {
    val controller = koinInject<TipController>()
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (enabled && !asked) {
            delay(SETTLE_MILLIS)
            controller.visit(tips)
            asked = true
        } else if (!enabled && asked) {
            controller.leave(tips.screen)
        }
    }
    DisposableEffect(tips.screen) {
        onDispose { controller.leave(tips.screen) }
    }
}

/** The screen's page tip as a card, while it's the tip showing. Renders nothing otherwise. */
@Composable
fun PageTipCard(tips: ScreenTips, modifier: Modifier = Modifier) {
    val intro = tips.intro ?: return
    val controller = koinInject<TipController>()
    val state by controller.state.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = state.screen == tips.screen && state.current == intro, modifier = modifier) {
        val colors = KinzoTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, MaterialTheme.shapes.large)
                .padding(start = KinzoTheme.spacing.lg, end = KinzoTheme.spacing.sm, top = KinzoTheme.spacing.lg)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs),
        ) {
            Text(intro.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                intro.body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.padding(end = KinzoTheme.spacing.sm),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { controller.dismiss(intro) }) {
                    Text(if (state.hasNext) "Next" else "Got it", style = MaterialTheme.typography.labelLarge, color = colors.text)
                }
            }
        }
    }
}

/** Shows the screen's tips again, seen or not. Goes in a top bar or beside a title. */
@Composable
fun TipsButton(tips: ScreenTips, modifier: Modifier = Modifier) {
    val controller = koinInject<TipController>()
    IconButton(onClick = { controller.replay(tips) }, modifier = modifier) {
        Icon(Icons.Outlined.Info, contentDescription = "Show tips for this screen", tint = KinzoTheme.colors.textMuted)
    }
}

/** Marks the control a spotlight tip points at. */
fun Modifier.tipAnchor(tip: Tip): Modifier = composed {
    val controller = koinInject<TipController>()
    DisposableEffect(tip.id) {
        onDispose { controller.anchors.remove(tip.id) }
    }
    onGloballyPositioned { controller.anchors[tip.id] = it.boundsInRoot() }
}

/**
 * Draws the spotlight tip showing now over the whole app: the screen dims except for the
 * control, with a bubble beside it. Goes once, at the root, above everything else, so screens
 * never draw overlays of their own. Tapping anywhere moves on.
 */
@Composable
fun TipOverlay() {
    val controller = koinInject<TipController>()
    val state by controller.state.collectAsStateWithLifecycle()
    val tip = state.current?.takeIf { it.kind == Tip.Kind.Spotlight }
    val anchor = tip?.let { controller.anchors[it.id] }
    // A control that isn't on screen lets the rest go; its tip shows on a later visit.
    LaunchedEffect(tip, anchor == null) {
        if (tip != null && anchor == null) {
            delay(ANCHOR_WAIT_MILLIS)
            controller.skip(tip)
        }
    }
    if (tip == null || anchor == null) return
    Spotlight(tip, anchor, hasNext = state.hasNext, onDone = { controller.dismiss(tip) })
}

@Composable
private fun Spotlight(tip: Tip, anchor: Rect, hasNext: Boolean, onDone: () -> Unit) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    Layout(
        content = { Bubble(tip, hasNext, onDone) },
        modifier = Modifier
            .fillMaxSize()
            // Takes every tap, so the screen underneath can't be used by accident while a tip shows.
            .pointerInput(tip.id) { detectTapGestures { onDone() } }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                val hole = anchor.inflate(6.dp.toPx())
                drawRect(scrim)
                drawRoundRect(
                    color = scrim,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    blendMode = BlendMode.Clear,
                )
            },
    ) { measurables, constraints ->
        val margin = 20.dp.roundToPx()
        val gap = 16.dp.roundToPx()
        val bubble = measurables.first().measure(
            Constraints(maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0), maxHeight = constraints.maxHeight),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Below the control when it's in the top half of the screen, above it otherwise.
            val y = if (anchor.center.y < constraints.maxHeight / 2f) {
                anchor.bottom.toInt() + gap
            } else {
                anchor.top.toInt() - gap - bubble.height
            }
            val maxY = (constraints.maxHeight - bubble.height - margin).coerceAtLeast(margin)
            bubble.place(margin, y.coerceIn(margin, maxY))
        }
    }
}

@Composable
private fun Bubble(tip: Tip, hasNext: Boolean, onDone: () -> Unit) {
    val colors = KinzoTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.large)
            .padding(KinzoTheme.spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
            Text(tip.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDone,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) {
                    Text(if (hasNext) "Next" else "Got it", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
