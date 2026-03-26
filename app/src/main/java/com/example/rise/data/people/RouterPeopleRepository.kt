package com.example.rise.data.people

import com.example.rise.transport.router.PresenceStatus
import kotlinx.coroutines.flow.Flow

data class PersonSummary(
    val id: String,
    val name: String,
    val bio: String,
    val profilePicturePath: String?,
    val presence: PresenceStatus = PresenceStatus.UNKNOWN,
)

interface RouterPeopleRepository {
    /**
     * Emits sync errors that occur while observing the people data. Consumers should surface the
     * error state but keep listening for subsequent updates because the sync layer will retry.
     */
    val syncPeopleErrors: Flow<Throwable>

    /**
     * Emits the full list of known people and updates whenever the backing data changes.
     * Use this when you need to render or react to the complete contacts rather than a single entry.
     */
    fun observePeople(): Flow<List<PersonSummary>>

    /**
     * Emits the requested person whenever that record changes, or `null` if it cannot be resolved.
     * Use this when you need to render or react to a single person's profile rather than the complete contacts.
     * Prefer this over [observePeople] when only one profile needs to stay in sync.
     */
    fun observePerson(personId: String): Flow<PersonSummary?>

    /**
     * Fetches the current snapshot of the requested person once without observing future changes.
     * Use this when you need to fetch a single person's profile without subscribing to future changes.
     * Returns `null` if no matching person is found, unlike [observePerson] which keeps listening.
     */
    suspend fun findPerson(personId: String): PersonSummary?
}
