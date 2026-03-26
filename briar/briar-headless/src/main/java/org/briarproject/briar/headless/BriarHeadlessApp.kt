package org.briarproject.briar.headless

import dagger.Component
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleCoreModule
import org.briarproject.bramble.BrambleJavaEagerSingletons
import org.briarproject.bramble.BrambleJavaModule
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.api.lifecycle.LifecycleManager
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.api.messaging.MessagingManager
import org.briarproject.briar.api.messaging.PrivateMessageFactory
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.BriarCoreModule
import java.security.SecureRandom
import javax.inject.Singleton

@Component(
    modules = [
        BrambleCoreModule::class,
        BrambleJavaModule::class,
        BriarCoreModule::class,
        HeadlessModule::class
    ]
)
@Singleton
internal interface BriarHeadlessApp : BrambleCoreEagerSingletons, BriarCoreEagerSingletons,
    BrambleJavaEagerSingletons, HeadlessEagerSingletons {

    fun getRouter(): Router

    fun getSecureRandom(): SecureRandom

    fun getMessagingManager(): MessagingManager
    fun getConversationManager(): ConversationManager
    fun getContactManager(): ContactManager
    fun getIdentityManager(): IdentityManager
    fun getAccountManager(): AccountManager
    fun getEventBus(): EventBus
    fun getPrivateMessageFactory(): PrivateMessageFactory
    fun getClock(): Clock
    fun getLifecycleManager(): LifecycleManager
}
