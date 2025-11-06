package com.example.rise.data.people

import app.cash.turbine.test
import com.example.rise.models.User
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.IdentityRecord
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.IdentityRegistryImpl
import com.example.rise.transport.router.IdentityRegistryStore
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouterPeopleRepositoryTest {

    private lateinit var sync: FakePeopleSync
    private lateinit var identityRegistry: FakeIdentityRegistry
    private lateinit var repository: RouterPeopleRepositoryImpl

    @Before
    fun setUp() {
        identityRegistry = FakeIdentityRegistry()
        sync = FakePeopleSync()
        repository = RouterPeopleRepositoryImpl(identityRegistry, sync)
    }

    @Test
    fun `observePeople maps identity registry records`() = runTest {
        identityRegistry.setIdentities(
            listOf(
                IdentityRecord(
                    canonicalIdentity = CanonicalIdentity("self", "Self"),
                    aliases = emptyMap(),
                    profile = IdentityProfile(bio = "", profilePicturePath = null, presence = PresenceStatus.ONLINE),
                ),
                IdentityRecord(
                    canonicalIdentity = CanonicalIdentity("friend", "Friend"),
                    aliases = mapOf(TransportId.FIRESTORE to "friend"),
                    profile = IdentityProfile(bio = "Howdy", profilePicturePath = "path", presence = PresenceStatus.OFFLINE),
                ),
            )
        )
        identityRegistry.setCurrentIdentity(CanonicalIdentity("self", "Self"))
        sync.setCurrentUserId("self")

        repository.observePeople().test {
            val people = awaitItem()
            assertEquals(1, people.size)
            with(people.first()) {
                assertEquals("friend", id)
                assertEquals("Friend", name)
                assertEquals("Howdy", bio)
                assertEquals("path", profilePicturePath)
                assertEquals(PresenceStatus.OFFLINE, presence)
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, sync.ensureStartedCount)
    }

    @Test
    fun `observePeople emits updates when identity profile changes`() = runTest {
        val friendRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("friend", "Friend"),
            aliases = mapOf(TransportId.FIRESTORE to "friend"),
            profile = IdentityProfile(bio = "", profilePicturePath = null, presence = PresenceStatus.OFFLINE),
        )
        identityRegistry.setIdentities(listOf(friendRecord))

        repository.observePeople().test {
            val initial = awaitItem()
            assertEquals(PresenceStatus.OFFLINE, initial.single().presence)

            val updatedRecord = friendRecord.copy(
                profile = friendRecord.profile.copy(presence = PresenceStatus.ONLINE),
            )
            identityRegistry.updateIdentity(updatedRecord)

            val updated = awaitItem()
            assertEquals(PresenceStatus.ONLINE, updated.single().presence)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, sync.ensureStartedCount)
    }

    @Test
    fun `observePeople filters current identity while sync canonical id is unknown`() = runTest {
        val selfRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("self", "Self"),
            aliases = mapOf(TransportId.FIRESTORE to "self"),
            profile = IdentityProfile(),
        )
        val friendRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("friend", "Friend"),
            aliases = mapOf(TransportId.FIRESTORE to "friend"),
            profile = IdentityProfile(),
        )
        identityRegistry.setIdentities(listOf(selfRecord, friendRecord))
        identityRegistry.setCurrentIdentity(selfRecord.canonicalIdentity)
        sync.setCurrentUserId(null)

        repository.observePeople().test {
            val people = awaitItem()
            assertEquals(listOf("friend"), people.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, sync.ensureStartedCount)
    }

    @Test
    fun `observePerson emits updates for requested identity`() = runTest {
        val friendRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("friend", "Friend"),
            aliases = mapOf(TransportId.FIRESTORE to "friend"),
            profile = IdentityProfile(bio = "Howdy", profilePicturePath = null, presence = PresenceStatus.UNKNOWN),
        )
        identityRegistry.setIdentities(listOf(friendRecord))

        repository.observePerson("friend").test {
            val initial = awaitItem()
            requireNotNull(initial)
            assertEquals("Friend", initial.name)
            assertEquals(PresenceStatus.UNKNOWN, initial.presence)

            val updatedRecord = friendRecord.copy(
                canonicalIdentity = friendRecord.canonicalIdentity.copy(displayName = "Frida"),
                profile = friendRecord.profile.copy(presence = PresenceStatus.ONLINE),
            )
            identityRegistry.updateIdentity(updatedRecord)

            val updated = awaitItem()
            requireNotNull(updated)
            assertEquals("Frida", updated.name)
            assertEquals(PresenceStatus.ONLINE, updated.presence)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, sync.ensureStartedCount)
    }

    @Test
    fun `findPerson returns snapshot from identity registry`() = runTest {
        val friendRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("friend", "Friend"),
            aliases = mapOf(TransportId.FIRESTORE to "friend"),
            profile = IdentityProfile(bio = "Howdy", profilePicturePath = "path", presence = PresenceStatus.ONLINE),
        )
        identityRegistry.setIdentities(listOf(friendRecord))

        val summary = repository.findPerson("friend")
        requireNotNull(summary)
        assertEquals("Friend", summary.name)
        assertEquals("path", summary.profilePicturePath)

        val missing = repository.findPerson("unknown")
        assertEquals(null, missing)
        assertEquals(2, sync.ensureStartedCount)
    }

    @Test
    fun `observePeople continues emitting after sync error`() = runTest {
        val friendRecord = IdentityRecord(
            canonicalIdentity = CanonicalIdentity("friend", "Friend"),
            aliases = mapOf(TransportId.FIRESTORE to "friend"),
            profile = IdentityProfile(presence = PresenceStatus.ONLINE),
        )
        identityRegistry.setIdentities(listOf(friendRecord))
        val sync = ErroringPeopleSync().apply { setCurrentUserId("self") }
        val repository = RouterPeopleRepositoryImpl(identityRegistry = identityRegistry, peopleSync = sync)

        val errorJob = launch {
            repository.syncPeopleErrors.test {
                val error = awaitItem()
                assertEquals("listener failure", error.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

        repository.observePeople().test {
            val initial = awaitItem()
            assertEquals(listOf("friend"), initial.map { it.id })

            sync.emitError(IllegalStateException("listener failure"))

            val updatedRecord = friendRecord.copy(
                profile = friendRecord.profile.copy(presence = PresenceStatus.UNKNOWN),
            )
            identityRegistry.updateIdentity(updatedRecord)

            val afterError = awaitItem()
            assertEquals(listOf("friend"), afterError.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        errorJob.cancelAndJoin()
    }

    @Test
    fun `observePeople does not drop contacts when current identity updates`() = runTest {
        val registry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        val sync = FakePeopleSync().apply { setCurrentUserId("self") }
        val repository = RouterPeopleRepositoryImpl(identityRegistry = registry, peopleSync = sync)

        repository.observePeople().test {
            awaitItem() // initial empty emission

            processSnapshot(
                entries = listOf(
                    FirestoreSnapshotEntry(
                        canonicalId = "friend",
                        user = User("Friend", "Howdy", null, mutableListOf()),
                    ),
                    FirestoreSnapshotEntry(
                        canonicalId = "self",
                        user = User("Self", "Bio", null, mutableListOf()),
                    ),
                ),
                currentUserId = "self",
                identityRegistry = registry,
            )

            repeat(5) {
                val emission = awaitItem()
                if (emission.any { it.id == "friend" }) {
                    cancelAndIgnoreRemainingEvents()
                    return@test
                }
            }
            error("Expected friend to remain visible in contacts")
        }
    }

    @Test
    fun `observePeople temporarily filters contact while current identity catches up`() = runTest {
        val registry = IdentityRegistryImpl(InMemoryIdentityRegistryStore())
        val sync = FakePeopleSync().apply { setCurrentUserId("self") }
        val repository = RouterPeopleRepositoryImpl(identityRegistry = registry, peopleSync = sync)

        repository.observePeople().test {
            assertEquals(emptyList<PersonSummary>(), awaitItem())

            registry.upsertIdentity(
                identity = CanonicalIdentity(id = "friend", displayName = "Friend"),
                aliases = emptyMap(),
                profile = IdentityProfile(),
                setAsCurrent = false,
            )

            val contacts = awaitItem()
            assertEquals(listOf("friend"), contacts.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processSnapshot updates registry from snapshot`() = runTest {
        val registry = FakeIdentityRegistry().apply {
            setIdentities(
                listOf(
                    IdentityRecord(
                        canonicalIdentity = CanonicalIdentity("orphan", "Orphan"),
                        aliases = mapOf(TransportId.FIRESTORE to "orphan"),
                        profile = IdentityProfile(),
                    )
                )
            )
        }
        val entries = listOf(
            FirestoreSnapshotEntry(
                canonicalId = "friend",
                user = User("Friend", "Bio", "path", mutableListOf()),
            ),
            FirestoreSnapshotEntry(
                canonicalId = "self",
                user = User("Self", "Bio", null, mutableListOf()),
            ),
        )

        processSnapshot(
            entries = entries,
            currentUserId = "self",
            identityRegistry = registry,
        )

        val snapshot = registry.identitiesSnapshot().associateBy { it.canonicalIdentity.id }
        assertEquals(setOf("friend", "self"), snapshot.keys)
        val friend = snapshot.getValue("friend")
        assertEquals("Friend", friend.canonicalIdentity.displayName)
        assertEquals("Bio", friend.profile.bio)
        assertEquals("path", friend.profile.profilePicturePath)
        assertEquals(PresenceStatus.UNKNOWN, friend.profile.presence)
        val self = snapshot.getValue("self")
        assertEquals("Self", self.canonicalIdentity.displayName)
        assertEquals("Bio", self.profile.bio)
        assertEquals(PresenceStatus.UNKNOWN, self.profile.presence)
        assertEquals("self", registry.currentIdentitySnapshot()?.id)
    }
}

private class InMemoryIdentityRegistryStore(
    initialState: IdentityRegistryStore.StoredState = IdentityRegistryStore.StoredState(emptyMap(), null)
) : IdentityRegistryStore {
    var state: IdentityRegistryStore.StoredState = initialState
        private set

    override fun load(): IdentityRegistryStore.StoredState = state

    override fun persist(records: Map<String, IdentityRecord>, currentIdentityId: String?) {
        val snapshot = records.mapValues { (_, record) ->
            IdentityRecord(
                canonicalIdentity = record.canonicalIdentity,
                aliases = record.aliases.toMap(),
                profile = record.profile,
            )
        }
        state = IdentityRegistryStore.StoredState(snapshot, currentIdentityId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeIdentityRegistry : IdentityRegistry {

    private val _records = MutableStateFlow<Map<String, IdentityRecord>>(emptyMap())
    private val _current = MutableStateFlow<CanonicalIdentity?>(null)

    fun setIdentities(records: List<IdentityRecord>) {
        _records.value = records.associateBy { it.canonicalIdentity.id }
    }

    fun updateIdentity(record: IdentityRecord) {
        _records.update { current ->
            current + (record.canonicalIdentity.id to record)
        }
    }

    fun setCurrentIdentity(identity: CanonicalIdentity?) {
        _current.value = identity
    }

    override val currentIdentity = _current
        .filterNotNull()

    override val identities = _records
        .map { it.values.sortedBy { record -> record.canonicalIdentity.displayName } }

    override fun currentIdentitySnapshot(): CanonicalIdentity? = _current.value

    override suspend fun resolveByConnector(transport: TransportId, transportId: String): CanonicalIdentity {
        throw UnsupportedOperationException()
    }

    override fun conversationId(participants: Set<String>): String = ""

    override suspend fun upsertIdentity(
        identity: CanonicalIdentity,
        aliases: Map<TransportId, String>,
        profile: IdentityProfile?,
        setAsCurrent: Boolean,
    ) {
        _records.update { current ->
            current + (identity.id to IdentityRecord(identity, aliases, profile ?: IdentityProfile()))
        }
        if (setAsCurrent || _current.value == null) {
            _current.value = identity
        }
    }

    override suspend fun removeIdentity(canonicalId: String) {
        _records.update { current -> current - canonicalId }
    }

    override fun identitiesSnapshot(): List<IdentityRecord> = _records.value.values.toList()

    override suspend fun linkAlias(canonicalId: String, transport: TransportId, transportId: String) = Unit

    override suspend fun removeAlias(canonicalId: String, transport: TransportId) = Unit

    override suspend fun clear() {
        _records.value = emptyMap()
    }
}

private class FakePeopleSync : PeopleSync {
    var ensureStartedCount: Int = 0
        private set
    private val _currentUserId = MutableStateFlow<String?>(null)

    override val syncPeopleErrors = emptyFlow<Throwable>()
    override val currentUserCanonicalId = _currentUserId

    override fun ensureStarted() {
        ensureStartedCount++
    }

    override fun stop() = Unit

    fun setCurrentUserId(id: String?) {
        _currentUserId.value = id
    }
}

private class ErroringPeopleSync : PeopleSync {
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private val _currentUserId = MutableStateFlow<String?>(null)

    override val syncPeopleErrors = _errors
    override val currentUserCanonicalId = _currentUserId

    override fun ensureStarted() = Unit

    override fun stop() = Unit

    fun emitError(error: Throwable) {
        _errors.tryEmit(error)
    }

    fun setCurrentUserId(id: String?) {
        _currentUserId.value = id
    }
}
