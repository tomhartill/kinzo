package com.tomhartill.kinzo.ui.screens.friends

import com.tomhartill.kinzo.ui.tips.TipsButton
import com.tomhartill.kinzo.ui.tips.ScreenTipsEffect
import com.tomhartill.kinzo.ui.tips.PageTipCard
import com.tomhartill.kinzo.ui.tips.KinzoTips
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.data.api.Person
import com.tomhartill.kinzo.data.api.Following
import com.tomhartill.kinzo.ui.components.Avatar
import com.tomhartill.kinzo.ui.components.ConfirmDialog
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.secondaryButtonColors
import com.tomhartill.kinzo.ui.components.SearchField
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.account.SignInPanel
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Friends: who you follow and who follows you, follow requests, search and an invite link.
 * Following is one-way, as on Instagram. Signed out, it offers sign-in.
 */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onSignedIn: (newUser: Boolean) -> Unit,
    onEmail: () -> Unit,
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KinzoTheme.spacing
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }

    ScreenTipsEffect(KinzoTips.Friends, enabled = state.signedIn)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        KinzoTopBar(
            title = "Friends",
            onBack = onBack,
            actions = { if (state.signedIn) TipsButton(KinzoTips.Friends) },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.sm,
                bottom = spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (!state.signedIn) {
                signedOut(onSignedIn, onEmail)
                return@LazyColumn
            }
            item(key = "tip") { PageTipCard(KinzoTips.Friends) }
            item(key = "search") {
                SearchField(query = state.query, onQueryChange = viewModel::onQueryChange, placeholder = "Find people by username")
            }
            state.message?.let { message ->
                item(key = "message") {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (state.isSearching) {
                searchResults(state, viewModel)
            } else {
                friendLists(state, viewModel, onConfirm = { confirm = it })
            }
        }
    }

    confirm?.let { pending ->
        val name = pending.person.name
        ConfirmDialog(
            title = if (pending.block) "Block $name?" else "Remove $name as a follower?",
            message = if (pending.block) {
                "$name won't be able to find or follow you, and you'll stop following each other. They won't be told."
            } else {
                "They'll stop seeing your training. They won't be told, and can follow you again unless your account is private."
            },
            confirmLabel = if (pending.block) "Block" else "Remove",
            destructive = true,
            onConfirm = {
                confirm = null
                if (pending.block) viewModel.block(pending.person) else viewModel.removeFollower(pending.person)
            },
            onDismiss = { confirm = null },
        )
    }
}

/** A step that asks first: blocking someone, or removing a follower. */
private data class PendingConfirm(val person: Person, val block: Boolean)

private fun LazyListScope.signedOut(onSignedIn: (Boolean) -> Unit, onEmail: () -> Unit) {
    item(key = "signed-out") {
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.lg), modifier = Modifier.padding(top = KinzoTheme.spacing.lg)) {
            Text("Train with friends", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in to follow friends, see each other's workouts and PRs, and keep each other going.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignInPanel(onSignedIn = onSignedIn, onEmail = onEmail)
        }
    }
}

private fun LazyListScope.searchResults(state: FriendsViewModel.State, viewModel: FriendsViewModel) {
    val results = state.results
    when {
        state.searchError != null -> item(key = "search-error") { Note(state.searchError) }
        results == null -> item(key = "searching") { Loading() }
        results.isEmpty() && !state.searching -> item(key = "no-results") {
            EmptyState(title = "No one found", body = "Check the spelling, or send them your invite link instead.")
        }
        else -> items(results, key = { "result-${it.id}" }) { person ->
            PersonRow(person) { FollowButton(person, person.id in state.busy, viewModel) }
        }
    }
}

private fun LazyListScope.friendLists(
    state: FriendsViewModel.State,
    viewModel: FriendsViewModel,
    onConfirm: (PendingConfirm) -> Unit,
) {
    item(key = "invite") {
        KinzoCard {
            Text("Invite friends", style = MaterialTheme.typography.titleMedium)
            Text(
                "Send your link to someone. When they open it in Kinzo, you follow each other straight away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(text = if (state.sharing) "Getting your link…" else "Share invite link", onClick = viewModel::shareInvite, enabled = !state.sharing)
        }
    }
    val lists = state.lists
    if (lists == null) {
        item(key = "lists-status") {
            if (state.loadError != null) {
                Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                    Note(state.loadError)
                    SecondaryButton(text = "Try again", onClick = viewModel::load)
                }
            } else {
                Loading()
            }
        }
        return
    }
    if (lists.requests.isNotEmpty()) {
        item(key = "requests-header") { SectionHeader("Requests to follow you", Modifier.padding(top = KinzoTheme.spacing.sm)) }
        items(lists.requests, key = { "request-${it.id}" }) { person ->
            PersonRow(person) {
                val busy = person.id in state.busy
                SmallButton("Accept", busy, filled = true) { viewModel.accept(person) }
                IconButton(onClick = { viewModel.removeFollower(person) }, enabled = !busy) {
                    Icon(Icons.Filled.Close, contentDescription = "Decline ${person.name}")
                }
            }
        }
    }
    item(key = "following-header") {
        SectionHeader(
            if (lists.following.isEmpty()) "Following" else "Following · ${lists.following.size}",
            Modifier.padding(top = KinzoTheme.spacing.sm),
        )
    }
    if (lists.following.isEmpty()) {
        item(key = "no-following") {
            EmptyState(title = "Not following anyone yet", body = "Search for someone's username above, or share your invite link.")
        }
    }
    items(lists.following, key = { "following-${it.id}" }) { person ->
        PersonRow(person) {
            val busy = person.id in state.busy
            if (person.following == Following.Requested) {
                SmallButton("Requested", busy) { viewModel.unfollow(person) }
            } else {
                PersonMenu(
                    person = person,
                    enabled = !busy,
                    items = listOf(
                        MenuAction("Unfollow") { viewModel.unfollow(person) },
                        MenuAction("Block", destructive = true) { onConfirm(PendingConfirm(person, block = true)) },
                    ),
                )
            }
        }
    }
    item(key = "followers-header") {
        SectionHeader(
            if (lists.followers.isEmpty()) "Followers" else "Followers · ${lists.followers.size}",
            Modifier.padding(top = KinzoTheme.spacing.sm),
        )
    }
    if (lists.followers.isEmpty()) {
        item(key = "no-followers") {
            EmptyState(title = "No followers yet", body = "When someone follows you, they'll show here.")
        }
    }
    items(lists.followers, key = { "follower-${it.id}" }) { person ->
        PersonRow(person) {
            val busy = person.id in state.busy
            if (person.following != Following.Yes) FollowButton(person, busy, viewModel)
            PersonMenu(
                person = person,
                enabled = !busy,
                items = listOf(
                    MenuAction("Remove follower") { onConfirm(PendingConfirm(person, block = false)) },
                    MenuAction("Block", destructive = true) { onConfirm(PendingConfirm(person, block = true)) },
                ),
            )
        }
    }
}

/** Follow, Follow back, Requested or Following, each of which undoes or does the follow. */
@Composable
private fun FollowButton(person: Person, busy: Boolean, viewModel: FriendsViewModel) {
    when (person.following) {
        Following.None -> SmallButton(if (person.followsYou) "Follow back" else "Follow", busy, filled = true) { viewModel.follow(person) }
        Following.Requested -> SmallButton("Requested", busy) { viewModel.unfollow(person) }
        Following.Yes -> SmallButton("Following", busy) { viewModel.unfollow(person) }
    }
}

@Composable
private fun PersonRow(person: Person, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
    ) {
        Avatar(person.name)
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (person.displayName.isNotBlank()) {
                Text(
                    "@${person.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

private class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** A row's overflow menu. */
@Composable
private fun PersonMenu(person: Person, enabled: Boolean, items: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${person.name}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(item.label, color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** A compact button for a row: filled for the action we expect, outlined otherwise. */
@Composable
private fun SmallButton(text: String, busy: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = KinzoTheme.spacing.lg)
    val content: @Composable RowScope.() -> Unit = {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    val modifier = Modifier.heightIn(min = 40.dp)
    if (filled) {
        Button(
            onClick = onClick,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = modifier,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            contentPadding = padding,
            colors = secondaryButtonColors(),
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(KinzoTheme.spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
