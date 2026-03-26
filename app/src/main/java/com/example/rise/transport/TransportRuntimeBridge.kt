package com.example.rise.transport

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Acts as the single entry point for transport-related runtime concerns.
 *
 * Stage 1 still guards Firestore-only repositories but now surfaces the embedded Briar
 * runtime so future stages can hook into chat and contact flows without rewriting the
 * existing call sites.
 */
interface TransportRuntimeBridge {

    /** Latest transport mode value backed by the shared feature flag. */
    val currentMode: StateFlow<BriarTransportMode>

    /** Live status of the embedded Briar runtime. */
    val runtimeStatus: StateFlow<BriarRuntimeStatus>
    val diagnostics: SharedFlow<BriarRuntimeEvent>

    /** Bridge handle for Briar-backed chat flows (Stage 2 will expand this contract). */
    val briarChatGateway: StateFlow<BriarChatGateway>

    /** Bridge handle for Briar-backed contact flows (Stage 3 will expand this contract). */
    val briarContactService: StateFlow<BriarContactService>

    /**
     * Raises if anything outside the Firestore transport path attempts to run before
     * hybrid wiring is complete.
     */
    fun requireFirestore(caller: String)
}
