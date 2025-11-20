package com.example.rise.data.people

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryBriarContactGateway : BriarContactGateway {

    private val contacts = MutableStateFlow<List<PersonSummary>>(emptyList())

    override fun observeContacts(): Flow<List<PersonSummary>> = contacts.asStateFlow()

    fun setContacts(newContacts: List<PersonSummary>) {
        contacts.value = newContacts
    }
}
