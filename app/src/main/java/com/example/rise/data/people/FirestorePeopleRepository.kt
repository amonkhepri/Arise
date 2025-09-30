package com.example.rise.data.people

import com.example.rise.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestorePeopleRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : PeopleRepository {

    override fun observePeople(): Flow<List<PersonSummary>> = callbackFlow {
        val registration = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val currentUserId = auth.currentUser?.uid
                val people = snapshot?.documents
                    ?.mapNotNull { document ->
                        if (document.id == currentUserId) return@mapNotNull null
                        val user = document.toObject(User::class.java) ?: return@mapNotNull null
                        PersonSummary(
                            id = document.id,
                            name = user.name,
                            bio = user.bio,
                            profilePicturePath = user.profilePicturePath
                        )
                    }
                    .orEmpty()
                trySend(people).isSuccess
            }
        awaitClose { registration.remove() }
    }
}
