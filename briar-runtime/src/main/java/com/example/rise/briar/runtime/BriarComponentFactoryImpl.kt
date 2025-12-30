@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.example.rise.briar.runtime

import java.io.File
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleJavaEagerSingletons
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.system.Clock
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.api.messaging.MessagingManager
import org.briarproject.briar.api.messaging.PrivateMessageFactory
import org.briarproject.briar.headless.BriarHeadlessApp
import org.briarproject.briar.headless.DaggerBriarHeadlessApp
import org.briarproject.briar.headless.HeadlessEagerSingletons
import org.briarproject.briar.headless.HeadlessModule
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState
import org.briarproject.bramble.api.lifecycle.LifecycleManager

/**
 * Production factory that wires the upstream Briar Dagger component packaged inside
 * the `briar-headless` composite build.
 */
class BriarComponentFactoryImpl : BriarComponentFactory {

    override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
        ensureStorageDirectory(config.storageDir)
        val component = DaggerBriarHeadlessApp.builder()
            .headlessModule(HeadlessModule(config.storageDir))
            .build()

        initialiseEagerSingletons(component)

        return BriarRuntimeHandleImpl(component)
    }

    private fun ensureStorageDirectory(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun initialiseEagerSingletons(component: BriarHeadlessApp) {
        BrambleCoreEagerSingletons.Helper.injectEagerSingletons(component)
        BrambleJavaEagerSingletons.Helper.injectEagerSingletons(component)
        BriarCoreEagerSingletons.Helper.injectEagerSingletons(component)
        HeadlessEagerSingletons.Helper.injectEagerSingletons(component)
    }

    private class BriarRuntimeHandleImpl(
        component: BriarHeadlessApp
    ) : BriarRuntimeHandle {

        private val readinessTracker = BriarReadinessTracker()
        private val eventBus: EventBus = component.getEventBus()
        private val messagingManager: MessagingManager = component.getMessagingManager()
        private val conversationManager: ConversationManager = component.getConversationManager()
        private val contactManager: ContactManager = component.getContactManager()
        private val identityManager: IdentityManager = component.getIdentityManager()
        private val pmFactory: PrivateMessageFactory = component.getPrivateMessageFactory()
        private val clock: Clock = component.getClock()
        private val lifecycleManager: LifecycleManager = component.getLifecycleManager()
        private val runtimeAccountManager: AccountManager = component.getAccountManager()
        private val lifecycleListener = object : org.briarproject.bramble.api.event.EventListener {
            override fun eventOccurred(e: org.briarproject.bramble.api.event.Event) {
                if (e is LifecycleEvent) {
                    when (e.lifecycleState) {
                        LifecycleState.RUNNING -> {
                            println("$TAG: Lifecycle event: RUNNING")
                            markReadyIfIdentityPresent()
                        }
                        LifecycleState.STOPPING,
                        LifecycleState.STOPPED -> {
                            println("$TAG: Lifecycle event: ${e.lifecycleState}")
                            readinessTracker.markNotReady()
                        }
                        else -> {}
                    }
                }
            }
        }

        override val chatGateway: BriarChatGateway =
            RealBriarChatGateway(
                readiness = readinessTracker.flow,
                messagingManager = messagingManager,
                conversationManager = conversationManager,
                privateMessageFactory = pmFactory,
                identityManager = identityManager,
                contactManager = contactManager,
                eventBus = eventBus,
                clock = clock
            )

        override val contactService: BriarContactService =
            RealBriarContactService(readinessTracker.flow, contactManager, eventBus)

        override val hasIdentity: Boolean
            get() = runCatching { identityManager.localAuthor }.isSuccess

        override val accountManager: AccountManager
            get() = runtimeAccountManager

        override fun markIdentityReady() {
            readinessTracker.markReady()
        }

        override fun signIn(password: String) {
            runtimeAccountManager.signIn(password)
        }

        override fun startServicesWithCurrentKey(): Boolean {
            val key = runtimeAccountManager.databaseKey ?: return false
            val result = runCatching { lifecycleManager.startServices(key) }.getOrNull()
            if (result == LifecycleManager.StartResult.SUCCESS || result == LifecycleManager.StartResult.ALREADY_RUNNING) {
                runCatching { lifecycleManager.waitForStartup() }
                return true
            }
            return false
        }

        init {
            eventBus.addListener(lifecycleListener)
            // If lifecycle is already running and identity exists, mark readiness; otherwise wait for events.
            if (lifecycleManager.lifecycleState == LifecycleState.RUNNING) {
                markReadyIfIdentityPresent()
            } else {
                println("$TAG: Lifecycle state is ${lifecycleManager.lifecycleState}; marking not ready")
                readinessTracker.markNotReady()
            }
        }

        override fun close() {
            (chatGateway as? RealBriarChatGateway)?.close()
            (contactService as? RealBriarContactService)?.close()
            readinessTracker.markNotReady()
            eventBus.removeListener(lifecycleListener)
            runCatching {
                lifecycleManager.stopServices()
                lifecycleManager.waitForShutdown()
            }
        }

        private fun markReadyIfIdentityPresent() {
            runCatching { identityManager.localAuthor }
                .onSuccess {
                    println("$TAG: Loaded local Briar author; marking ready")
                    readinessTracker.markReady()
                }
                .onFailure { error ->
                    println("$TAG: Failed to load local Briar author; staying not ready - ${error.message}")
                    readinessTracker.markNotReady()
                }
        }

        companion object {
            private const val TAG = "BriarRuntimeHandle"
        }
    }
}
