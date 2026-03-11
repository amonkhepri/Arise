package com.example.rise.ui.mainActivity

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BriarInvitationOnboardingResultHandlerTest {

  @Test
  fun `launch chat result starts chat intent and marks onboarding handled`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { error("invalid invitation should not be logged for launch result") },
      logInvitationFailure = { error("failure should not be logged for launch result") },
    )
    val launchIntent = Intent("chat")

    handler.handle(BriarInvitationOnboardingCoordinator.Result.LaunchChat(launchIntent))

    assertEquals(1, launchedIntents.size)
    assertSame(launchIntent, launchedIntents.single())
    assertTrue(shownMessages.isEmpty())
    assertEquals(1, handledCount)
  }

  @Test
  fun `pending sync result preserves fallback message and does not start chat`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { error("invalid invitation should not be logged for pending sync") },
      logInvitationFailure = { error("failure should not be logged for pending sync") },
    )

    handler.handle(BriarInvitationOnboardingCoordinator.Result.ContactAddedPendingSync("Alice"))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(
      listOf(BriarInvitationOnboardingResultHandler.pendingSyncMessage("Alice")),
      shownMessages,
    )
    assertEquals(1, handledCount)
  }
}
