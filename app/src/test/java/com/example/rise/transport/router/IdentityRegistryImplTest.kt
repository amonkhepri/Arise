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
        val profile = IdentityProfile(bio = "Hello", profilePicturePath = "path", presence = PresenceStatus.ONLINE)
        registry.upsertIdentity(
            identity = canonical,
            aliases = mapOf(TransportId.FIRESTORE to "firestore-self"),
            profile = profile,
            setAsCurrent = true,
        )

        val resolved = registry.resolveByConnector(TransportId.FIRESTORE, "firestore-self")
        assertEquals(canonical, resolved)
        assertEquals("self", store.state.currentIdentityId)
        assertTrue(store.state.records.containsKey("self"))
        val identities = registry.identities.first()
        assertEquals(1, identities.size)
        val record = identities.first()
        assertEquals("Self", record.identity.displayName)
        assertEquals(profile, record.profile)
    }

    @Test
    fun `linkAlias moves alias across canonical identities`() = runTest {
        val store = FakeIdentityRegistryStore()
        val registry = IdentityRegistryImpl(store)

        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "alice", displayName = "Alice"),
            aliases = mapOf(TransportId.FIRESTORE to "fire-alice"),
            profile = IdentityProfile(bio = "A", profilePicturePath = null, presence = PresenceStatus.UNKNOWN),
            setAsCurrent = true,
        )
        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "bob", displayName = "Bob"),
            aliases = emptyMap(),
            profile = IdentityProfile(),
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

    @Test
    fun `removeIdentity deletes aliases and records`() = runTest {
        val store = FakeIdentityRegistryStore()
        val registry = IdentityRegistryImpl(store)

        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "alice", displayName = "Alice"),
            aliases = mapOf(TransportId.FIRESTORE to "fire-alice"),
            profile = IdentityProfile(),
            setAsCurrent = false,
        )

        registry.removeIdentity("alice")

        val records = registry.identities.first()
        assertTrue(records.none { it.identity.id == "alice" })
        assertTrue(store.state.records.isEmpty())
    }

    @Test
    fun `identitiesSnapshot returns latest records`() = runTest {
        val registry = IdentityRegistryImpl(FakeIdentityRegistryStore())
        registry.upsertIdentity(
            identity = CanonicalIdentity(id = "user", displayName = "User"),
            aliases = mapOf(TransportId.FIRESTORE to "fire-user"),
            profile = IdentityProfile(),
            setAsCurrent = false,
        )

        val snapshot = registry.identitiesSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("user", snapshot.first().identity.id)
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
                    aliases = record.aliases.toMap(),
                    profile = record.profile,
                )
            }
            state = IdentityRegistryStore.StoredState(recordsCopy, currentIdentityId)
        }
    }
}
