package com.tomhartill.kinzo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One row in a [ReorderSheet]. */
data class ReorderItem(val id: String, val title: String, val subtitle: String? = null)

/**
 * A sheet listing [items] one line each, so even long workouts fit on screen, with a handle
 * to drag each into place. [onReorder] gets the new order (as IDs) after every drop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSheet(
    title: String,
    items: List<ReorderItem>,
    onReorder: (orderedIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // The order as dragged so far. It only starts again from [items] if the items themselves
    // change (one removed elsewhere), not when a saved order comes back from the database.
    var order by remember(items.map { it.id }.toSet()) { mutableStateOf(items) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun move(index: Int, offset: Int) {
        val target = index + offset
        if (target !in order.indices) return
        order = order.toMutableList().apply { add(target, removeAt(index)) }
        onReorder(order.map { it.id })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = KinzoTheme.spacing.screen), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text("Drag the handles to change the order.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = KinzoTheme.spacing.md, vertical = KinzoTheme.spacing.md),
        ) {
            itemsIndexed(order, key = { _, item -> item.id }) { index, item ->
                ReorderableItem(reorderState, key = item.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isDragging) colors.surfaceContainerHigh else colors.surface,
                        shadowElevation = elevation,
                        modifier = Modifier.semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("Move up") { move(index, -1); true }.takeIf { index > 0 },
                                CustomAccessibilityAction("Move down") { move(index, 1); true }.takeIf { index < order.lastIndex },
                            )
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(start = KinzoTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.subtitle != null) {
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            // Screen readers move rows with the Move up and Move down actions instead.
                            Box(
                                modifier = Modifier
                                    .size(KinzoTheme.spacing.touchTarget)
                                    .draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            onReorder(order.map { it.id })
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(KinzoIcons.DragHandle, contentDescription = null, tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            text = "Done",
            onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            modifier = Modifier.padding(horizontal = KinzoTheme.spacing.screen, vertical = KinzoTheme.spacing.md),
        )
    }
}
