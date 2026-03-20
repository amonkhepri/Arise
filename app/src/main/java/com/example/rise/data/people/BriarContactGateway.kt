package com.example.rise.data.people

import kotlinx.coroutines.flow.Flow

/**
 * Surface that exposes contacts discovered via the Briar transport layer.
 */
interface BriarContactGateway {
    fun observeContacts(): Flow<List<PersonSummary>>
}
