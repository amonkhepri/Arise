package com.example.rise.transport.briar

import com.example.rise.transport.router.ConnectorContact
import kotlinx.coroutines.flow.Flow

/**
 * Adapter that surfaces Briar contact and presence updates in the canonical format expected by the
 * transport router. It bridges the runtime-facing Briar contact service into the app layer.
 */
interface BriarContactAdapter {
    /**
     * Observes the list of contacts known to Briar, emitting whenever the runtime reports a change
     * so the router can keep its directory synchronized.
     */
    fun observeContacts(): Flow<List<ConnectorContact>>
}
