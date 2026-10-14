package com.tomhartill.kinzo.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.api.Account
import com.tomhartill.kinzo.data.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel(private val accounts: AccountRepository) : ViewModel() {
    val account: StateFlow<Account?> = accounts.account

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { accounts.refresh() }
    }

    fun signOut() = run { accounts.signOut() }

    fun deleteAccount() = run { accounts.deleteAccount() }

    private fun run(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: ApiException) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }
}
