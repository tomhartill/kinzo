package com.tomhartill.kinzo.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.account.Country
import com.tomhartill.kinzo.account.PhoneNumbers
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.SignInResult
import com.tomhartill.kinzo.data.api.ApiException
import com.tomhartill.kinzo.data.api.CodeChallenge
import com.tomhartill.kinzo.data.api.CodeChannel
import com.tomhartill.kinzo.data.api.PhoneStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Signing in with an email and a code sent by text or WhatsApp, or ([addPhone]) adding a phone
 * to the signed-in account. See docs/specs/email-and-phone-sign-in.md.
 */
class CodeSignInViewModel(
    private val addPhone: Boolean,
    private val accounts: AccountRepository,
) : ViewModel() {
    enum class Step {
        Email,
        Phone,
        Code,

        /** The email belongs to a Google or Facebook account: sign in that way instead. */
        UseProvider,
    }

    data class State(
        val step: Step,
        val email: String = "",
        val country: Country = PhoneNumbers.default,
        val number: String = "",
        val channels: List<CodeChannel> = listOf(CodeChannel.Sms),
        val challenge: CodeChallenge? = null,
        val code: String = "",
        /** Seconds until Resend works again. */
        val resendIn: Int = 0,
        val providers: List<String> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        val emailLooksValid: Boolean get() = EMAIL.matches(email.trim())
        val phoneLooksValid: Boolean get() = PhoneNumbers.looksComplete(PhoneNumbers.international(number, country))
    }

    private val _state = MutableStateFlow(State(step = if (addPhone) Step.Phone else Step.Email))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _signedIn = Channel<SignInResult.SignedIn>(Channel.BUFFERED)

    /** Fires once when the code signs someone in. */
    val signedIn: Flow<SignInResult.SignedIn> = _signedIn.receiveAsFlow()

    private val _phoneAdded = Channel<Unit>(Channel.CONFLATED)

    /** Fires once when a phone has been added to the account. */
    val phoneAdded: Flow<Unit> = _phoneAdded.receiveAsFlow()

    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            val channels = accounts.codeChannels()
            if (channels.isNotEmpty()) _state.update { it.copy(channels = channels) }
        }
    }

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, error = null) }

    fun onCountryChange(country: Country) = _state.update { it.copy(country = country, error = null) }

    fun onNumberChange(number: String) = _state.update { it.copy(number = number, error = null) }

    /** Email entered: a returning email gets its code now; a new one asks for a number. */
    fun submitEmail() {
        val state = _state.value
        if (state.busy) return
        if (!state.emailLooksValid) {
            _state.update { it.copy(error = "That doesn't look like an email address.") }
            return
        }
        send(state.channels.first(), phone = null)
    }

    /** Sends (or resends) a code over [channel]. */
    fun send(channel: CodeChannel, phone: String? = currentPhone()) {
        val state = _state.value
        if (state.busy) return
        if ((addPhone || state.step == Step.Phone) && !state.phoneLooksValid) {
            _state.update { it.copy(error = "Check the number, including the right country.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (addPhone) {
                    showCode(accounts.startAddPhone(phone ?: return@launch, channel))
                } else {
                    val start = accounts.startCodeSignIn(state.email, phone, channel)
                    when (start.next) {
                        PhoneStart.Next.Code -> showCode(start.challenge ?: throw unexpected())
                        PhoneStart.Next.Phone -> _state.update { it.copy(step = Step.Phone, busy = false) }
                        PhoneStart.Next.Provider ->
                            _state.update { it.copy(step = Step.UseProvider, providers = start.providers, busy = false) }
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
                e.retryIn?.let(::startCountdown)
            }
        }
    }

    fun onCodeChange(text: String) {
        val code = text.filter { it.isDigit() }.take(CODE_LENGTH)
        _state.update { it.copy(code = code, error = null) }
        // Six digits in (typed or autofilled): try it straight away.
        if (code.length == CODE_LENGTH) verify()
    }

    fun verify() {
        val state = _state.value
        val challenge = state.challenge ?: return
        if (state.busy || state.code.length != CODE_LENGTH) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (addPhone) {
                    accounts.finishAddPhone(challenge.challengeId, state.code)
                    _phoneAdded.send(Unit)
                } else {
                    _signedIn.send(accounts.finishCodeSignIn(challenge.challengeId, state.code))
                }
                _state.update { it.copy(busy = false) }
            } catch (e: ApiException) {
                val message = when {
                    e.code == "wrong_code" && e.attemptsLeft != null ->
                        "That code isn't right. ${e.attemptsLeft} ${if (e.attemptsLeft == 1) "try" else "tries"} left."
                    else -> e.message
                }
                _state.update { it.copy(busy = false, code = "", error = message) }
            }
        }
    }

    /** Back a step: from the code to whatever came before it, from the number to the email. */
    fun back(): Boolean {
        val state = _state.value
        val previous = when (state.step) {
            Step.Code -> if (addPhone || state.number.isNotBlank()) Step.Phone else Step.Email
            Step.Phone -> if (addPhone) null else Step.Email
            Step.UseProvider -> Step.Email
            Step.Email -> null
        } ?: return false
        countdown?.cancel()
        _state.update { it.copy(step = previous, code = "", challenge = null, resendIn = 0, error = null, busy = false) }
        return true
    }

    private fun currentPhone(): String? = with(_state.value) {
        if (addPhone || number.isNotBlank()) PhoneNumbers.international(number, country) else null
    }

    private fun showCode(challenge: CodeChallenge) {
        _state.update { it.copy(step = Step.Code, challenge = challenge, code = "", busy = false, error = null) }
        startCountdown(challenge.resendIn)
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        _state.update { it.copy(resendIn = seconds) }
        countdown = viewModelScope.launch {
            while (_state.value.resendIn > 0) {
                delay(1_000)
                _state.update { it.copy(resendIn = it.resendIn - 1) }
            }
        }
    }

    private fun unexpected() = ApiException("bad_response", "Kinzo sent back something unexpected. Try again.")

    companion object {
        const val CODE_LENGTH = 6
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
