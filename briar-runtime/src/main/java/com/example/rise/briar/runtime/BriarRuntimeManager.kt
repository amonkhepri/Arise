package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public façade for controlling the embedded Briar runtime. Koin registers this
 * manager so other modules can observe state and request lifecycle transitions.
 */
interface BriarRuntimeManager {
    val status: StateFlow<BriarRuntimeStatus>
    val diagnostics: SharedFlow<BriarRuntimeEvent>
    val chatGateway: StateFlow<BriarChatGateway>
    val contactService: StateFlow<BriarContactService>

    suspend fun ensureStarted()
    suspend fun stop()
}
