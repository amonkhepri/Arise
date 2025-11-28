package com.example.rise.briar.runtime

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import org.briarproject.bramble.api.db.DbException
import org.briarproject.bramble.api.event.Event
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.event.EventListener
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent
import org.briarproject.bramble.api.sync.event.MessagesSentEvent
import org.briarproject.bramble.api.sync.event.MessagesAckedEvent
import org.briarproject.briar.api.messaging.MessagingManager
import org.briarproject.briar.api.messaging.PrivateMessageFactory

/**
 * Minimal chat gateway that pipes Briar's messaging stack into the transport
 * abstractions. The mapping assumes participant IDs are Briar contact IDs
 * encoded as integers in string form.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealBriarChatGateway(
    private val readiness: StateFlow<BriarReadinessStatus>,
    private val messagingManager: MessagingManager,
    private val conversationManager: ConversationManager,
    private val privateMessageFactory: PrivateMessageFactory,
    private val identityManager: IdentityManager,
    private val contactManager: ContactManager,
    private val eventBus: EventBus,
    private val clock: Clock,
) : BriarChatGateway, EventListener {

    private val conversations = ConcurrentHashMap<String, MutableStateFlow<List<BriarMessage>>>()
    private val conversationContacts = ConcurrentHashMap<String, Int>()

    init {
        eventBus.addListener(this)
    }

    override val isAvailable: Boolean
        get() = readiness.value is BriarReadinessStatus.Ready

    override fun availability() =
        readiness
            .map { it is BriarReadinessStatus.Ready }
            .distinctUntilChanged()

    override suspend fun currentIdentity(): BriarIdentity? = try {
        val author = identityManager.localAuthor
        BriarIdentity(
            id = author.id.toString(),
            displayName = author.name,
        )
    } catch (_: Exception) {
        null
    }

    override suspend fun ensureConversation(
        descriptor: BriarConversationDescriptor
    ): BriarConversation {
        require(readiness.value is BriarReadinessStatus.Ready) { "Briar runtime is not ready" }
        val contactId = descriptor.participantIds.singleOrNull()?.toIntOrNull()
            ?: throw IllegalArgumentException("Expected exactly one numeric Briar contact id")
        val contact = org.briarproject.bramble.api.contact.ContactId(contactId)
        val groupId = messagingManager.getConversationId(contact)
        val convKey = groupId.toString()
        val flow = conversations.getOrPut(convKey) { MutableStateFlow(emptyList()) }
        conversationContacts[convKey] = contactId
        // Seed with current history if any
        try {
            val headers = conversationManager.getMessageHeaders(contact)
            val messages = headers.mapNotNull { header ->
                val body = messagingManager.getMessageText(header.id) ?: return@mapNotNull null
                BriarMessage(
                    messageId = header.id.toString(),
                    conversationId = convKey,
                    senderId = contactId.toString(),
                    senderName = contactName(contactId),
                    recipientIds = setOf(localIdentityId()),
                    body = body,
                    timestamp = Instant.ofEpochMilli(header.timestamp)
                )
            }
            flow.value = messages
        } catch (_: DbException) {
            // ignore; fallback to empty history
        }

        return BriarConversation(
            canonicalId = descriptor.canonicalConversationId,
            transportConversationId = convKey,
        )
    }

    override fun observeMessages(conversationId: String): Flow<List<BriarMessage>> {
        val messages = conversations.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.asStateFlow()
        return readiness.flatMapLatest { status ->
            if (status is BriarReadinessStatus.Ready) messages else flowOf(emptyList())
        }
    }

    override suspend fun sendMessage(message: BriarOutboundMessage) {
        require(readiness.value is BriarReadinessStatus.Ready) { "Briar runtime is not ready" }
        val contactId = message.recipientIds.firstOrNull()?.toIntOrNull()
            ?: throw IllegalArgumentException("Briar contact id missing or not numeric")
        val groupId = messagingManager.getConversationId(org.briarproject.bramble.api.contact.ContactId(contactId))
        val now = clock.currentTimeMillis()
        val pm = privateMessageFactory.createLegacyPrivateMessage(groupId, now, message.body)
        messagingManager.addLocalMessage(pm)
        // Optimistically echo the outbound message so observers see it immediately.
        val flow = conversations.getOrPut(groupId.toString()) { MutableStateFlow(emptyList()) }
        val outbound = BriarMessage(
            messageId = "local-${now}",
            conversationId = groupId.toString(),
            senderId = localIdentityId(),
            senderName = localDisplayName(),
            recipientIds = setOf(contactId.toString()),
            body = message.body,
            timestamp = Instant.ofEpochMilli(now)
        )
        flow.value += outbound
    }

    override fun eventOccurred(e: Event) {
        when (e) {
            is ConversationMessageReceivedEvent<*> -> handleIncomingMessage(e)
            is MessagesSentEvent -> handleMessagesSent(e)
            is MessagesAckedEvent -> handleMessagesAcked(e)
        }
    }

    private fun handleIncomingMessage(e: ConversationMessageReceivedEvent<*>) {
        val groupId = e.messageHeader.groupId.toString()
        val body = try {
            messagingManager.getMessageText(e.messageHeader.id)
        } catch (_: DbException) {
            null
        } ?: return
        val sender = e.contactId.int.toString()
        val msg = BriarMessage(
            messageId = e.messageHeader.id.toString(),
            conversationId = groupId,
            senderId = sender,
            senderName = contactName(e.contactId.int),
            recipientIds = setOf(localIdentityId()),
            body = body,
            timestamp = Instant.ofEpochMilli(e.messageHeader.timestamp)
        )
        val flow = conversations.getOrPut(groupId) { MutableStateFlow(emptyList()) }
        flow.value += msg
    }

    private fun handleMessagesSent(event: MessagesSentEvent) {
        val contactId = event.contactId.int
        val conversationId = conversationContacts.entries
            .firstOrNull { it.value == contactId }?.key ?: return
        val flow = conversations.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.mapIndexed { index, message ->
            if (message.messageId.startsWith("local-") && message.recipientIds.contains(contactId.toString())) {
                message.copy(messageId = event.messageIds.first().toString())
            } else message
        }
    }

    private fun handleMessagesAcked(event: MessagesAckedEvent) {
        val contactId = event.contactId.int
        val conversationId = conversationContacts.entries
            .firstOrNull { it.value == contactId }?.key ?: return
        val flow = conversations.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.map { message ->
            if (event.messageIds.any { it.toString() == message.messageId }) {
                message // could add status info here later
            } else message
        }
    }

    private fun localIdentityId(): String =
        runCatching { identityManager.localAuthor.id.toString() }.getOrDefault("")
    private fun localDisplayName(): String =
        runCatching { identityManager.localAuthor.name }.getOrDefault("")
    private fun contactName(contactId: Int): String =
        runCatching {
            contactManager.getContact(org.briarproject.bramble.api.contact.ContactId(contactId)).let { c ->
                c.alias ?: c.author.name
            }
        }.getOrDefault(contactId.toString())

    fun close() {
        eventBus.removeListener(this)
        conversations.clear()
        conversationContacts.clear()
    }
}
