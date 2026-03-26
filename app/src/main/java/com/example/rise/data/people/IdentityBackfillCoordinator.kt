package com.example.rise.data.people

import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.PresenceStatus
import kotlinx.coroutines.flow.first

/**
 * One-shot helper that hydrates [IdentityRegistry] with the roster exported by Firestore.
 * It reuses the same snapshot processing logic as [FirestorePeopleSync] so canonical IDs,
 * aliases, profiles, and presence data remain consistent with the live sync path.
 */
class IdentityBackfillCoordinator(
    private val userRemoteDataSource: UserRemoteDataSource,
    private val identityRegistry: IdentityRegistry,
) {

    suspend fun backfill(currentUserId: String?) {
        val snapshots = userRemoteDataSource.observeUsers().first()
        val entries = snapshots.map { snapshot ->
            FirestoreSnapshotEntry(
                canonicalId = snapshot.id,
                user = snapshot.user,
                presence = PresenceStatus.UNKNOWN,
            )
        }
        processSnapshot(
            entries = entries,
            currentUserId = currentUserId,
            identityRegistry = identityRegistry,
        )
    }
}
