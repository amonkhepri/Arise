package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.ConnectorContact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBriarContactAdapterTest {

    @Test
    fun `observeContacts maps briar contacts`() = runTest {
        val contact = BriarContact(
            canonicalId = "canon",
            transportAlias = "alias",
            displayName = "Alias",
            presence = BriarPresenceStatus.ONLINE,
        )
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true
            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(listOf(contact))
        }
        val adapter = DefaultBriarContactAdapter(fakeBridge(service))

        val contacts: List<ConnectorContact> = adapter.observeContacts().first()

        assertEquals(1, contacts.size)
        val mapped = contacts.first()
        assertEquals("canon", mapped.canonicalId)
        assertEquals("alias", mapped.transportId)
    }

    private fun fakeBridge(service: BriarContactService): TransportRuntimeBridge {
        val contactFlow = MutableStateFlow(service)
        val mode = MutableStateFlow(BriarTransportMode.HYBRID)
        val status = MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.RUNNING))
        return object : TransportRuntimeBridge {
            override val currentMode: StateFlow<BriarTransportMode> = mode.asStateFlow()
            override val runtimeStatus: StateFlow<BriarRuntimeStatus> = status.asStateFlow()
            override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
            override val briarChatGateway = MutableStateFlow(NoOpBriarChatGateway).asStateFlow()
            override val briarContactService: StateFlow<BriarContactService> = contactFlow
            override fun requireFirestore(caller: String) = Unit
        }
    }
}
