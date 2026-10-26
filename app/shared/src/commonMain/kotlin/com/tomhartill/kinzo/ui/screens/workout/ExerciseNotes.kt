package com.tomhartill.kinzo.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.delay

/**
 * The note for one exercise in a workout, written straight onto the page under its name: a
 * seat height, a form cue, how it felt. While it's empty, the last note written for the exercise
 * shows as a hint. It saves a moment after typing stops, and when the field is left.
 */
@Composable
fun ExerciseNotesField(
    exerciseName: String,
    notes: String?,
    previousNotes: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(notes.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    val saved by rememberUpdatedState(notes.orEmpty())
    val currentOnSave by rememberUpdatedState(onSave)
    val latestText by rememberUpdatedState(text)

    fun saveIfChanged() {
        if (latestText.trim() != saved) currentOnSave(latestText)
    }

    // A change saved elsewhere shows here, unless it's being typed in.
    LaunchedEffect(notes) { if (!focused) text = notes.orEmpty() }
    LaunchedEffect(text) {
        delay(SAVE_DELAY_MS)
        saveIfChanged()
    }
    // Leaving the screen straight after typing still keeps the note.
    DisposableEffect(Unit) { onDispose { saveIfChanged() } }

    val placeholder = previousNotes?.let { "Last note: $it" } ?: "Add a note"
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) saveIfChanged()
                focused = it.isFocused
            }
            .semantics { contentDescription = "Note for $exerciseName" },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = KinzoTheme.spacing.touchTarget)
                    .background(if (focused) colors.surfaceContainerHigh else Color.Transparent, MaterialTheme.shapes.small)
                    .padding(horizontal = KinzoTheme.spacing.sm, vertical = KinzoTheme.spacing.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

private const val SAVE_DELAY_MS = 600L
