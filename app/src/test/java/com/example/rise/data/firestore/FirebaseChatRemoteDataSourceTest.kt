package com.example.rise.data.firestore

import app.cash.turbine.test
import com.example.rise.models.ChatChannel
import com.example.rise.models.TextMessage
import com.example.rise.util.MainDispatcherRule
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseChatRemoteDataSourceTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var firestore: FirebaseFirestore
    private lateinit var chatChannels: CollectionReference
    private lateinit var channelDocument: DocumentReference
    private lateinit var messagesCollection: CollectionReference
    private lateinit var orderedQuery: Query
    private lateinit var listenerRegistration: ListenerRegistration
    private lateinit var listenerSlot: CapturingSlot<EventListener<QuerySnapshot>>

    private lateinit var dataSource: FirebaseChatRemoteDataSource

    @Before
    fun setUp() {
        firestore = mockk()
        chatChannels = mockk()
        channelDocument = mockk()
        messagesCollection = mockk()
        orderedQuery = mockk()
        listenerRegistration = mockk()
        listenerSlot = slot()

        every { firestore.collection("chatChannels") } returns chatChannels
        every { chatChannels.document(any()) } returns channelDocument
        every { chatChannels.document() } returns channelDocument
        every { channelDocument.collection("messages") } returns messagesCollection
        every { messagesCollection.orderBy("time") } returns orderedQuery
        every { orderedQuery.addSnapshotListener(capture(listenerSlot)) } returns listenerRegistration
        every { listenerRegistration.remove() } returns Unit

        dataSource = FirebaseChatRemoteDataSource(firestore)
    }

    @Test
    fun observeMessages_emitsSnapshotPayloads() = runTest {
        val conversationId = "conversation"
        val flow = dataSource.observeMessages(conversationId)

        flow.test {
            val listener = listenerSlot.captured
            val snapshot = mockk<QuerySnapshot>()
            val document1 = mockk<DocumentSnapshot>()
            val document2 = mockk<DocumentSnapshot>()
            val message1 = TextMessage(
                text = "hello",
                time = Date(1000),
                senderId = "alice",
                recipientId = "bob",
                senderName = "Alice"
            )
            val message2 = TextMessage(
                text = "world",
                time = Date(2000),
                senderId = "bob",
                recipientId = "alice",
                senderName = "Bob"
            )
            every { snapshot.toObjects(TextMessage::class.java) } returns listOf(message1, message2)
            every { snapshot.documents } returns listOf(document1, document2)
            every { document1.id } returns "doc-1"
            every { document2.id } returns "doc-2"

            listener.onEvent(snapshot, null)

            val emitted = awaitItem()
            assertEquals(
                listOf(
                    ChatRemoteDataSource.RemoteMessage("doc-1", message1),
                    ChatRemoteDataSource.RemoteMessage("doc-2", message2)
                ),
                emitted
            )

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { listenerRegistration.remove() }
    }

    @Test
    fun observeMessages_emitsEmptyListAndClosesOnPermissionDenied() = runTest {
        val conversationId = "conversation"
        val flow = dataSource.observeMessages(conversationId)

        flow.test {
            val listener = listenerSlot.captured
            val permissionDenied = FirebaseFirestoreException(
                "Permission denied",
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            )

            listener.onEvent(null, permissionDenied)

            val emitted = awaitItem()
            assertEquals(emptyList<ChatRemoteDataSource.RemoteMessage>(), emitted)
            awaitComplete()
        }

        verify(exactly = 1) { listenerRegistration.remove() }
    }

    @Test
    fun getUserDisplayName_returnsName() = runTest {
        val usersCollection: CollectionReference = mockk()
        val userDocument: DocumentReference = mockk()
        val snapshot: DocumentSnapshot = mockk()

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("user-id") } returns userDocument
        val getTask: Task<DocumentSnapshot> = Tasks.forResult(snapshot)
        every { userDocument.get() } returns getTask
        every { snapshot.getString("name") } returns "Alice"

        val name = dataSource.getUserDisplayName("user-id")

        assertEquals("Alice", name)
    }

    @Test
    fun getUserDisplayName_returnsNullWhenMissing() = runTest {
        val usersCollection: CollectionReference = mockk()
        val userDocument: DocumentReference = mockk()
        val snapshot: DocumentSnapshot = mockk()

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("user-id") } returns userDocument
        val getTask: Task<DocumentSnapshot> = Tasks.forResult(snapshot)
        every { userDocument.get() } returns getTask
        every { snapshot.getString("name") } returns null

        val name = dataSource.getUserDisplayName("user-id")

        assertNull(name)
    }

    @Test
    fun getExistingChannelId_returnsValue() = runTest {
        val usersCollection: CollectionReference = mockk()
        val currentUserDocument: DocumentReference = mockk()
        val engagedChannels: CollectionReference = mockk()
        val otherUserDocument: DocumentReference = mockk()
        val snapshot: DocumentSnapshot = mockk()

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("current-user") } returns currentUserDocument
        every { currentUserDocument.collection("engagedChatChannels") } returns engagedChannels
        every { engagedChannels.document("other-user") } returns otherUserDocument
        val getTask: Task<DocumentSnapshot> = Tasks.forResult(snapshot)
        every { otherUserDocument.get() } returns getTask
        every { snapshot.getString("channelId") } returns "channel-123"

        val channelId = dataSource.getExistingChannelId("current-user", "other-user")

        assertEquals("channel-123", channelId)
    }

    @Test
    fun getExistingChannelId_returnsNullWhenMissing() = runTest {
        val usersCollection: CollectionReference = mockk()
        val currentUserDocument: DocumentReference = mockk()
        val engagedChannels: CollectionReference = mockk()
        val otherUserDocument: DocumentReference = mockk()
        val snapshot: DocumentSnapshot = mockk()

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("current-user") } returns currentUserDocument
        every { currentUserDocument.collection("engagedChatChannels") } returns engagedChannels
        every { engagedChannels.document("other-user") } returns otherUserDocument
        val getTask: Task<DocumentSnapshot> = Tasks.forResult(snapshot)
        every { otherUserDocument.get() } returns getTask
        every { snapshot.getString("channelId") } returns null

        val channelId = dataSource.getExistingChannelId("current-user", "other-user")

        assertNull(channelId)
    }

    @Test
    fun createChannel_createsBothUserEntries() = runTest {
        val newChannelId = "new-channel"
        val usersCollection: CollectionReference = mockk()
        val currentUserDocument: DocumentReference = mockk()
        val otherUserDocument: DocumentReference = mockk()
        val currentUserChannels: CollectionReference = mockk()
        val otherUserChannels: CollectionReference = mockk()
        val currentUserChannelDocument: DocumentReference = mockk()
        val otherUserChannelDocument: DocumentReference = mockk()
        val channelPayload = slot<ChatChannel>()
        val currentUserPayload = slot<Map<String, String>>()
        val otherUserPayload = slot<Map<String, String>>()

        every { channelDocument.id } returns newChannelId
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("current-user") } returns currentUserDocument
        every { usersCollection.document("other-user") } returns otherUserDocument
        every { currentUserDocument.collection("engagedChatChannels") } returns currentUserChannels
        every { otherUserDocument.collection("engagedChatChannels") } returns otherUserChannels
        every { currentUserChannels.document("other-user") } returns currentUserChannelDocument
        every { otherUserChannels.document("current-user") } returns otherUserChannelDocument
        every { channelDocument.set(capture(channelPayload)) } returns Tasks.forResult<Void>(null)
        every { currentUserChannelDocument.set(capture(currentUserPayload)) } returns Tasks.forResult<Void>(null)
        every { otherUserChannelDocument.set(capture(otherUserPayload)) } returns Tasks.forResult<Void>(null)

        val channelId = dataSource.createChannel("current-user", "other-user")

        assertEquals(newChannelId, channelId)

        assertEquals(listOf("current-user", "other-user"), channelPayload.captured.userIds)
        assertEquals(mapOf("channelId" to newChannelId), currentUserPayload.captured)
        assertEquals(mapOf("channelId" to newChannelId), otherUserPayload.captured)
    }

    @Test
    fun sendMessage_addsMessageToConversation() = runTest {
        val conversationId = "conversation"
        val message = TextMessage(
            text = "hello",
            time = Date(1000),
            senderId = "alice",
            recipientId = "bob",
            senderName = "Alice"
        )
        val otherUserDocument: DocumentReference = mockk()
        val messagesCollection: CollectionReference = mockk()
        val documentReferenceTask: Task<DocumentReference> = Tasks.forResult(otherUserDocument)

        every { chatChannels.document(conversationId) } returns channelDocument
        every { channelDocument.collection("messages") } returns messagesCollection
        every { messagesCollection.add(message) } returns documentReferenceTask

        dataSource.sendMessage(conversationId, message)

        verify(exactly = 1) { messagesCollection.add(message) }
    }

    @Test
    fun observeMessages_filtersMissingPayloads() = runTest {
        val conversationId = "conversation"
        val flow = dataSource.observeMessages(conversationId)

        flow.test {
            val listener = listenerSlot.captured
            val snapshot = mockk<QuerySnapshot>()
            val document1 = mockk<DocumentSnapshot>()
            val document2 = mockk<DocumentSnapshot>()
            val message1 = TextMessage(
                text = "hello",
                time = Date(1000),
                senderId = "alice",
                recipientId = "bob",
                senderName = "Alice"
            )

            every { snapshot.toObjects(TextMessage::class.java) } returns listOf(message1)
            every { snapshot.documents } returns listOf(document1, document2)
            every { document1.id } returns "doc-1"
            every { document2.id } returns "doc-2"

            listener.onEvent(snapshot, null)

            val emitted = awaitItem()
            assertEquals(
                listOf(ChatRemoteDataSource.RemoteMessage("doc-1", message1)),
                emitted
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}
