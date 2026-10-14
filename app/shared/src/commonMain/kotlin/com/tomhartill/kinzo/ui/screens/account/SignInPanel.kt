package com.tomhartill.kinzo.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.data.SignInProvider
import com.tomhartill.kinzo.ui.components.BrandIcons
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * The two sign-in buttons, with any error under them. Used on the sign-in screen and anywhere
 * a feature needs an account (Friends, an invite), so signing in is always one tap away.
 * [onSignedIn] gets true on a first sign-in.
 */
@Composable
fun SignInPanel(
    onSignedIn: (newUser: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(viewModel) {
        viewModel.signedIn.collect { signedIn(it.newUser) }
    }
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
        // Google's sign-in guidelines: white with a grey outline, or near-black in dark mode.
        ProviderButton(
            label = "Continue with Google",
            icon = BrandIcons.Google,
            iconTint = Color.Unspecified,
            container = if (dark) Color(0xFF131314) else Color.White,
            content = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            border = if (dark) Color(0xFF8E918F) else Color(0xFF747775),
            busy = state.busy == SignInProvider.Google,
            enabled = state.busy == null,
            onClick = { viewModel.signIn(SignInProvider.Google) },
        )
        ProviderButton(
            label = "Continue with Facebook",
            icon = BrandIcons.FacebookF,
            iconTint = Color.White,
            container = BrandIcons.FacebookBlue,
            content = Color.White,
            border = null,
            busy = state.busy == SignInProvider.Facebook,
            enabled = state.busy == null,
            onClick = { viewModel.signIn(SignInProvider.Facebook) },
        )
        val error = state.error
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            "Kinzo only uses your name, email and photo, and never posts anything for you.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProviderButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    container: Color,
    content: Color,
    border: Color?,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = border?.let { BorderStroke(1.dp, it) },
        // The other button fades while one sign-in is under way.
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (enabled || busy) 1f else 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = KinzoTheme.spacing.lg, vertical = KinzoTheme.spacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(KinzoTheme.spacing.md))
            Text(if (busy) "Signing in…" else label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
