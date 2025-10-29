package com.example.rise.transport.router

import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultConnectorRegistryTest {

    private val firestoreConnector = StubConnector(TransportId.FIRESTORE)
    private val briarConnector = StubConnector(TransportId.BRIAR)
    private val telegramConnector = StubConnector(TransportId.TELEGRAM)

    @Test
    fun `connectorFor returns connector matching transport`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        assertSame(firestoreConnector, registry.connectorFor(TransportId.FIRESTORE))
        assertSame(briarConnector, registry.connectorFor(TransportId.BRIAR))
        assertEquals(null, registry.connectorFor(TransportId.TELEGRAM))
    }

    @Test
    fun `primaryFor returns firestore when mode is FIRESTORE`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        assertSame(firestoreConnector, registry.primaryFor(BriarTransportMode.FIRESTORE))
    }

    @Test
    fun `primaryFor returns briar when mode is HYBRID`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        assertSame(briarConnector, registry.primaryFor(BriarTransportMode.HYBRID))
    }

    @Test(expected = IllegalStateException::class)
    fun `primaryFor throws when no connector available for mode`() {
        val registry = DefaultConnectorRegistry(emptySet())

        registry.primaryFor(BriarTransportMode.BRIAR_ONLY)
    }

    @Test
    fun `primaryFor falls back to available connector when preferred missing`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector))

        assertSame(firestoreConnector, registry.primaryFor(BriarTransportMode.HYBRID))
        assertSame(firestoreConnector, registry.primaryFor(BriarTransportMode.BRIAR_ONLY))
    }

    @Test
    fun `mirrorsFor returns empty list for FIRESTORE mode`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        assertTrue(registry.mirrorsFor(BriarTransportMode.FIRESTORE).isEmpty())
    }

    @Test
    fun `mirrorsFor returns firestore when mode is HYBRID`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        val mirrors = registry.mirrorsFor(BriarTransportMode.HYBRID)

        assertEquals(listOf(firestoreConnector), mirrors)
    }

    @Test
    fun `mirrorsFor returns empty list for BRIAR_ONLY`() {
        val registry = DefaultConnectorRegistry(setOf(firestoreConnector, briarConnector))

        assertTrue(registry.mirrorsFor(BriarTransportMode.BRIAR_ONLY).isEmpty())
    }

    private class StubConnector(override val transport: TransportId) : TransportConnector {
        override val status: Flow<ConnectorStatus> = MutableStateFlow(ConnectorStatus.ACTIVE)
        override suspend fun currentIdentity(): CanonicalIdentity =
            error("Not used in this test")
        override suspend fun ensureConversation(conversation: CanonicalConversation): String =
            error("Not used in this test")
        override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> =
            error("Not used in this test")
        override suspend fun  sendMessage(message: ConnectorOutboundMessage) =
            error("Not used in this test")
    }
}
