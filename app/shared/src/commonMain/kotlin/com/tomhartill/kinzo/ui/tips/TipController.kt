package com.tomhartill.kinzo.ui.tips

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import com.tomhartill.kinzo.data.GuidanceRepository
import com.tomhartill.kinzo.domain.ScreenTips
import com.tomhartill.kinzo.domain.Tip
import com.tomhartill.kinzo.domain.TipQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App-wide: which tip shows now, and where each spotlight's control is on screen. */
class TipController(private val repository: GuidanceRepository, private val scope: CoroutineScope) {
    private val queue = MutableStateFlow(TipQueue())
    val state: StateFlow<TipQueue> = queue.asStateFlow()

    /** Spotlight controls' bounds in the app's root coordinates, by tip id. */
    internal val anchors = mutableStateMapOf<String, Rect>()

    init {
        scope.launch {
            repository.tips().collect { tips -> queue.update { it.loaded(tips.seen, tips.enabled) } }
        }
    }

    fun visit(tips: ScreenTips) = queue.update { it.visit(tips) }

    fun replay(tips: ScreenTips) = queue.update { it.replay(tips) }

    fun leave(screen: String) = queue.update { it.leave(screen) }

    fun dismiss(tip: Tip) {
        queue.update { it.dismiss(tip) }
        scope.launch { repository.markTipSeen(tip.id) }
    }

    fun skip(tip: Tip) = queue.update { it.skip(tip) }

    fun resetAll() {
        scope.launch { repository.resetTips() }
    }
}
