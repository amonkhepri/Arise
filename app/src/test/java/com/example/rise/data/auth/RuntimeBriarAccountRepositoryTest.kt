package com.example.rise.data.auth

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.briar.runtime.NoOpBriarChatGateway
import com.example.rise.briar.runtime.NoOpBriarContactService
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.TransportRouter
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeBriarAccountRepositoryTest {

    @Test
    fun `createAccount succeeds and resolves identity`() = runTest {
        val runtime = FakeRuntimeManager(createResult = Result.success(true))
        val router = RecordingTransportRouter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        repo.createAccount("alice", "pw")

        assertEquals(1, runtime.createCalls.get())
        assertEquals(1, router.ensureIdentityCalls.get())
    }

    @Test
    fun `createAccount maps database key assertion to exists message`() = runTest {
        val runtime = FakeRuntimeManager(createResult = Result.failure(AssertionError("database key")))
        val router = RecordingTransportRouter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        try {
            repo.createAccount("alice", "pw")
            throw AssertionError("Expected failure")
        } catch (error: IllegalStateException) {
            assertEquals("Account already exists for this nickname. Please sign in.", error.message)
        }
    }

    @Test
    fun `signIn succeeds and resolves identity`() = runTest {
        val runtime = FakeRuntimeManager(signInResult = Result.success(true))
        val router = RecordingTransportRouter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        repo.signIn("alice", "pw")

        assertEquals(1, runtime.signInCalls.get())
        assertEquals(1, router.ensureIdentityCalls.get())
    }

    @Test
    fun `signIn fails when identity display name does not match nickname`() = runTest {
        val runtime = FakeRuntimeManager(signInResult = Result.success(true))
        val router = RecordingTransportRouter(
            identityResult = Result.success(CanonicalIdentity(id = "self", displayName = "bob"))
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        try {
            repo.signIn("alice", "pw")
            throw AssertionError("Expected mismatch failure")
        } catch (error: IllegalStateException) {
            assertEquals("Nickname does not match existing account", error.message)
        }
    }

    @Test
    fun `signIn fails when runtime returns false`() = runTest {
        val runtime = FakeRuntimeManager(signInResult = Result.success(false))
        val router = RecordingTransportRouter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        try {
            repo.signIn("alice", "pw")
            throw AssertionError("Expected failure")
        } catch (error: IllegalStateException) {
            assertEquals("Failed to sign in to Briar account", error.message)
        }
    }

    @Test
    fun `signIn surfaces identity errors`() = runTest {
        val runtime = FakeRuntimeManager(signInResult = Result.success(true))
        val router = RecordingTransportRouter(identityResult = Result.failure(IllegalStateException("no identity")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RuntimeBriarAccountRepository(runtime, router, ioDispatcher = dispatcher)

        try {
            repo.signIn("alice", "pw")
            throw AssertionError("Expected failure")
        } catch (error: IllegalStateException) {
            assertEquals("no identity", error.message)
        }
        assertEquals(1, router.ensureIdentityCalls.get())
    }
}

private class FakeRuntimeManager(
    var createResult: Result<Boolean> = Result.success(true),
    var signInResult: Result<Boolean> = Result.success(true),
) : BriarRuntimeManager {
    override val status: StateFlow<BriarRuntimeStatus> = MutableStateFlow(BriarRuntimeStatus.stopped)
    override val diagnostics: kotlinx.coroutines.flow.SharedFlow<BriarRuntimeEvent> = kotlinx.coroutines.flow.MutableSharedFlow()
    override val chatGateway: StateFlow<BriarChatGateway> = MutableStateFlow(NoOpBriarChatGateway)
    override val contactService: StateFlow<BriarContactService> = MutableStateFlow(NoOpBriarContactService)

    val createCalls = AtomicInteger(0)
    val signInCalls = AtomicInteger(0)

    override suspend fun ensureStarted() = Unit

    override suspend fun createAccount(name: String, password: String): Boolean {
        createCalls.incrementAndGet()
        return createResult.getOrThrow()
    }

    override suspend fun signIn(password: String): Boolean {
        signInCalls.incrementAndGet()
        return signInResult.getOrThrow()
    }

    override suspend fun stop() = Unit
}

private class RecordingTransportRouter(
    val identityResult: Result<CanonicalIdentity> = Result.success(CanonicalIdentity(id = "self", displayName = "alice")),
) : TransportRouter {
    override val currentIdentity: kotlinx.coroutines.flow.Flow<CanonicalIdentity> = kotlinx.coroutines.flow.emptyFlow()
    val ensureIdentityCalls = AtomicInteger(0)

    override suspend fun ensureCurrentIdentity(): CanonicalIdentity {
        ensureIdentityCalls.incrementAndGet()
        return identityResult.getOrThrow()
    }

    override suspend fun ensureConversation(otherIdentity: CanonicalIdentity) = throw UnsupportedOperationException()
    override fun observeConversation(conversationId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.rise.transport.router.CanonicalMessage>>()
    override suspend fun sendMessage(message: com.example.rise.transport.router.ConnectorOutboundMessage) = Unit
    override suspend fun reset() = Unit
}
