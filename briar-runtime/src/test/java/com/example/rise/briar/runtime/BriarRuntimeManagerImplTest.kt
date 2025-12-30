package com.example.rise.briar.runtime

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.crypto.SecretKey
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BriarRuntimeManagerImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `ensureStarted transitions to running`() = runTest {
        val env = briarRuntimeEnvironment()
        val factory = RecordingFactory()
        val manager = BriarRuntimeManagerImpl(
            briarRuntimeEnvironment = env,
            componentFactory = factory,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        manager.ensureStarted()

        assertTrue(factory.created)
        assertEquals(BriarRuntimePhase.RUNNING, manager.status.value.phase)
        assertTrue(factory.handleOpened)
        assertEquals(factory.chatGateway, manager.chatGateway.value)
        assertEquals(factory.contactService, manager.contactService.value)
    }

    @Test
    fun `stop resets to stopped`() = runTest {
        val env = briarRuntimeEnvironment()
        val factory = RecordingFactory()
        val manager = BriarRuntimeManagerImpl(
            briarRuntimeEnvironment = env,
            componentFactory = factory,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        manager.ensureStarted()
        manager.stop()

        assertTrue(factory.handleClosed)
        assertEquals(BriarRuntimePhase.STOPPED, manager.status.value.phase)
        assertEquals(NoOpBriarChatGateway, manager.chatGateway.value)
        assertEquals(NoOpBriarContactService, manager.contactService.value)
    }

    @Test
    fun `failing factory reports failure`() = runTest {
        val env = briarRuntimeEnvironment()
        val factory = object : BriarComponentFactory {
            override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
                throw IllegalStateException("boom")
            }
        }
        val manager = BriarRuntimeManagerImpl(
            briarRuntimeEnvironment = env,
            componentFactory = factory,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        runCatching { manager.ensureStarted() }

        assertEquals(BriarRuntimePhase.FAILED, manager.status.value.phase)
        assertTrue(manager.status.value.lastError is IllegalStateException)
    }

    @Test
    fun `diagnostics emit identity status`() = runTest {
        val env = briarRuntimeEnvironment()
        val factory = RecordingFactory(identityExists = false)
        val manager = BriarRuntimeManagerImpl(
            briarRuntimeEnvironment = env,
            componentFactory = factory,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        manager.ensureStarted()

        val identityEvent = manager.diagnostics.first { it is BriarRuntimeEvent.IdentityStatus } as BriarRuntimeEvent.IdentityStatus
        assertEquals(false, identityEvent.exists)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `ensureStarted does not wait when identity missing after start`() = runTest {
        val env = briarRuntimeEnvironment()
        val factory = object : BriarComponentFactory {
            override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
                return object : BriarRuntimeHandle {
                    override val chatGateway: BriarChatGateway = NoOpBriarChatGateway
                    override val contactService: BriarContactService = NoOpBriarContactService
                    override val hasIdentity: Boolean
                        get() = false
                    override val accountManager: AccountManager =
                        object : AccountManager {
                            override fun hasDatabaseKey(): Boolean = true
                            override fun getDatabaseKey(): SecretKey? = null
                            override fun accountExists(): Boolean = true
                            override fun createAccount(name: String, password: String): Boolean = false
                            override fun deleteAccount() {}
                            override fun signIn(password: String) {}
                            override fun changePassword(oldPassword: String, newPassword: String) {}
                        }

                    override fun markIdentityReady() = Unit
                    override fun signIn(password: String) = Unit
                    override fun startServicesWithCurrentKey(): Boolean = true
                    override fun close() {}
                }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = BriarRuntimeManagerImpl(
            briarRuntimeEnvironment = env,
            componentFactory = factory,
            ioDispatcher = dispatcher
        )

        manager.ensureStarted()

        assertEquals(0, testScheduler.currentTime)
        val identityEvent = manager.diagnostics.first { it is BriarRuntimeEvent.IdentityStatus } as BriarRuntimeEvent.IdentityStatus
        assertEquals(false, identityEvent.exists)
    }

    private fun briarRuntimeEnvironment(): BriarRuntimeEnvironment {
        return BriarRuntimeEnvironment {
            val storage = temporaryFolder.newFolder("briar")
            BriarRuntimeConfig(storageDir = storage)
        }
    }

    private class RecordingFactory(
        private val identityExists: Boolean = true,
    ) : BriarComponentFactory {
        var created: Boolean = false
            private set
        var handleClosed: Boolean = false
            private set
        var handleOpened: Boolean = false
            private set
        val chatGateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
            override suspend fun currentIdentity(): BriarIdentity? = null
            override suspend fun ensureConversation(descriptor: BriarConversationDescriptor): BriarConversation {
                return BriarConversation(descriptor.canonicalConversationId, descriptor.canonicalConversationId)
            }

            override fun observeMessages(conversationId: String) = flowOf(emptyList<BriarMessage>())
            override suspend fun sendMessage(message: BriarOutboundMessage) = Unit
        }
        val contactService = object : BriarContactService {
            override val isAvailable: Boolean = true
            override suspend fun addContactByLink(link: String, alias: String?) = Unit
            override fun observeContacts() = flowOf(emptyList<BriarContact>())
        }

        override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
            created = true
            val briarRuntimeHandle = object : BriarRuntimeHandle {
                override val chatGateway: BriarChatGateway = this@RecordingFactory.chatGateway
                override val contactService: BriarContactService = this@RecordingFactory.contactService
                override val hasIdentity: Boolean = identityExists
                override val accountManager: AccountManager =
                    object : AccountManager {
                        override fun hasDatabaseKey(): Boolean = identityExists
                        override fun getDatabaseKey(): SecretKey? = null
                        override fun accountExists(): Boolean = identityExists
                        override fun createAccount(name: String, password: String): Boolean = identityExists
                        override fun deleteAccount() {}
                        override fun signIn(password: String) {}
                        override fun changePassword(oldPassword: String, newPassword: String) {}
                    }
                override fun markIdentityReady() = Unit
                override fun signIn(password: String) = Unit
                override fun startServicesWithCurrentKey(): Boolean = true

                override fun close() {
                    handleClosed = true
                }
            }
            handleOpened = true
            return briarRuntimeHandle
        }
    }
}
