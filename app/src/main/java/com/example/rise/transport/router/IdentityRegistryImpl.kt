package com.example.rise.transport.router

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IdentityRegistryImpl(
    private val store: IdentityRegistryStore,
) : IdentityRegistry {

    // Guards concurrent writers so alias maps and persisted state stay consistent across threads.
    private val mutex = Mutex()
    private val aliasToCanonical = ConcurrentHashMap<Pair<TransportId, String>, String>()
    private val _currentIdentity = MutableStateFlow<CanonicalIdentity?>(null)
    private val _records = MutableStateFlow<Map<String, IdentityRecord>>(emptyMap())

    init {
        val storedState = store.load()
        _records.value = storedState.records
        storedState.records.forEach { (canonicalId, record) ->
            record.aliases.forEach { (transport, alias) ->
                aliasToCanonical[transport to alias] = canonicalId
            }
        }
        val currentIdentity = storedState.currentIdentityId?.let { id ->
            storedState.records[id]?.identity
        }
        if (currentIdentity != null) {
            _currentIdentity.value = currentIdentity
        }
    }

    override val currentIdentity: Flow<CanonicalIdentity> = _currentIdentity.filterNotNull()

    override val identities: Flow<List<IdentityRecord>> = _records
        .asStateFlow()
        .map { records ->
            records.values.sortedBy { it.identity.displayName }
        }

    override fun currentIdentitySnapshot(): CanonicalIdentity? = _currentIdentity.value

    override suspend fun resolveByConnector(
        transport: TransportId,
        transportId: String
    ): CanonicalIdentity {
        val canonicalId = aliasToCanonical[transport to transportId]
            ?: error("Unknown identity for $transport:$transportId")
        return _records.value[canonicalId]?.identity
            ?: error("No canonical identity stored for id=$canonicalId")
    }

    override fun conversationId(participants: Set<String>): String {
        if (participants.isEmpty()) error("Participants cannot be empty")
        val sorted = participants.toList().sorted()
        val joined = sorted.joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    override suspend fun upsertIdentity(
        identity: CanonicalIdentity,
        aliases: Map<TransportId, String>,
        profile: IdentityProfile?,
        setAsCurrent: Boolean
    ) {
        mutex.withLock {
            val updated = _records.value.toMutableMap()
            val existingRecord = updated[identity.id]
            val existingAliases = existingRecord?.aliases.orEmpty().toMutableMap()
            aliases.forEach { (transport, alias) ->
                moveAliasIfNeeded(updated, canonicalId = identity.id, transport = transport, alias = alias)
                existingAliases[transport] = alias
                aliasToCanonical[transport to alias] = identity.id
            }
            val profileToStore = profile ?: existingRecord?.profile ?: IdentityProfile()
            updated[identity.id] = IdentityRecord(
                identity = identity,
                aliases = existingAliases.toMap(),
                profile = profileToStore,
            )
            _records.value = updated
            if (setAsCurrent) {
                _currentIdentity.value = identity
            } else if (_currentIdentity.value == null) {
                _currentIdentity.value = identity
            }
            store.persist(updated, _currentIdentity.value?.id)
        }
    }

    override suspend fun removeIdentity(canonicalId: String) {
        mutex.withLock {
            val updated = _records.value.toMutableMap()
            val record = updated.remove(canonicalId) ?: return@withLock
            record.aliases.forEach { (transport, alias) ->
                aliasToCanonical.remove(transport to alias)
            }
            _records.value = updated
            if (_currentIdentity.value?.id == canonicalId) {
                _currentIdentity.value = null
            }
            store.persist(updated, _currentIdentity.value?.id)
        }
    }

    override fun identitiesSnapshot(): List<IdentityRecord> = _records.value.values.toList()

    override suspend fun linkAlias(canonicalId: String, transport: TransportId, transportId: String) {
        mutex.withLock {
            val updated = _records.value.toMutableMap()
            val record = updated[canonicalId]
                ?: error("Cannot link alias – canonical identity $canonicalId missing")
            moveAliasIfNeeded(updated, canonicalId, transport, transportId)
            val aliases = record.aliases.toMutableMap().apply { this[transport] = transportId }
            updated[canonicalId] = record.copy(aliases = aliases.toMap())
            aliasToCanonical[transport to transportId] = canonicalId
            _records.value = updated
            store.persist(updated, _currentIdentity.value?.id)
        }
    }

    override suspend fun removeAlias(canonicalId: String, transport: TransportId) {
        mutex.withLock {
            val updated = _records.value.toMutableMap()
            val record = updated[canonicalId]
                ?: return
            val aliases = record.aliases.toMutableMap()
            val removed = aliases.remove(transport) ?: return
            aliasToCanonical.remove(transport to removed)
            updated[canonicalId] = record.copy(aliases = aliases.toMap())
            _records.value = updated
            store.persist(updated, _currentIdentity.value?.id)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            aliasToCanonical.clear()
            _records.value = emptyMap()
            _currentIdentity.value = null
            store.persist(emptyMap(), null)
        }
    }

    private fun moveAliasIfNeeded(
        records: MutableMap<String, IdentityRecord>,
        canonicalId: String,
        transport: TransportId,
        alias: String,
    ) {
        val existingCanonical = aliasToCanonical[transport to alias]
        if (existingCanonical != null && existingCanonical != canonicalId) {
            val existingRecord = records[existingCanonical]
            if (existingRecord != null) {
                val updatedAliases = existingRecord.aliases.toMutableMap()
                updatedAliases.remove(transport)
                records[existingCanonical] = existingRecord.copy(aliases = updatedAliases.toMap())
            }
            aliasToCanonical.remove(transport to alias)
        }
    }
}
