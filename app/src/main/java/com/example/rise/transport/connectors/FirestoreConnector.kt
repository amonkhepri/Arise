package com.example.rise.transport.connectors

import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.chat.CachedChatMessage
import com.example.rise.data.chat.ChatLocalCache
import com.example.rise.data.firestore.ChatRemoteDataSource
import com.example.rise.models.TextMessage
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportId
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirestoreConnector(
    private val authService: AuthenticationService,
    private val chatRemoteDataSource: ChatRemoteDataSource,
    private val transportBridge: TransportRuntimeBridge,
    private val localCache: ChatLocalCache,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransportConnector {

    private val _status = MutableStateFlow(ConnectorStatus.ACTIVE)
    override val status: StateFlow<ConnectorStatus> = _status.asStateFlow()
    override val transport: TransportId = TransportId.FIRESTORE

    private val channelCache = ConcurrentHashMap<String, String>()
    private val messagesCache = ConcurrentHashMap<String, List<ConnectorInboundMessage>>()
    private val cacheScope = CoroutineScope(SupervisorJob() + cacheDispatcher)
    private val cacheMutex = Mutex()
    @Volatile private var pendingClearJob: Job? = null

    @Volatile private var currentIdentity: CanonicalIdentity? = null
    private val authStateHandle: AuthStateHandle

    init {
        authStateHandle = authService.addAuthStateListener { user ->
            val cachedIdentity = currentIdentity
            if (user == null) {
                if (cachedIdentity != null || channelCache.isNotEmpty() || messagesCache.isNotEmpty()) {
                    clearUserCaches(cachedIdentity?.id)
                }
            } else if (cachedIdentity != null && cachedIdentity.id != user.id) {
                clearUserCaches(cachedIdentity.id)
            }
        }
    }

    override suspend fun currentIdentity(): CanonicalIdentity {
        transportBridge.requireFirestore("FirestoreConnector#currentIdentity")
        val authUser = authService.currentUser() ?: throw IllegalStateException("User must be signed in")
        currentIdentity?.let { cached ->
            if (cached.id == authUser.id) {
                return cached
            }
            clearUserCaches(cached.id)
        }
        val displayName = chatRemoteDataSource.getUserDisplayName(authUser.id)
            ?.takeIf { it.isNotBlank() }
            ?: authUser.displayName.orEmpty()
        return CanonicalIdentity(
            id = authUser.id,
            displayName = displayName,
        ).also { currentIdentity = it }
    }

    override suspend fun ensureConversation(conversation: com.example.rise.transport.router.CanonicalConversation): String {
        transportBridge.requireFirestore("FirestoreConnector#ensureConversation")
        val currentUser = currentIdentity()
        val otherUserId = conversation.participants.firstOrNull { it != currentUser.id }
            ?: throw IllegalArgumentException("Conversation participants must include other user")
        channelCache[otherUserId]?.let { return it }
        val currentUserId = authService.currentUser()?.id ?: throw IllegalStateException("User must be signed in")
        withCacheAccess {
            localCache.readChannelId(currentUserId, otherUserId)
        }?.let { cached ->
            channelCache[otherUserId] = cached
            return cached
        }
        val existingId = chatRemoteDataSource.getExistingChannelId(currentUserId, otherUserId)
        if (existingId != null) {
            channelCache[otherUserId] = existingId
            withCacheAccess {
                localCache.writeChannelId(currentUserId, otherUserId, existingId)
            }
            return existingId
        }
        return chatRemoteDataSource.createChannel(currentUserId, otherUserId).also {
            channelCache[otherUserId] = it
            withCacheAccess {
                localCache.writeChannelId(currentUserId, otherUserId, it)
            }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<ConnectorInboundMessage>> = callbackFlow {
        transportBridge.requireFirestore("FirestoreConnector#observeMessages")
        val userId = authService.currentUser()?.id
        messagesCache[conversationId]?.let { cached ->
            if (cached.isNotEmpty()) trySend(cached)
        } ?: run {
            if (userId != null) {
                val cached = withCacheAccess {
                    localCache.readMessages(userId, conversationId)
                }
                if (cached.isNotEmpty()) {
                    val connectorMessages = cached.map { it.toInboundMessage(conversationId) }
                    messagesCache[conversationId] = connectorMessages
                    trySend(connectorMessages)
                }
            }
        }
        val job = cacheScope.launch {
            chatRemoteDataSource.observeMessages(conversationId).collect { remoteMessages ->
                val connectorMessages = remoteMessages.map { it.toConnectorMessage(conversationId) }
                messagesCache[conversationId] = connectorMessages
                if (userId != null ) {
                    withCacheAccess {
                        localCache.writeMessages(
                            userId = userId,
                            channelId = conversationId,
                            messages = connectorMessages.map { it.toCachedMessage() },
                        )
                    }
                }
                trySend(connectorMessages)
            }
        }
        awaitClose { job.cancel() }
    }

    override suspend fun sendMessage(message: ConnectorOutboundMessage) {
        transportBridge.requireFirestore("FirestoreConnector#sendMessage")
        val textMessage = TextMessage(
            text = message.body,
            time = message.timestamp,
            senderId = message.senderId,
            recipientId = message.recipientIds.firstOrNull().orEmpty(),
            senderName = message.senderName,
        )
        chatRemoteDataSource.sendMessage(message.conversationId, textMessage)
    }

    private fun ChatRemoteDataSource.RemoteMessage.toConnectorMessage(conversationId: String): ConnectorInboundMessage {
        val remotePayload = payload
        return ConnectorInboundMessage(
            messageId = id,
            conversationId = conversationId,
            senderId = remotePayload.senderId,
            recipientId = remotePayload.recipientId,
            senderName = remotePayload.senderName,
            body = remotePayload.text,
            transport = TransportId.FIRESTORE,
            timestamp = remotePayload.time,
        )
    }

    private fun CachedChatMessage.toInboundMessage(conversationId: String): ConnectorInboundMessage =
        ConnectorInboundMessage(
            messageId = messageId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            body = text,
            transport = TransportId.FIRESTORE,
            timestamp = time,
        )

    private fun ConnectorInboundMessage.toCachedMessage(): CachedChatMessage =
        CachedChatMessage(
            messageId = messageId,
            text = body,
            time = timestamp,
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
        )

    private fun clearUserCaches(previousUserId: String?) {
        channelCache.clear()
        messagesCache.clear()
        if (previousUserId != null) {
            val job = cacheScope.launch(start = CoroutineStart.UNDISPATCHED) {
                cacheMutex.withLock {
                    localCache.clear(previousUserId)
                }
            }
            pendingClearJob = job
            job.invokeOnCompletion { pendingClearJob = null }
        } else {
            pendingClearJob = null
        }
        currentIdentity = null
    }

    private suspend fun <T> withCacheAccess(block: suspend () -> T): T {
        pendingClearJob?.let { job ->
            if (!job.isCompleted) {
                job.join()
            }
        }
        return cacheMutex.withLock { block() }
    }
}
