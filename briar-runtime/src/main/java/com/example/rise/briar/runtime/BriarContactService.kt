package com.example.rise.briar.runtime

import kotlinx.coroutines.flow.Flow

/**
 * Service exposed by the embedded Briar runtime that surfaces contact and presence state to higher
 * layers. Transport adapters subscribe to this API to mirror the runtime roster.
 */
interface BriarContactService {
    /** Indicates whether the runtime is initialized and ready to emit contact updates. */
    val isAvailable: Boolean

    /**
     * Streams the list of Briar contacts, emitting whenever the runtime reports a change so callers
     * can keep their canonical directories synchronized.
     */
    fun observeContacts(): Flow<List<BriarContact>>
}
