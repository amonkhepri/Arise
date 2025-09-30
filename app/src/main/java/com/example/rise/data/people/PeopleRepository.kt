package com.example.rise.data.people

import kotlinx.coroutines.flow.Flow

data class PersonSummary(
    val id: String,
    val name: String,
    val bio: String,
    val profilePicturePath: String?
)

interface PeopleRepository {
    fun observePeople(): Flow<List<PersonSummary>>
}
