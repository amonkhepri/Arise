package com.example.rise.transport.router

import kotlinx.coroutines.flow.Flow

/**
 * Maintains the mapping between connector identities and the canonical Briar-anchored IDs.
 * Stage 2 keeps a simple deterministic registry that relies on Firestore user IDs while the
 * Briar runtime and Telegram flows come online.
 */
interface IdentityRegistry {

    /**
     * Emits the current signed-in identity anchored to the canonical transport.
     */
    val currentIdentity: Flow<CanonicalIdentity>

    /**
     * Emits the full registry so UI surfaces can drive manual merge/link flows.
     */
    val identities: Flow<List<IdentityRecord>>

    /**
     * Returns the most recently observed canonical identity for the signed-in user, or `null`
     * when no identity has been resolved yet.
     *
     * This is a synchronous accessor to the same data that backs [currentIdentity]. The router uses
     * it when it needs a quick, non-suspending check (for example, to compare the cached identity
     * against the connector-reported identity inside `resolveCurrentIdentity()` before deciding
     * whether the cache should be refreshed, cleared on sign-out, or reused).
     *
     * Consumers must treat this as a snapshot. The value may become stale immediately after it is
     * read if another thread updates the registry; call sites that require fresh data should use
     * the suspend/Flow APIs instead.
     */
    fun currentIdentitySnapshot(): CanonicalIdentity?

    /**
     * Resolves the canonical identity for the supplied connector-specific ID.
     */
    suspend fun resolveByConnector(transport: TransportId, transportId: String): CanonicalIdentity

    /**
     * Returns a deterministic canonical conversation ID for the supplied participants.
     * Stage 2 uses a hash composed of sorted canonical IDs.
     */
    fun conversationId(participants: Set<String>): String

    /**
     * Seeds the registry with a known mapping. FirestoreConnector leverages this when it
     * loads existing chat participants.
     */
    suspend fun upsertIdentity(
        identity: CanonicalIdentity,
        aliases: Map<TransportId, String>,
        setAsCurrent: Boolean = false,
    )

    /**
     * Manually links a connector alias to an existing canonical identity (used by merge UI).
     */
    suspend fun linkAlias(canonicalId: String, transport: TransportId, transportId: String)

    /**
     * Removes an alias mapping (e.g., when a tester unlinks a connector from an identity).
     */
    suspend fun removeAlias(canonicalId: String, transport: TransportId)

    /**
     * Clears all cached identities and aliases. Used when the active account changes so any
     * lingering mappings from the previous user do not leak into the new session.
     */
    suspend fun clear()
}
