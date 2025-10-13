package com.example.rise.data.myaccount

import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseMyAccountRepositoryTest {

    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val firestore: FirebaseFirestore = mockk()
    private val collection: CollectionReference = mockk()
    private val document: DocumentReference = mockk()
    private val transportBridge: TransportRuntimeBridge = mockk(relaxed = true)

    private fun TestScope.repository() = FirebaseMyAccountRepository(
        auth = auth,
        firestore = firestore,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        transportBridge = transportBridge,
    )

    @Test
    fun `fetchCurrentUser returns user when snapshot exists`() = runTest {
        val uid = "uid-123"
        val firebaseUser = mockk<FirebaseUser> {
            every { this@mockk.uid } returns uid
        }
        val expectedUser = User(name = "Ada", bio = "Moon lover", profilePicturePath = null, registrationTokens = mutableListOf())
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(User::class.java) } returns expectedUser
        }
        val getTask = TaskCompletionSource<DocumentSnapshot>().apply { setResult(snapshot) }.task

        every { auth.currentUser } returns firebaseUser
        every { firestore.collection("users") } returns collection
        every { collection.document(uid) } returns document
        every { document.get() } returns getTask

        val result = repository().fetchCurrentUser()

        assertEquals(expectedUser, result)
    }

    @Test
    fun `fetchCurrentUser throws when current user is missing`() = runTest {
        every { auth.currentUser } returns null

        try {
            repository().fetchCurrentUser()
            fail("Expected IllegalStateException when no user is authenticated")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `fetchCurrentUser throws when snapshot has no user`() = runTest {
        val uid = "uid-123"
        val firebaseUser = mockk<FirebaseUser> {
            every { this@mockk.uid } returns uid
        }
        val snapshot = mockk<DocumentSnapshot> {
            every { toObject(User::class.java) } returns null
        }
        val getTask = TaskCompletionSource<DocumentSnapshot>().apply { setResult(snapshot) }.task

        every { auth.currentUser } returns firebaseUser
        every { firestore.collection("users") } returns collection
        every { collection.document(uid) } returns document
        every { document.get() } returns getTask

        try {
            repository().fetchCurrentUser()
            fail("Expected IllegalStateException when user document missing")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `updateCurrentUser updates provided non blank fields`() = runTest {
        val uid = "uid-123"
        val firebaseUser = mockk<FirebaseUser> {
            every { this@mockk.uid } returns uid
        }
        val updateTask = TaskCompletionSource<Void>().apply { setResult(null) }.task
        val updatesSlot = slot<Map<String, Any>>()

        every { auth.currentUser } returns firebaseUser
        every { firestore.collection("users") } returns collection
        every { collection.document(uid) } returns document
        every { document.update(capture(updatesSlot)) } returns updateTask

        repository().updateCurrentUser(name = "Ada", bio = "Explores the stars")

        assertEquals(mapOf("name" to "Ada", "bio" to "Explores the stars"), updatesSlot.captured)
        verify(exactly = 1) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `updateCurrentUser skips update when both fields blank`() = runTest {
        val uid = "uid-123"
        val firebaseUser = mockk<FirebaseUser> {
            every { this@mockk.uid } returns uid
        }

        every { auth.currentUser } returns firebaseUser
        every { firestore.collection("users") } returns collection
        every { collection.document(uid) } returns document

        repository().updateCurrentUser(name = " ", bio = "")

        verify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `updateCurrentUser throws when current user missing`() = runTest {
        every { auth.currentUser } returns null

        try {
            repository().updateCurrentUser(name = "Ada", bio = "Bio")
            fail("Expected IllegalStateException when no user is authenticated")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `signOut delegates to FirebaseAuth`() = runTest {
        repository().signOut()

        verify { auth.signOut() }
    }
}
