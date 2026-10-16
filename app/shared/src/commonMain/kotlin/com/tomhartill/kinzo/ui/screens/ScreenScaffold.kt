package com.tomhartill.kinzo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tomhartill.kinzo.domain.ScreenTips
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import com.tomhartill.kinzo.ui.tips.PageTipCard
import com.tomhartill.kinzo.ui.tips.ScreenTipsEffect
import com.tomhartill.kinzo.ui.tips.TipsButton

/**
 * Large left-aligned title over scrolling content: the layout every top-level screen shares.
 * With [tips], the screen's first-time tips show under the title, and a button beside it shows
 * them again.
 */
@Composable
internal fun TitledScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tips: ScreenTips? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (tips != null) ScreenTipsEffect(tips)
    val spacing = KinzoTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        ScreenTitle(title, subtitle, tips = tips)
        if (tips != null) PageTipCard(tips)
        content()
    }
}

/** The large title and optional subtitle at the top of a tab, with its tips button if it has tips. */
@Composable
internal fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, tips: ScreenTips? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (tips != null) TipsButton(tips)
    }
}

@Composable
internal fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
