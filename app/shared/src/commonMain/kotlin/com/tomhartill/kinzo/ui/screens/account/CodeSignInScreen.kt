package com.tomhartill.kinzo.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.account.PhoneNumbers
import com.tomhartill.kinzo.data.api.CodeChannel
import com.tomhartill.kinzo.ui.components.KinzoTopBar
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.secondaryButtonColors
import com.tomhartill.kinzo.ui.components.kinzoTextFieldColors
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.screens.account.CodeSignInViewModel.Step
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Email, then a code by text or WhatsApp. With [addPhone], adds a phone to the signed-in account
 * instead, starting at the number. [onSignedIn] gets true on a first sign-in.
 */
@Composable
fun CodeSignInScreen(
    addPhone: Boolean,
    onClose: () -> Unit,
    onSignedIn: (newUser: Boolean) -> Unit,
    onPhoneAdded: () -> Unit,
    viewModel: CodeSignInViewModel = koinViewModel { parametersOf(addPhone) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    val phoneAdded by rememberUpdatedState(onPhoneAdded)
    LaunchedEffect(viewModel) { viewModel.signedIn.collect { signedIn(it.newUser) } }
    LaunchedEffect(viewModel) { viewModel.phoneAdded.collect { phoneAdded() } }
    val spacing = KinzoTheme.spacing
    val colors = MaterialTheme.colorScheme

    // Back steps through the screens before it leaves.
    val firstStep = if (addPhone) Step.Phone else Step.Email
    SystemBackHandler(enabled = state.step != firstStep) { viewModel.back() }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        KinzoTopBar(title = if (addPhone) "Add a phone" else "", onBack = { if (!viewModel.back()) onClose() })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when (state.step) {
                Step.Email -> EmailStep(state, viewModel)
                Step.Phone -> PhoneStep(state, viewModel, addPhone)
                Step.Code -> CodeStep(state, viewModel)
                Step.UseProvider -> UseProviderStep(state, onSignedIn)
            }
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        val action: Pair<String, () -> Unit>? = when (state.step) {
            Step.Email -> "Continue" to viewModel::submitEmail
            Step.Phone -> null // a button per channel, in the step
            Step.Code -> "Continue" to viewModel::verify
            Step.UseProvider -> null
        }
        if (action != null) {
            PrimaryButton(
                text = if (state.busy) "One moment…" else action.first,
                onClick = action.second,
                enabled = !state.busy,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = spacing.screen, vertical = spacing.lg),
            )
        }
    }
}

@Composable
private fun EmailStep(state: CodeSignInViewModel.State, viewModel: CodeSignInViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Heading("What's your email?", "We'll text or WhatsApp a code to your phone. No password.")
    TextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { viewModel.submitEmail() }),
        shape = MaterialTheme.shapes.small,
        colors = kinzoTextFieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentType = ContentType.EmailAddress },
    )
}

@Composable
private fun PhoneStep(state: CodeSignInViewModel.State, viewModel: CodeSignInViewModel, addPhone: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Heading(
        "What's your mobile number?",
        if (addPhone) "Then you can sign in with a code as well." else "We'll send a code to check it's yours. It isn't shown to anyone.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        CountryPicker(state, viewModel)
        TextField(
            value = state.number,
            onValueChange = viewModel::onNumberChange,
            label = { Text("Mobile number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.send(state.channels.first()) }),
            shape = MaterialTheme.shapes.small,
            colors = kinzoTextFieldColors(),
            modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentType = ContentType.PhoneNumber },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
        state.channels.forEachIndexed { index, channel ->
            val label = if (state.busy && index == 0) "Sending…" else "${if (channel == CodeChannel.Sms) "Text" else "WhatsApp"} me a code"
            if (index == 0) {
                PrimaryButton(text = label, onClick = { viewModel.send(channel) }, enabled = !state.busy)
            } else {
                Button(
                    onClick = { viewModel.send(channel) },
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    colors = secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = KinzoTheme.spacing.touchTarget),
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun CountryPicker(state: CodeSignInViewModel.State, viewModel: CodeSignInViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { open = true },
            shape = MaterialTheme.shapes.small,
            colors = secondaryButtonColors(),
            modifier = Modifier.heightIn(min = KinzoTheme.spacing.touchTarget),
        ) { Text("${state.country.iso} +${state.country.dialCode}", style = MaterialTheme.typography.bodyLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PhoneNumbers.countries.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.name}  +${country.dialCode}") },
                    onClick = {
                        viewModel.onCountryChange(country)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CodeStep(state: CodeSignInViewModel.State, viewModel: CodeSignInViewModel) {
    val challenge = state.challenge ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(challenge.challengeId) { focus.requestFocus() }
    val by = if (challenge.channel == CodeChannel.Sms) "by text" else "on WhatsApp"
    Heading("Enter your code", "We sent a 6-digit code to ${challenge.phoneHint} $by.")
    TextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        singleLine = true,
        placeholder = { Text("000000", style = CodeStyle, color = MaterialTheme.colorScheme.outline) },
        textStyle = CodeStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.verify() }),
        shape = MaterialTheme.shapes.small,
        colors = kinzoTextFieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentType = ContentType.SmsOtpCode },
    )
    Column {
        val other = state.channels.firstOrNull { it != challenge.channel }
        TextButton(
            onClick = { viewModel.send(challenge.channel) },
            enabled = !state.busy && state.resendIn == 0,
            modifier = Modifier.heightIn(min = KinzoTheme.spacing.touchTarget),
        ) {
            Text(if (state.resendIn > 0) "Resend code in ${state.resendIn}s" else "Resend code", style = MaterialTheme.typography.labelLarge)
        }
        if (other != null) {
            TextButton(
                onClick = { viewModel.send(other) },
                enabled = !state.busy && state.resendIn == 0,
                modifier = Modifier.heightIn(min = KinzoTheme.spacing.touchTarget),
            ) {
                Text(if (other == CodeChannel.Sms) "Send by text instead" else "Send on WhatsApp instead", style = MaterialTheme.typography.labelLarge)
            }
        }
        TextButton(onClick = { viewModel.back() }, modifier = Modifier.heightIn(min = KinzoTheme.spacing.touchTarget)) {
            Text("Use a different ${if (state.number.isBlank() && state.email.isNotBlank()) "email" else "number"}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun UseProviderStep(state: CodeSignInViewModel.State, onSignedIn: (Boolean) -> Unit) {
    val names = state.providers.map { it.replaceFirstChar(Char::uppercase) }
    Heading(
        "This email signs in with ${names.joinToString(" or ").ifEmpty { "Google or Facebook" }}",
        "Continue with that below. Once you're in, you can add your phone from Account to use codes too.",
    )
    SignInPanel(onSignedIn = onSignedIn)
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CodeStyle = TextStyle(fontSize = 28.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center)
