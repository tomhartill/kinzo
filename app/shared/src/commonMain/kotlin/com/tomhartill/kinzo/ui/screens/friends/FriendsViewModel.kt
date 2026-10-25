package com.tomhartill.kinzo.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.account.Sharer
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.api.ApiException
import com.tomhartill.kinzo.data.api.FollowLists
import com.tomhartill.kinzo.data.api.Following
import com.tomhartill.kinzo.data.api.KinzoApi
import com.tomhartill.kinzo.data.api.Person
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Followers, following, follow requests, search and invite links. Needs an account and a connection. */
class FriendsViewModel(
    private val api: KinzoApi,
    accounts: AccountRepository,
    private val sharer: Sharer,
) : ViewModel() {
    data class State(
        val signedIn: Boolean = false,
        val loading: Boolean = false,
        val lists: FollowLists? = null,
        /** Why the lists couldn't load. */
        val loadError: String? = null,
        val query: String = "",
        val searching: Boolean = false,
        val results: List<Person>? = null,
        val searchError: String? = null,
        /** People with an action under way, so their buttons wait. */
        val busy: Set<String> = emptySet(),
        /** A one-line note about the last action, e.g. a failure. */
        val message: String? = null,
        val sharing: Boolean = false,
    ) {
        val isSearching: Boolean get() = query.trim().removePrefix("@").length >= MIN_QUERY
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            accounts.account.distinctUntilChangedBy { it?.id }.collect { account ->
                _state.value = State(signedIn = account != null)
                if (account != null) load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val lists = api.follows()
                _state.update { it.copy(loading = false, lists = lists) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, searchError = null) }
        searchJob?.cancel()
        val q = query.trim().removePrefix("@")
        if (q.length < MIN_QUERY) {
            _state.update { it.copy(searching = false, results = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            delay(SEARCH_DELAY_MILLIS)
            try {
                val results = api.searchPeople(q)
                _state.update { it.copy(searching = false, results = results) }
            } catch (e: ApiException) {
                _state.update { it.copy(searching = false, searchError = e.message) }
            }
        }
    }

    /** Follows them, or asks to if their account is private. */
    fun follow(person: Person) = act(person) {
        val updated = api.follow(person.id)
        setResult(person.id) { updated }
    }

    /** Unfollows them, or withdraws a request to follow. */
    fun unfollow(person: Person) = act(person) {
        api.unfollow(person.id)
        setResult(person.id) { it.copy(following = Following.None) }
    }

    /** Approves their request to follow you. */
    fun accept(person: Person) = act(person) {
        api.acceptFollower(person.id)
        setResult(person.id) { it.copy(followsYou = true, requestedYou = false) }
    }

    /** Stops them following you, or declines their request. */
    fun removeFollower(person: Person) = act(person) {
        api.removeFollower(person.id)
        setResult(person.id) { it.copy(followsYou = false, requestedYou = false) }
    }

    fun block(person: Person) = act(person) {
        api.block(person.id)
        _state.update { s -> s.copy(results = s.results?.filterNot { it.id == person.id }) }
    }

    fun shareInvite() {
        if (_state.value.sharing) return
        _state.update { it.copy(sharing = true, message = null) }
        viewModelScope.launch {
            try {
                val invite = api.myInvite()
                sharer.share("Train with me on Kinzo: ${invite.url}")
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(sharing = false) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun act(person: Person, action: suspend () -> Unit) {
        if (person.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + person.id, message = null) }
        viewModelScope.launch {
            try {
                action()
                // The lists come from the server, so they're always the truth after a change.
                val lists = api.follows()
                _state.update { it.copy(lists = lists) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busy = it.busy - person.id) }
            }
        }
    }

    private fun setResult(id: String, change: (Person) -> Person) {
        _state.update { s -> s.copy(results = s.results?.map { if (it.id == id) change(it) else it }) }
    }

    private companion object {
        const val MIN_QUERY = 2
        const val SEARCH_DELAY_MILLIS = 300L
    }
}
