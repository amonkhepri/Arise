package com.example.rise.transport.router

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityRegistryImplTest {

    @Test
    fun `upsertIdentity persists aliases and resolves lookup`() = runTest {
        val store = FakeIdentityRegistryStore()
        val registry = IdentityRegistryImpl(store)

        val canonical = CanonicalIdentity(id = "self", displayName = "Self")
        registry.upsertIdentity(
            identity = canonical,
            aliases = mapOf(TransportId.FIRESTORE to "firestore-self"),
            setAsCurrent = true,
        )

        val resolved = registry.resolveByConnector(TransportId.FIRESTORE, "firestore-self")
        assertEquals(canonical, resolved)
        assertEquals("self", store.state.currentIdentityId)
        assertTrue(store.state.records.containsKey("self"))
        val identities = registry.identities.first()
        assertEquals(1, identities.size)
        assertEquals("Self", identities.first().identity.displayName)
    }

    @Test
    fun `linkAlias moves alias across canonical identities`() = runTest {
        val store = FakeIdentityRegistryStore()
        val registry = IdentityRegistryImpl(store)

        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "alice", displayName = "Alice"),
            aliases = mapOf(TransportId.FIRESTORE to "fire-alice"),
            setAsCurrent = true,
        )
        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "bob", displayName = "Bob"),
            aliases = emptyMap(),
            setAsCurrent = false,
        )

        registry.linkAlias(canonicalId = "bob", transport = TransportId.FIRESTORE, transportId = "fire-alice")

        val bob = registry.identities.first().first { it.identity.id == "bob" }
        assertEquals("fire-alice", bob.aliases[TransportId.FIRESTORE])
        val alice = registry.identities.first().first { it.identity.id == "alice" }
        assertFalse(alice.aliases.containsKey(TransportId.FIRESTORE))
        val resolved = registry.resolveByConnector(TransportId.FIRESTORE, "fire-alice")
        assertEquals("bob", resolved.id)
    }

    private class FakeIdentityRegistryStore(
        initialState: IdentityRegistryStore.StoredState = IdentityRegistryStore.StoredState(emptyMap(), null)
    ) : IdentityRegistryStore {
        var state: IdentityRegistryStore.StoredState = initialState
            private set

        override fun load(): IdentityRegistryStore.StoredState = state

        override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
            val recordsCopy = records.mapValues { (_, record) ->
                IdentityRecord(
                    identity = record.identity,
                    aliases = record.aliases.toMap()
                )
            }
            state = IdentityRegistryStore.StoredState(recordsCopy, currentIdentityId)
        }
    }
}
