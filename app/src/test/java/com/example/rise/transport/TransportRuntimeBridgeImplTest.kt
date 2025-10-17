package com.example.rise.transport

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TransportModeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportRuntimeBridgeImplTest {

    @Test
    fun `requireFirestore allows default mode`() {
        runBlocking {
            val dispatcher = Dispatchers.Unconfined
            val provider = FakeTransportModeProvider(BriarTransportMode.FIRESTORE)
            val runtimeManager = FakeRuntimeManager()
            TransportRuntimeBridgeImpl(provider, runtimeManager, dispatcher).apply {
                yield()
                requireFirestore("test call") // should not throw
            }
        }
    }

    @Test
    fun `requireFirestore does not throw on non Firestore modes`() {
        runBlocking {
            val dispatcher = Dispatchers.Unconfined
            val provider = FakeTransportModeProvider(BriarTransportMode.FIRESTORE)
            val runtimeManager = FakeRuntimeManager()
            val bridge = TransportRuntimeBridgeImpl(provider, runtimeManager, dispatcher)

            provider.setMode(BriarTransportMode.HYBRID)
            yield()

            assertTrue(runtimeManager.started)
            bridge.requireFirestore("test call")
            assertEquals(BriarTransportMode.HYBRID, bridge.currentMode.value)
        }
    }

    @Test
    fun `switching back to Firestore stops runtime`() {
        runBlocking {
            val dispatcher = Dispatchers.Unconfined
            val provider = FakeTransportModeProvider(BriarTransportMode.FIRESTORE)
            val runtimeManager = FakeRuntimeManager()
            TransportRuntimeBridgeImpl(provider, runtimeManager, dispatcher)

            provider.setMode(BriarTransportMode.HYBRID)
            yield()
            provider.setMode(BriarTransportMode.FIRESTORE)
            yield()

            assertTrue(runtimeManager.stopped)
        }
    }

    private class FakeTransportModeProvider(
        initialMode: BriarTransportMode
    ) : TransportModeProvider {

        private val state = MutableStateFlow(initialMode)

        override fun observeMode(): Flow<BriarTransportMode> = state

        override suspend fun setMode(mode: BriarTransportMode) {
            state.value = mode
        }

        override suspend fun getMode(): BriarTransportMode = state.value
    }

    private class FakeRuntimeManager : BriarRuntimeManager {
        private val _status = MutableStateFlow(BriarRuntimeStatus(BriarRuntimePhase.STOPPED))
        private val _diagnostics = MutableSharedFlow<BriarRuntimeEvent>()
        private val _chatGateway = MutableStateFlow<BriarChatGateway>(object : BriarChatGateway {
            override val isAvailable: Boolean = false
        })
        private val _contactService =
            MutableStateFlow<BriarContactService>(object : BriarContactService {
                override val isAvailable: Boolean = false
            })

        var started = false
            private set
        var stopped = false
            private set

        override val status: StateFlow<BriarRuntimeStatus> = _status
        override val diagnostics: SharedFlow<BriarRuntimeEvent> = _diagnostics
        override val chatGateway: StateFlow<BriarChatGateway> = _chatGateway
        override val contactService: StateFlow<BriarContactService> = _contactService

        override suspend fun ensureStarted() {
            started = true
            _status.value = BriarRuntimeStatus(BriarRuntimePhase.RUNNING)
        }

        override suspend fun stop() {
            stopped = true
            _status.value = BriarRuntimeStatus(BriarRuntimePhase.STOPPED)
        }
    }
}
