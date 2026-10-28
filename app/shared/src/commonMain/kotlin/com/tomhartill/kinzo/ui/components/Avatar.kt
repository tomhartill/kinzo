package com.tomhartill.kinzo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A person's initials in a grey circle. Profile photos come later. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.38f).sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** "Tom Hartill" is TH, "@tom.h" is T, "" is ?. */
internal fun initials(name: String): String {
    val words = name.trim().removePrefix("@").split(' ', '.', '_').filter { it.isNotEmpty() }
    val letters = when {
        words.isEmpty() -> "?"
        name.trim().startsWith("@") -> words.first().take(1)
        else -> words.take(2).joinToString("") { it.take(1) }
    }
    return letters.uppercase()
}
