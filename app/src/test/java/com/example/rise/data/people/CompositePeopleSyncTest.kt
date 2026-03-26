package com.example.rise.data.people

import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompositePeopleSyncTest {

  @Test
  fun `constructor rejects briar delegate index outside delegates`() {
    val delegates = listOf(FakePeopleSync())

    val error = assertThrows(IllegalArgumentException::class.java) {
      CompositePeopleSync(
        delegates = delegates,
        transportMode = MutableStateFlow(BriarTransportMode.FIRESTORE),
        briarDelegateIndex = 1,
      )
    }

    assertEquals(
      "briarDelegateIndex must point to an existing delegate",
      error.message,
    )
  }

  @Test
  fun `current user id prefers briar delegate in hybrid mode`() = runTest {
    val firestoreSync = FakePeopleSync(initialCurrentUserId = "firestore-self")
    val briarSync = FakePeopleSync(initialCurrentUserId = "briar-self")
    val composite = CompositePeopleSync(
      delegates = listOf(firestoreSync, briarSync),
      transportMode = MutableStateFlow(BriarTransportMode.HYBRID),
      briarDelegateIndex = 1,
    )

    val result = composite.currentUserCanonicalId.first()

    assertEquals("briar-self", result)
  }

  @Test
  fun `current user id prefers firestore delegate in firestore mode`() = runTest {
    val firestoreSync = FakePeopleSync(initialCurrentUserId = "firestore-self")
    val briarSync = FakePeopleSync(initialCurrentUserId = "briar-self")
    val composite = CompositePeopleSync(
      delegates = listOf(firestoreSync, briarSync),
      transportMode = MutableStateFlow(BriarTransportMode.FIRESTORE),
      briarDelegateIndex = 1,
    )

    val result = composite.currentUserCanonicalId.first()

    assertEquals("firestore-self", result)
  }

  private class FakePeopleSync(
    initialCurrentUserId: String? = null,
  ) : PeopleSync {
    override val syncPeopleErrors: Flow<Throwable> = MutableSharedFlow()
    override val currentUserCanonicalId = MutableStateFlow(initialCurrentUserId)

    override fun ensureStarted() = Unit

    override fun stop() = Unit
  }
}
