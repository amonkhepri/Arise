package com.example.rise.data.people

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class HybridPeopleRepository(
    private val transportBridge: TransportRuntimeBridge,
    private val firestoreRepository: PeopleRepository,
    private val briarRepository: PeopleRepository,
) : PeopleRepository {

    override fun observePeople(): Flow<List<PersonSummary>> {
        return transportBridge.currentMode.flatMapLatest { mode ->
            when (mode) {
                BriarTransportMode.FIRESTORE -> firestoreRepository.observePeople()
                BriarTransportMode.HYBRID, BriarTransportMode.BRIAR_ONLY -> briarRepository.observePeople()
            }
        }
    }
}
