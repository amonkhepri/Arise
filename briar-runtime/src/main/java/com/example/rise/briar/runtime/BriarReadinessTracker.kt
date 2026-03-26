package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class BriarReadinessStatus {
    object NotReady : BriarReadinessStatus()
    object Ready : BriarReadinessStatus()
    data class Error(val reason: String? = null) : BriarReadinessStatus()
}

/**
 * Shared readiness indicator for the embedded Briar runtime. Chat/contact
 * adapters rely on this to decide whether the underlying services and identity
 * are usable.
 */
class BriarReadinessTracker(initialStatus: BriarReadinessStatus = BriarReadinessStatus.NotReady) {
    private val readiness = MutableStateFlow(initialStatus)

    val flow: StateFlow<BriarReadinessStatus> = readiness.asStateFlow()

    fun markReady() {
        readiness.value = BriarReadinessStatus.Ready
    }

    fun markNotReady() {
        readiness.value = BriarReadinessStatus.NotReady
    }

    fun markError(reason: String? = null) {
        readiness.value = BriarReadinessStatus.Error(reason)
    }
}
