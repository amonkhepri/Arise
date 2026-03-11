package com.example.rise.data.auth

import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeAuthStateProviderTest {

    @Test
    fun `reports signed in when primary auth is signed in`() {
        val provider = CompositeAuthStateProvider(
            primary = FakeAuthStateProvider(
                isSignedIn = true,
                userId = "firebase-id",
                displayName = "Firebase User",
            ),
            identityRegistry = FakeIdentityRegistry(),
        )

        assertTrue(provider.isSignedIn())
        assertEquals("firebase-id", provider.currentUserId())
        assertEquals("Firebase User", provider.currentUserDisplayName())
    }

    @Test
    fun `reports signed in when briar identity exists without primary auth`() {
        val provider = CompositeAuthStateProvider(
            primary = FakeAuthStateProvider(isSignedIn = false),
            identityRegistry = FakeIdentityRegistry(
                snapshotIdentity = CanonicalIdentity(id = "briar-id", displayName = "Briar User"),
            ),
        )

        assertTrue(provider.isSignedIn())
        assertEquals("briar-id", provider.currentUserId())
        assertEquals("Briar User", provider.currentUserDisplayName())
    }

    @Test
    fun `reports signed out when neither primary auth nor briar identity exists`() {
        val provider = CompositeAuthStateProvider(
            primary = FakeAuthStateProvider(isSignedIn = false),
            identityRegistry = FakeIdentityRegistry(),
        )

        assertFalse(provider.isSignedIn())
        assertEquals(null, provider.currentUserId())
        assertEquals(null, provider.currentUserDisplayName())
    }

    private class FakeAuthStateProvider(
        private val isSignedIn: Boolean,
        private val userId: String? = null,
        private val displayName: String? = null,
    ) : AuthStateProvider {
        override fun isSignedIn(): Boolean = isSignedIn

        override fun currentUserId(): String? = userId

        override fun currentUserDisplayName(): String? = displayName
    }

    private class FakeIdentityRegistry(
        private val snapshotIdentity: CanonicalIdentity? = null,
    ) : IdentityRegistry {
        override val currentIdentity: Flow<CanonicalIdentity> = emptyFlow()
        override val identities: Flow<List<IdentityRecord>> = emptyFlow()

        override fun currentIdentitySnapshot(): CanonicalIdentity? = snapshotIdentity

        override suspend fun resolveByConnector(
            transport: TransportId,
            transportId: String,
        ): CanonicalIdentity = error("unused")

        override fun conversationId(participants: Set<String>): String = error("unused")

        override suspend fun upsertIdentity(
            identity: CanonicalIdentity,
            aliases: Map<TransportId, String>,
            profile: IdentityProfile?,
            setAsCurrent: Boolean,
        ) = Unit

        override suspend fun removeIdentity(canonicalId: String) = Unit

        override fun identitiesSnapshot(): List<IdentityRecord> = emptyList()

        override suspend fun linkAlias(canonicalId: String, transport: TransportId, transportId: String) = Unit

        override suspend fun removeAlias(canonicalId: String, transport: TransportId) = Unit

        override suspend fun clear() = Unit
    }
}
