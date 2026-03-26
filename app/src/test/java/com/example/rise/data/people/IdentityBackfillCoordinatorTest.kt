package com.example.rise.data.people

import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.models.User
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IdentityBackfillCoordinatorTest {

    private lateinit var registry: IdentityRegistry
    private lateinit var coordinator: IdentityBackfillCoordinator

    @Before
    fun setUp() {
        registry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        coordinator = IdentityBackfillCoordinator(
            userRemoteDataSource = FakeUserRemoteDataSource(
                listOf(
                    UserRemoteDataSource.UserSnapshot(
                        id = "me",
                        user = User("Me", "Bio", null, mutableListOf()),
                    ),
                    UserRemoteDataSource.UserSnapshot(
                        id = "friend",
                        user = User("Friend", "Howdy", null, mutableListOf()),
                    )
                )
            ),
            identityRegistry = registry,
        )
    }

    @Test
    fun `backfill populates registry and sets current identity`() = runTest {
        coordinator.backfill(currentUserId = "me")

        val current = registry.currentIdentitySnapshot()
        assertEquals("me", current?.id)
        val entries = registry.identitiesSnapshot()
        assertEquals(2, entries.size)
        val friend = entries.first { it.canonicalIdentity.id == "friend" }
        assertEquals("Friend", friend.canonicalIdentity.displayName)
        assertEquals(PresenceStatus.UNKNOWN, friend.profile.presence)
        assertEquals("friend", friend.aliases[TransportId.FIRESTORE])
    }

    private class FakeUserRemoteDataSource(
        private val snapshots: List<UserRemoteDataSource.UserSnapshot>
    ) : UserRemoteDataSource {

        override suspend fun fetchUser(userId: String): User? = snapshots.firstOrNull { it.id == userId }?.user

        override suspend fun updateUser(userId: String, updates: Map<String, Any>) = Unit

        override suspend fun setUser(userId: String, user: User) = Unit

        override fun observeUsers(): Flow<List<UserRemoteDataSource.UserSnapshot>> = flowOf(snapshots)
    }

    private class InMemoryIdentityRegistryStore : IdentityRegistryStore {
        private var state = IdentityRegistryStore.StoredState(emptyMap(), null)

        override fun load(): IdentityRegistryStore.StoredState = state

        override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
            state = IdentityRegistryStore.StoredState(records, currentIdentityId)
        }
    }
}
