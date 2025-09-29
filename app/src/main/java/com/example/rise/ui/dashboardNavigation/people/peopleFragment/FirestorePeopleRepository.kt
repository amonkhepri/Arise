package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import com.example.rise.auth.UserSessionProvider
import com.example.rise.util.FirestoreUtil
import com.google.firebase.firestore.ListenerRegistration

class FirestorePeopleRepository(
    private val userSessionProvider: UserSessionProvider
) : PeopleRepository {

    override fun listenForPeople(onPeopleChanged: (List<PersonRecord>) -> Unit): ListenerRegistration {
        return FirestoreUtil.addUsersListener { users ->
            val currentUserId = userSessionProvider.currentUserId()
            val records = users
                .filterKeys { it != currentUserId }
                .map { (id, user) -> PersonRecord(id, user) }
            onPeopleChanged(records)
        }
    }

    override fun removeListener(registration: ListenerRegistration) {
        FirestoreUtil.removeListener(registration)
    }
}
