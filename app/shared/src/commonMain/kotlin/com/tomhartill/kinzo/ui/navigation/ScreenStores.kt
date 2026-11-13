package com.tomhartill.kinzo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A view model store per back-stack entry, so a screen's view models live exactly as long as
 * the screen. It is itself a view model in the platform's store (the Android activity's), so
 * screens keep their view models through rotation and other configuration changes.
 */
internal class ScreenStores : ViewModel() {
    private val stores = mutableMapOf<Long, ViewModelStore>()

    fun storeFor(entry: BackStackEntry): ViewModelStore = stores.getOrPut(entry.id) { ViewModelStore() }

    /** Clears a popped screen's view models. */
    fun clear(entry: BackStackEntry) {
        stores.remove(entry.id)?.clear()
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    override fun onCleared() = clearAll()
}

/**
 * Provides the [ScreenStores] to [content]. View models for the tabs go in the platform's own
 * store; hosts without one get a store that lasts as long as the UI.
 */
@Composable
internal fun ProvideScreenStores(content: @Composable (ScreenStores) -> Unit) {
    val owner = LocalViewModelStoreOwner.current
    if (owner != null) {
        content(viewModel(viewModelStoreOwner = owner) { ScreenStores() })
    } else {
        val rootStore = remember { ViewModelStore() }
        val stores = remember { ScreenStores() }
        DisposableEffect(Unit) {
            onDispose {
                stores.clearAll()
                rootStore.clear()
            }
        }
        ProvideViewModelStore(rootStore) { content(stores) }
    }
}
