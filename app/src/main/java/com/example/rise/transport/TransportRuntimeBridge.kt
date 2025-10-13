package com.example.rise.transport

import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Acts as the single entry point for transport-related runtime concerns.
 *
 * Stage 0 keeps the implementation focused on guarding Firestore-only code paths while
 * exposing the active transport mode reactively for future stages.
 */
interface TransportRuntimeBridge {

    /** Latest transport mode value backed by the shared feature flag. */
    val currentMode: StateFlow<BriarTransportMode>

    /**
     * Raises if anything outside the Firestore transport path attempts to run before
     * Stage 1 provides hybrid wiring.
     */
    fun requireFirestore(caller: String)
}
