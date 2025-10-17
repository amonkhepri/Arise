package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight `BriarChatGateway` that never exposes real functionality. It keeps the
 * wiring stable during Stage 1 while allowing downstream code to observe readiness flips.
 */
object NoOpBriarChatGateway : BriarChatGateway {
    private val readiness = MutableStateFlow(false)
    val readinessFlow: StateFlow<Boolean> = readiness

    override val isAvailable: Boolean
        get() = readiness.value

    fun reset() {
        readiness.value = false
    }

    fun markReady() {
        readiness.value = true
    }
}
