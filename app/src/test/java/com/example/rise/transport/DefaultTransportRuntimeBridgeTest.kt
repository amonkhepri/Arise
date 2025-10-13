package com.example.rise.transport

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TransportModeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTransportRuntimeBridgeTest {

    @Test
    fun `requireFirestore allows default mode`() = runBlocking {
        val dispatcher = Dispatchers.Unconfined
        val provider = FakeTransportModeProvider(BriarTransportMode.FIRESTORE)
        val bridge = DefaultTransportRuntimeBridge(provider, dispatcher)

        yield()

        bridge.requireFirestore("test call") // should not throw
    }

    @Test
    fun `requireFirestore does not throw on non Firestore modes`() = runBlocking {
        val dispatcher = Dispatchers.Unconfined
        val provider = FakeTransportModeProvider(BriarTransportMode.FIRESTORE)
        val bridge = DefaultTransportRuntimeBridge(provider, dispatcher)

        provider.setMode(BriarTransportMode.HYBRID)
        yield()

        bridge.requireFirestore("test call")
        assertEquals(BriarTransportMode.HYBRID, bridge.currentMode.value)
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
}
