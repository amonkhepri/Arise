package com.example.rise.transport.briar

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContact
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarPresenceStatus
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BriarContactRepositoryTest {

    @Test
    fun `addContactByLink delegates to contact service when available`() = runTest {
        val service = RecordingContactService(isAvailable = true)
        val repository = BriarContactRepository(fakeBridge(service))
        val link = validLink()

        repository.addContactByLink(link, "Alias")

        assertEquals(listOf(link to "Alias"), service.calls)
    }

    @Test
    fun `addContactByLink waits for contact service availability on cold start`() = runTest {
        val unavailableService = RecordingContactService(isAvailable = false)
        val availableService = RecordingContactService(isAvailable = true)
        val services = MutableStateFlow<BriarContactService>(unavailableService)
        val repository = BriarContactRepository(
            transportRuntimeBridge = fakeBridge(services),
            serviceAvailabilityTimeoutMillis = 5_000L,
        )
        val link = validLink()

        backgroundScope.launch {
            delay(2_500L)
            services.value = availableService
        }

        repository.addContactByLink(link, "Alias")

        assertEquals(emptyList<Pair<String, String?>>(), unavailableService.calls)
        assertEquals(listOf(link to "Alias"), availableService.calls)
    }

    @Test
    fun `addContactByLink throws when runtime not ready`() = runTest {
        val service = RecordingContactService(isAvailable = false)
        val repository = BriarContactRepository(
            transportRuntimeBridge = fakeBridge(service),
            serviceAvailabilityTimeoutMillis = 1L,
        )

        val error = try {
            repository.addContactByLink(validLink(), "Alias")
            null
        } catch (t: Throwable) {
            t
        }
        assertTrue(error is IllegalStateException)
        assertEquals(emptyList<Pair<String, String?>>(), service.calls)
    }

    @Test
    fun `addContactByLink propagates service errors`() = runTest {
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true
            override suspend fun addContactByLink(link: String, alias: String?) {
                throw IllegalArgumentException("invalid link")
            }
            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val repository = BriarContactRepository(fakeBridge(service))

        val error = try {
            repository.addContactByLink("invalid", "Alias")
            null
        } catch (t: Throwable) {
            t
        }
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `getHandshakeLink delegates to contact service when available`() = runTest {
        val service = RecordingContactService(
            isAvailable = true,
            handshakeLink = validLink(),
        )
        val repository = BriarContactRepository(fakeBridge(service))

        val result = repository.getHandshakeLink()

        assertEquals(validLink(), result)
        assertEquals(1, service.getHandshakeLinkCalls)
    }

    @Test
    fun `getHandshakeLink throws when runtime not ready`() = runTest {
        val service = RecordingContactService(
            isAvailable = false,
            handshakeLink = validLink(),
        )
        val repository = BriarContactRepository(
            transportRuntimeBridge = fakeBridge(service),
            serviceAvailabilityTimeoutMillis = 1L,
        )

        val error = try {
            repository.getHandshakeLink()
            null
        } catch (t: Throwable) {
            t
        }

        assertTrue(error is IllegalStateException)
        assertEquals(0, service.getHandshakeLinkCalls)
    }

    @Test
    fun `getHandshakeLink propagates service errors`() = runTest {
        val service = object : BriarContactService {
            override val isAvailable: Boolean = true
            override fun getHandshakeLink(): String {
                throw IllegalArgumentException("link unavailable")
            }
            override suspend fun addContactByLink(link: String, alias: String?) = Unit
            override fun observeContacts(): Flow<List<BriarContact>> = flowOf(emptyList())
        }
        val repository = BriarContactRepository(fakeBridge(service))

        val error = try {
            repository.getHandshakeLink()
            null
        } catch (t: Throwable) {
            t
        }

        assertTrue(error is IllegalArgumentException)
    }

    private fun validLink(): String = "briar://${"b".repeat(53)}" // handshake link length

    private fun fakeBridge(service: BriarContactService): TransportRuntimeBridge {
        return fakeBridge(MutableStateFlow(service))
    }

    private fun fakeBridge(
        contacts: MutableStateFlow<BriarContactService>,
    ): TransportRuntimeBridge {
        val mode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)
        val status = MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.RUNNING))
        val chat = MutableStateFlow<BriarChatGateway>(NoOpBriarChatGateway)
        return object : TransportRuntimeBridge {
            override val currentMode: StateFlow<BriarTransportMode> = mode
            override val runtimeStatus: StateFlow<BriarRuntimeStatus> = status
            override val diagnostics: MutableSharedFlow<BriarRuntimeEvent> = MutableSharedFlow()
            override val briarChatGateway: StateFlow<BriarChatGateway> = chat
            override val briarContactService: StateFlow<BriarContactService> = contacts
            override fun requireFirestore(caller: String) = Unit
        }
    }

    private class RecordingContactService(
        override val isAvailable: Boolean,
        private val handshakeLink: String = "briar://${"b".repeat(53)}",
    ) : BriarContactService {
        val calls = mutableListOf<Pair<String, String?>>()
        var getHandshakeLinkCalls: Int = 0
        override suspend fun addContactByLink(link: String, alias: String?) {
            calls += link to alias
        }

        override fun getHandshakeLink(): String {
            getHandshakeLinkCalls += 1
            return handshakeLink
        }

        override fun observeContacts(): Flow<List<BriarContact>> = flowOf(
            listOf(
                BriarContact(
                    canonicalId = "id",
                    transportAlias = "alias",
                    displayName = "Alias",
                    presence = BriarPresenceStatus.UNKNOWN,
                )
            )
        )
    }
}
