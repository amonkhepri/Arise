package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mirrors [NoOpBriarChatGateway] for the contact surface so consumers can observe
 * readiness changes even before the true implementation arrives.
 */
object NoOpBriarContactService : BriarContactService {
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
