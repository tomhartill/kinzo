package com.tomhartill.kinzo.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.api.ApiException
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

/** Picking a username, with a live check that it's free as you type. */
class UsernameViewModel(private val accounts: AccountRepository) : ViewModel() {
    enum class Check { Current, Checking, Available, Taken, Invalid, Offline }

    data class State(
        val text: String,
        /** Their name, for the welcome. */
        val displayName: String = "",
        val check: Check = Check.Current,
        val saving: Boolean = false,
        val error: String? = null,
    ) {
        // Offline: let them try; the server has the final say.
        val canSave: Boolean get() = !saving && check in setOf(Check.Available, Check.Current, Check.Offline)
    }

    private val current: String = accounts.account.value?.username.orEmpty()
    private val _state = MutableStateFlow(State(text = current, displayName = accounts.account.value?.displayName.orEmpty()))
    val state: StateFlow<State> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    private var checkJob: Job? = null

    fun onTextChange(text: String) {
        val username = Usernames.normalize(text)
        checkJob?.cancel()
        val check = when {
            username == current -> Check.Current
            !Usernames.isValid(username) -> Check.Invalid
            else -> Check.Checking
        }
        _state.update { it.copy(text = text.trim().removePrefix("@").lowercase(), check = check, error = null) }
        if (check != Check.Checking) return
        checkJob = viewModelScope.launch {
            delay(CHECK_DELAY_MILLIS) // wait until they stop typing
            val result = try {
                val answer = accounts.checkUsername(username)
                when {
                    !answer.valid -> Check.Invalid
                    answer.available -> Check.Available
                    else -> Check.Taken
                }
            } catch (e: ApiException) {
                Check.Offline
            }
            _state.update { if (Usernames.normalize(it.text) == username) it.copy(check = result) else it }
        }
    }

    fun save() {
        val state = _state.value
        val username = Usernames.normalize(state.text)
        if (state.check == Check.Current) {
            _done.trySend(Unit)
            return
        }
        if (!state.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                accounts.setUsername(username)
                _done.send(Unit)
            } catch (e: ApiException) {
                _state.update {
                    if (e.code == "username_taken") it.copy(saving = false, check = Check.Taken)
                    else it.copy(saving = false, error = e.message)
                }
            }
        }
    }

    private companion object {
        const val CHECK_DELAY_MILLIS = 400L
    }
}

/** The username rules, the same as the API's: 3 to 30 of a-z, 0-9, dots and underscores. */
object Usernames {
    private val pattern = Regex("^[a-z0-9._]{3,30}$")

    fun normalize(text: String): String = text.trim().removePrefix("@").lowercase()

    fun isValid(username: String): Boolean = pattern.matches(username)
}
