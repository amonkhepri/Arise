package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Service exposed by the embedded Briar runtime that surfaces contact and presence state to higher
 * layers. Transport adapters subscribe to this API to mirror the runtime roster.
 */
interface BriarContactService {
    /** Indicates whether the runtime is initialized and ready to emit contact updates. */
    val isAvailable: Boolean

    /** Emits availability changes so connectors can react when readiness toggles. */
    fun availability(): Flow<Boolean> = flowOf(isAvailable)

    /**
     * Attempts to add a contact using a Briar handshake link. Callers should ensure the runtime
     * is ready before invoking.
     */
    suspend fun addContactByLink(link: String, alias: String? = null)

    /**
     * Streams the list of Briar contacts, emitting whenever the runtime reports a change so callers
     * can keep their canonical directories synchronized.
     */
    fun observeContacts(): Flow<List<BriarContact>>
}
