package com.tomhartill.kinzo.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.api.ApiException
import com.tomhartill.kinzo.data.api.KinzoApi
import com.tomhartill.kinzo.data.api.Person
import com.tomhartill.kinzo.data.api.Following
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A friend's invite link, opened in the app. Accepting it makes you follow each other straight away. */
class InviteViewModel(
    private val code: String,
    private val api: KinzoApi,
    accounts: AccountRepository,
) : ViewModel() {
    sealed interface State {
        data object SignedOut : State

        data object Loading : State

        /** [accepting] while the tap is on its way; [error] if it didn't work. */
        data class Ready(val inviter: Person, val accepting: Boolean = false, val error: String? = null) : State

        /** Your own link, opened on your own phone. */
        data object Yours : State

        /** You and they follow each other. */
        data class FollowingEachOther(val person: Person) : State

        /** The link was reset or never existed. */
        data object Invalid : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accounts.account.distinctUntilChangedBy { it?.id }.collect { account ->
                if (account == null) _state.value = State.SignedOut else load()
            }
        }
    }

    fun load() {
        _state.value = State.Loading
        viewModelScope.launch {
            _state.value = try {
                val preview = api.invite(code)
                when {
                    preview.isYou -> State.Yours
                    preview.inviter.following == Following.Yes && preview.inviter.followsYou -> State.FollowingEachOther(preview.inviter)
                    else -> State.Ready(preview.inviter)
                }
            } catch (e: ApiException) {
                if (e.status == 404) State.Invalid else State.Failed(e.message)
            }
        }
    }

    fun accept() {
        val ready = _state.value as? State.Ready ?: return
        if (ready.accepting) return
        _state.value = ready.copy(accepting = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = State.FollowingEachOther(api.acceptInvite(code))
            } catch (e: ApiException) {
                _state.update { if (e.status == 404) State.Invalid else ready.copy(accepting = false, error = e.message) }
            }
        }
    }
}
