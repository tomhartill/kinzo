package com.tomhartill.kinzo.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.kinzoTextFieldColors
import com.tomhartill.kinzo.ui.screens.account.UsernameViewModel.Check
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Choose a username. [welcome] is the version shown straight after a first sign-in, with the
 * one Kinzo picked from their name already filled in.
 */
@Composable
fun UsernameScreen(
    welcome: Boolean,
    onDone: () -> Unit,
    viewModel: UsernameViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.done.collect { done() } }
    val spacing = KinzoTheme.spacing
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(
            title = if (welcome) "" else "Username",
            onBack = onDone,
            backIcon = Icons.Filled.Close,
            backLabel = if (welcome) "Skip" else "Close",
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (welcome) {
                Text(
                    if (state.displayName.isBlank()) "You're in" else "You're in, ${state.displayName.substringBefore(' ')}",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            if (state.askName) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Your name") },
                    supportingText = { Text("What friends see. Optional.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.small,
                    colors = kinzoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Your username is how friends find you. You can change it any time from Profile.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                prefix = { Text("@") },
                singleLine = true,
                isError = state.check == Check.Taken || state.check == Check.Invalid,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                shape = MaterialTheme.shapes.small,
                colors = kinzoTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            val (message, isError) = when (state.check) {
                Check.Current -> (if (welcome) "Kinzo picked this for you." else "This is your username.") to false
                Check.Checking -> "Checking…" to false
                Check.Available -> "That one's free." to false
                Check.Taken -> "Someone already has that one." to true
                Check.Invalid -> "Use 3 to 30 letters, numbers, dots or underscores." to true
                Check.Offline -> "Can't check right now. You can still try to save it." to false
            }
            Text(
                state.error ?: message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError || state.error != null) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        PrimaryButton(
            text = when {
                state.saving -> "Saving…"
                welcome && state.check == Check.Current && state.name.isBlank() -> "Looks good"
                else -> "Save"
            },
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
        )
    }
}
