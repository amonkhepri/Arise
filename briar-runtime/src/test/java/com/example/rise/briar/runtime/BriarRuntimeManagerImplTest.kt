package com.example.rise.briar.runtime

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BriarRuntimeManagerImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `ensureStarted transitions to running`() = runTest {
        val env = environment()
        val factory = RecordingFactory()
        val manager = BriarRuntimeManagerImpl(
            environment = env,
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
        val env = environment()
        val factory = RecordingFactory()
        val manager = BriarRuntimeManagerImpl(
            environment = env,
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
        val env = environment()
        val factory = object : BriarComponentFactory {
            override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
                throw IllegalStateException("boom")
            }
        }
        val manager = BriarRuntimeManagerImpl(
            environment = env,
            componentFactory = factory,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        runCatching { manager.ensureStarted() }

        assertEquals(BriarRuntimePhase.FAILED, manager.status.value.phase)
        assertTrue(manager.status.value.lastError is IllegalStateException)
    }

    private fun environment(): BriarRuntimeEnvironment {
        return BriarRuntimeEnvironment {
            val storage = temporaryFolder.newFolder("briar")
            BriarRuntimeConfig(storageDir = storage)
        }
    }

    private class RecordingFactory : BriarComponentFactory {
        var created: Boolean = false
            private set
        var handleClosed: Boolean = false
            private set
        var handleOpened: Boolean = false
            private set
        val chatGateway = object : BriarChatGateway {
            override val isAvailable: Boolean = true
        }
        val contactService = object : BriarContactService {
            override val isAvailable: Boolean = true
        }

        override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
            created = true
            val handle = object : BriarRuntimeHandle {
                override val chatGateway: BriarChatGateway = this@RecordingFactory.chatGateway
                override val contactService: BriarContactService = this@RecordingFactory.contactService

                override fun close() {
                    handleClosed = true
                }
            }
            handleOpened = true
            return handle
        }
    }
}
