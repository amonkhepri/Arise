package com.example.rise.data.people

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class HybridPeopleRepositoryTest {

    @Test
    fun `switches people source when mode changes`() = runTest {
        val transportMode = MutableStateFlow(BriarTransportMode.FIRESTORE)
        val transportBridge = FakeTransportBridge(transportMode)
        val firestorePeople = FakePeopleRepository(listOf(PersonSummary("fs", "Firestore Friend", "", null)))
        val briarGateway = InMemoryBriarContactGateway().apply {
            setContacts(listOf(PersonSummary("briar", "Briar Buddy", "", null)))
        }
        val briarRepo = BriarPeopleRepository(briarGateway)
        val repository = HybridPeopleRepository(transportBridge, firestorePeople, briarRepo)

        repository.observePeople().test {
            // Firestore by default
            val first = awaitItem()
            assert(first.any { it.id == "fs" })

            // Switch to hybrid and ensure Briar contacts surface
            transportMode.update { BriarTransportMode.HYBRID }
            val second = awaitItem()
            assert(second.any { it.id == "briar" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeTransportBridge(
        override val currentMode: MutableStateFlow<BriarTransportMode>
    ) : TransportRuntimeBridge {
        override fun requireFirestore(caller: String) = Unit
    }

    private class FakePeopleRepository(initial: List<PersonSummary>) : PeopleRepository {
        private val state = MutableStateFlow(initial)
        override fun observePeople(): Flow<List<PersonSummary>> = state
    }
}
