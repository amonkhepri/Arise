package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import com.example.rise.models.User
import com.google.firebase.firestore.ListenerRegistration

data class PersonRecord(val userId: String, val user: User)

interface PeopleRepository {
    fun listenForPeople(onPeopleChanged: (List<PersonRecord>) -> Unit): ListenerRegistration
    fun removeListener(registration: ListenerRegistration)
}
