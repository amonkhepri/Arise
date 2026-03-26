package com.example.rise.transport.connectors

import com.example.rise.auth.AuthStateHandle
import com.example.rise.auth.AuthenticationService
import com.example.rise.data.chat.CachedChatMessage
import com.example.rise.data.chat.ChatLocalCache
import com.example.rise.data.firestore.ChatRemoteDataSource
import com.example.rise.data.firestore.UserRemoteDataSource
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.AccountConnector
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.CapabilityDescriptor
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorContact
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.ConnectorOutboundMessage
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.transport.router.ConnectorTelemetryEvent
import com.example.rise.transport.router.ConnectorTelemetrySink
import com.example.rise.transport.router.PresenceStatus
import com.example.rise.transport.router.TransportConnector
import com.example.rise.transport.router.TransportConversationId
import com.example.rise.transport.router.TransportId
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class FirestoreConnector(
    private val authService: AuthenticationService,
    private val chatRemoteDataSource: ChatRemoteDataSource,
    private val userRemoteDataSource: UserRemoteDataSource,
    private val transportBridge: TransportRuntimeBridge,
    private val localCache: ChatLocalCache,
    private val telemetrySink: ConnectorTelemetrySink,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransportConnector, AccountConnector {

    private val _status = MutableStateFlow(ConnectorStatus.STARTING)
    override val status: StateFlow<ConnectorStatus> = _status.asStateFlow()
    private val _lifecycle = MutableStateFlow(ConnectorLifecycleState.INITIAL)
    override val lifecycle: StateFlow<ConnectorLifecycleState> = _lifecycle.asStateFlow()
    private val _capabilities = MutableStateFlow(
        ConnectorCapabilities(
            mapOf(
                "messages" to CapabilityDescriptor(1, mapOf("supportsAttachments" to "false", "enabled" to "true")),
                "contacts" to CapabilityDescriptor(
                    1,
                    mapOf(
                        "presence" to PresenceStatus.UNKNOWN.name,
                        "presenceField" to "users.presence",
                        "presenceFallback" to PresenceStatus.UNKNOWN.name,
                    ),
                ),
                "account" to CapabilityDescriptor(1, mapOf("editableFields" to "name,bio,profilePicturePath")),
                "notifications" to CapabilityDescriptor(1, mapOf("push" to "true")),
            )
        )
    )
    override val capabilities: StateFlow<ConnectorCapabilities> = _capabilities.asStateFlow()
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
                transition(ConnectorLifecycleState.AUTHENTICATING)
                if (cachedIdentity != null || channelCache.isNotEmpty() || messagesCache.isNotEmpty()) {
                    clearUserCaches(cachedIdentity?.id)
                }
            } else {
                if (cachedIdentity != null && cachedIdentity.id != user.id) {
                    clearUserCaches(cachedIdentity.id)
                }
                transition(ConnectorLifecycleState.READY)
            }
        }
        transition(
            if (authService.currentUser() == null) ConnectorLifecycleState.AUTHENTICATING
            else ConnectorLifecycleState.READY
        )
    }

    override suspend fun currentIdentity(): CanonicalIdentity {
        transportBridge.requireFirestore("FirestoreConnector#currentIdentity")
        val authUser = authService.currentUser() ?: run {
            transition(ConnectorLifecycleState.AUTHENTICATING)
            throw IllegalStateException("User must be signed in")
        }
        currentIdentity?.let { cached ->
            if (cached.id == authUser.id) {
                transition(ConnectorLifecycleState.READY)
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
        ).also {
            currentIdentity = it
            transition(ConnectorLifecycleState.READY)
        }
    }

    override suspend fun ensureConversation(conversation: com.example.rise.transport.router.CanonicalConversation): TransportConversationId =
        runWithTelemetry("ensureConversation") {
            transportBridge.requireFirestore("FirestoreConnector#ensureConversation")
            val currentUser = currentIdentity()
            val otherUserId = conversation.participants.firstOrNull { it != currentUser.id }
                ?: throw IllegalArgumentException("Conversation participants must include other user")
            channelCache[otherUserId]?.let { cached ->
                return@runWithTelemetry TransportConversationId(cached, cached)
            }
            val currentUserId = authService.currentUser()?.id ?: throw IllegalStateException("User must be signed in")
            val cachedChannel = withCacheAccess {
                localCache.readChannelId(currentUserId, otherUserId)
            }
            if (cachedChannel != null) {
                channelCache[otherUserId] = cachedChannel
                return@runWithTelemetry TransportConversationId(cachedChannel, cachedChannel)
            }
            val existingId = chatRemoteDataSource.getExistingChannelId(currentUserId, otherUserId)
            if (existingId != null) {
                channelCache[otherUserId] = existingId
                withCacheAccess {
                    localCache.writeChannelId(currentUserId, otherUserId, existingId)
                }
                return@runWithTelemetry TransportConversationId(
                    canonicalId = existingId,
                    transportConversationId = existingId,
                )
            }
            val createdId = chatRemoteDataSource.createChannel(currentUserId, otherUserId)
            channelCache[otherUserId] = createdId
            withCacheAccess {
                localCache.writeChannelId(currentUserId, otherUserId, createdId)
            }
            return@runWithTelemetry TransportConversationId(
                canonicalId = createdId,
                transportConversationId = createdId,
            )
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
            try {
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
                    transition(ConnectorLifecycleState.READY)
                    trySend(connectorMessages)
                }
            } catch (error: Throwable) {
                reportFailure("observeMessages", error)
                throw error
            }
        }
        awaitClose { job.cancel() }
    }

    override fun observeContacts(): Flow<List<ConnectorContact>> {
        return userRemoteDataSource.observeUsers()
            .onStart { transportBridge.requireFirestore("FirestoreConnector#observeContacts") }
            .onEach { transition(ConnectorLifecycleState.READY) }
            .catch { error ->
                reportFailure("observeContacts", error)
                throw error
            }
            .map { snapshots ->
                snapshots.map { snapshot ->
                    ConnectorContact(
                        transport = TransportId.FIRESTORE,
                        transportId = snapshot.id,
                        displayName = snapshot.user.name,
                        canonicalId = snapshot.id,
                        bio = snapshot.user.bio,
                        profilePicturePath = snapshot.user.profilePicturePath,
                        presence = snapshot.user.presence.toPresenceStatus(),
                        registrationTokens = snapshot.user.registrationTokens.toList(),
                    )
                }
            }
    }

    override suspend fun sendMessage(message: ConnectorOutboundMessage) = runWithTelemetry("sendMessage") {
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

    override suspend fun fetchAccountProfile(): User = runWithTelemetry("fetchAccountProfile") {
        transportBridge.requireFirestore("FirestoreConnector#fetchAccountProfile")
        val uid = authService.currentUser()?.id ?: throw IllegalStateException("User must be signed in")
        return@runWithTelemetry userRemoteDataSource.fetchUser(uid)
            ?: throw IllegalStateException("User not found")
    }

    override suspend fun updateAccountProfile(update: AccountConnector.AccountProfileUpdate) = runWithTelemetry("updateAccountProfile") {
        transportBridge.requireFirestore("FirestoreConnector#updateAccountProfile")
        val uid = authService.currentUser()?.id ?: throw IllegalStateException("User must be signed in")
        val patch = mutableMapOf<String, Any>()
        update.name?.let { patch["name"] = it }
        update.bio?.let { patch["bio"] = it }
        update.profilePicturePath?.let { patch["profilePicturePath"] = it }
        if (patch.isEmpty()) return@runWithTelemetry
        userRemoteDataSource.updateUser(uid, patch)
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

    private fun String?.toPresenceStatus(): PresenceStatus {
        val raw = this?.trim().orEmpty()
        if (raw.isEmpty()) {
            return PresenceStatus.UNKNOWN
        }
        return runCatching { PresenceStatus.valueOf(raw.uppercase(Locale.US)) }
            .getOrDefault(PresenceStatus.UNKNOWN)
    }

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
        transition(
            if (authService.currentUser() == null) ConnectorLifecycleState.AUTHENTICATING
            else ConnectorLifecycleState.READY
        )
    }

    private suspend fun <T> withCacheAccess(block: suspend () -> T): T {
        pendingClearJob?.let { job ->
            if (!job.isCompleted) {
                job.join()
            }
        }
        return cacheMutex.withLock { block() }
    }

    private fun transition(state: ConnectorLifecycleState) {
        if (_lifecycle.value == state) {
            return
        }
        _lifecycle.value = state
        _status.value = when (state) {
            ConnectorLifecycleState.READY -> ConnectorStatus.ACTIVE
            ConnectorLifecycleState.DEGRADED,
            ConnectorLifecycleState.FAILED -> ConnectorStatus.ERROR
            ConnectorLifecycleState.AUTHENTICATING,
            ConnectorLifecycleState.HANDSHAKING,
            ConnectorLifecycleState.RETIRING -> ConnectorStatus.STARTING
            ConnectorLifecycleState.INITIAL,
            ConnectorLifecycleState.STOPPED -> ConnectorStatus.INACTIVE
        }
    }

    private suspend fun <T> runWithTelemetry(operation: String, block: suspend () -> T): T {
        return try {
            val result = block()
            transition(ConnectorLifecycleState.READY)
            result
        } catch (error: Throwable) {
            reportFailure(operation, error)
            throw error
        }
    }

    private fun reportFailure(operation: String, error: Throwable) {
        telemetrySink.emit(
            ConnectorTelemetryEvent.Failure(
                transport = transport,
                state = _lifecycle.value,
                error = error,
            )
        )
        Timber.tag(TAG).w(error, "Firestore connector failure during %s", operation)
        transition(ConnectorLifecycleState.DEGRADED)
    }

    companion object {
        private const val TAG = "FirestoreConnector"
    }
}
