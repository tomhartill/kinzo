package com.tomhartill.kinzo.ui.screens.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.Pose
import com.tomhartill.kinzo.domain.ProgressPhoto
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.viewmodel.koinViewModel

/**
 * Progress photos: pick a pose, take a photo, and see them all by day, newest first. They stay
 * private to this phone. Comparing two photos comes later.
 */
@Composable
fun ProgressPhotosScreen(
    onBack: () -> Unit,
    viewModel: ProgressPhotosViewModel = koinViewModel(),
) {
    val photos by viewModel.all.collectAsStateWithLifecycle()
    val pose by viewModel.pose.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val zone = remember { TimeZone.currentSystemDefault() }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val spacing = KinzoTheme.spacing

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(title = "Progress photos", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.padding(bottom = spacing.lg)) {
                    Text(
                        "Only on this phone, never in your gallery. The same pose, light and time of day each week makes changes easier to see.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    ChoiceRow(options = Pose.entries, selected = pose, optionLabel = { it.label }, onSelect = viewModel::setPose)
                    PrimaryButton(
                        text = when {
                            busy -> "Saving…"
                            viewModel.hasCamera -> "Take a ${pose.label.lowercase()} photo"
                            else -> "Choose a ${pose.label.lowercase()} photo"
                        },
                        onClick = viewModel::take,
                        enabled = !busy,
                    )
                    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                }
            }
            val loaded = photos
            if (loaded != null && loaded.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No photos yet. Take your first one today, then one a week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            loaded.orEmpty().groupBy { Dates.longDayLabel(it.takenAt, zone) }.forEach { (day, onDay) ->
                item(key = "day-${onDay.first().id}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(day, modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs))
                }
                items(onDay, key = { it.id }) { photo ->
                    Thumbnail(photo, load = viewModel::image, onClick = { openId = photo.id })
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    val open = photos?.firstOrNull { it.id == openId }
    if (open != null) {
        PhotoViewer(
            photo = open,
            title = "${open.pose.label} · ${Dates.longDayLabel(open.takenAt, zone)}",
            load = viewModel::image,
            onDelete = {
                viewModel.delete(open)
                openId = null
            },
            onClose = { openId = null },
        )
    }
}

@Composable
private fun Thumbnail(photo: ProgressPhoto, load: suspend (String) -> ByteArray?, onClick: () -> Unit) {
    val image = rememberImage(photo.thumbnailFileName, load)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (image != null) {
            Image(image, contentDescription = "${photo.pose.label} photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            photo.pose.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .padding(KinzoTheme.spacing.xs)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = KinzoTheme.spacing.xs, vertical = KinzoTheme.spacing.xxs),
        )
    }
}

/** One photo filling the screen, with delete. */
@Composable
private fun PhotoViewer(
    photo: ProgressPhoto,
    title: String,
    load: suspend (String) -> ByteArray?,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val image = rememberImage(photo.fileName, load)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(image, contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this photo?",
            message = "It's only on this phone, so it can't be got back.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Reads and decodes a photo file off the main thread. Null while loading or if it's missing. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberImage(fileName: String, load: suspend (String) -> ByteArray?): ImageBitmap? =
    produceState<ImageBitmap?>(null, fileName) {
        value = try {
            withContext(Dispatchers.Default) { load(fileName)?.decodeToImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }.value
