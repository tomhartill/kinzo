package com.tomhartill.kinzo.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.components.Avatar
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** The signed-in account: username, friends, signing out and deleting the account. */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onEditUsername: () -> Unit,
    onOpenFriends: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val back by rememberUpdatedState(onBack)
    val spacing = KinzoTheme.spacing
    val colors = MaterialTheme.colorScheme

    // Signed out (here, or because the session ended): nothing left to show.
    val current = account
    LaunchedEffect(current == null) { if (current == null) back() }
    if (current == null) return

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KinzoTopBar(title = "Account", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Avatar(current.name, size = 64.dp)
                Column {
                    Text(current.name, style = MaterialTheme.typography.titleLarge)
                    Text("@${current.username}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    current.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                }
            }

            SectionHeader("Profile")
            KinzoCard(onClick = onEditUsername) {
                Text("Username", style = MaterialTheme.typography.titleMedium)
                Text("@${current.username} · how friends find you", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            KinzoCard(onClick = onOpenFriends) {
                Text("Friends", style = MaterialTheme.typography.titleMedium)
                Text("Add friends and answer requests", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }

            SectionHeader("Signed in")
            SecondaryButton(text = "Sign out", onClick = { confirmSignOut = true }, enabled = !busy)
            TextButton(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.heightIn(min = spacing.touchTarget),
            ) {
                Text("Delete account", style = MaterialTheme.typography.labelLarge, color = colors.error)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Your workouts stay on this phone. Sign in again any time to see your friends.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete your account?",
            message = "This deletes your Kinzo account and your friends list for good. Workouts on this phone stay here. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
