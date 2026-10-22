package com.tomhartill.kinzo.ui.screens.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.components.Avatar
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.screens.account.SignInPanel
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Opened from a friend's invite link. */
@Composable
fun InviteScreen(
    code: String,
    onClose: () -> Unit,
    onSignedIn: (newUser: Boolean) -> Unit,
    onEmail: () -> Unit,
    onOpenFriends: () -> Unit,
    viewModel: InviteViewModel = koinViewModel { parametersOf(code) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KinzoTheme.spacing
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(title = "", onBack = onClose, backIcon = Icons.Filled.Close, backLabel = "Close")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                InviteViewModel.State.SignedOut -> {
                    Message("You've been invited to Kinzo", "Sign in to accept the invite and become friends.")
                    SignInPanel(onSignedIn = onSignedIn, onEmail = onEmail)
                }
                InviteViewModel.State.Loading -> Box(Modifier.fillMaxWidth().padding(top = spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is InviteViewModel.State.Ready -> {
                    Avatar(current.inviter.name, size = 88.dp)
                    Message(
                        "${current.inviter.name} invited you",
                        "Become friends to see each other's workouts and PRs. @${current.inviter.username}",
                    )
                    PrimaryButton(
                        text = if (current.accepting) "Adding…" else "Become friends",
                        onClick = viewModel::accept,
                        enabled = !current.accepting,
                    )
                    current.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, textAlign = TextAlign.Center) }
                    SecondaryButton(text = "Not now", onClick = onClose)
                }
                is InviteViewModel.State.Friends -> {
                    Avatar(current.person.name, size = 88.dp)
                    Message("You and ${current.person.name} are friends", "You'll see each other's workouts and PRs.")
                    PrimaryButton(text = "See friends", onClick = onOpenFriends)
                }
                InviteViewModel.State.Yours -> {
                    Message("This is your own invite", "Send it to a friend and they can add you in one tap.")
                    PrimaryButton(text = "See friends", onClick = onOpenFriends)
                }
                InviteViewModel.State.Invalid -> {
                    Message("This invite has expired", "Ask your friend for a new link, or search for their username.")
                    PrimaryButton(text = "Find friends", onClick = onOpenFriends)
                }
                is InviteViewModel.State.Failed -> {
                    Message("Couldn't open the invite", current.message)
                    PrimaryButton(text = "Try again", onClick = viewModel::load)
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
