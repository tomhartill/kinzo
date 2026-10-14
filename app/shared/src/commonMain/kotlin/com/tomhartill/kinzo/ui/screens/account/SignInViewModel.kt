package com.tomhartill.kinzo.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.SignInProvider
import com.tomhartill.kinzo.data.SignInResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel(private val accounts: AccountRepository) : ViewModel() {
    data class State(
        /** The provider whose sign-in is under way; both buttons wait while one runs. */
        val busy: SignInProvider? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _signedIn = Channel<SignInResult.SignedIn>(Channel.BUFFERED)

    /** Fires once when sign-in succeeds. */
    val signedIn: Flow<SignInResult.SignedIn> = _signedIn.receiveAsFlow()

    fun signIn(provider: SignInProvider) {
        if (_state.value.busy != null) return
        _state.value = State(busy = provider)
        viewModelScope.launch {
            val result = accounts.signIn(provider)
            _state.update { State(error = (result as? SignInResult.Failed)?.message) }
            if (result is SignInResult.SignedIn) _signedIn.send(result)
        }
    }
}
