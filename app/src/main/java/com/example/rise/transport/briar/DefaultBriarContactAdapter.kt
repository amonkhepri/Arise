package com.example.rise.transport.briar

import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.ConnectorContact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBriarContactAdapter(
    private val transportBridge: TransportRuntimeBridge,
) : BriarContactAdapter {

    override fun observeContacts(): Flow<List<ConnectorContact>> {
        return transportBridge.briarContactService.flatMapLatest { service ->
            service.availability().distinctUntilChanged().flatMapLatest { available ->
                if (!available) {
                    flowOf(emptyList())
                } else {
                    service.observeContacts().map { contacts ->
                        contacts.map { it.toConnectorContact() }
                    }
                }
            }
        }
    }
}
