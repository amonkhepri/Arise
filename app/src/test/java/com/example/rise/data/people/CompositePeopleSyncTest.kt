package com.example.rise.data.people

import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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

  private class FakePeopleSync : PeopleSync {
    override val syncPeopleErrors: Flow<Throwable> = MutableSharedFlow()
    override val currentUserCanonicalId = MutableStateFlow<String?>(null)

    override fun ensureStarted() = Unit

    override fun stop() = Unit
  }
}
