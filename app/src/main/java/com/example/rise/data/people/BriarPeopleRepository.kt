package com.example.rise.data.people

import kotlinx.coroutines.flow.Flow

class BriarPeopleRepository(
    private val contactGateway: BriarContactGateway,
) : PeopleRepository {

    override fun observePeople(): Flow<List<PersonSummary>> = contactGateway.observeContacts()
}
