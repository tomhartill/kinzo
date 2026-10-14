package com.tomhartill.kinzo.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.api.Account
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val settings: SettingsRepository, accounts: AccountRepository) : ViewModel() {
    val state: StateFlow<Settings?> =
        settings.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The signed-in account, or null. Signing in is optional. */
    val account: StateFlow<Account?> = accounts.account

    init {
        // Picks up a username changed on another device. Does nothing offline or signed out.
        viewModelScope.launch { accounts.refresh() }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun setRestSeconds(seconds: Int) {
        viewModelScope.launch { settings.setRestSeconds(seconds) }
    }
}
