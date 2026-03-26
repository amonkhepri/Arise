package com.example.rise.ui.mainActivity

import android.content.Intent
import com.example.rise.transport.briar.invite.BriarInvitationDuplicateFailure
import com.example.rise.transport.briar.invite.BriarInvitationExpiredFailure
import com.example.rise.transport.briar.invite.BriarInvitationInvalidFailure
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
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

  @Test
  fun `chat launch failure shows explicit hard failure message`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val failure = BriarInvitationChatLaunchFailure("Alice")

    handler.handle(BriarInvitationOnboardingCoordinator.Result.InvitationFailed(failure))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(
      listOf("Briar contact was added, but chat could not be opened. Try again from People."),
      shownMessages,
    )
    assertTrue(loggedInvalidInvitations.isEmpty())
    assertEquals(listOf(failure), loggedFailures)
    assertEquals(1, handledCount)
  }

  @Test
  fun `duplicate invitation failure shows duplicate-specific feedback`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val failure = BriarInvitationDuplicateFailure(IllegalStateException("already used"))

    handler.handle(BriarInvitationOnboardingCoordinator.Result.InvitationFailed(failure))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(listOf("This Briar invitation link was already used."), shownMessages)
    assertTrue(loggedInvalidInvitations.isEmpty())
    assertTrue(loggedFailures.isEmpty())
    assertEquals(1, handledCount)
  }

  @Test
  fun `expired invitation failure shows expired-specific feedback`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val failure = BriarInvitationExpiredFailure(IllegalStateException("expired"))

    handler.handle(BriarInvitationOnboardingCoordinator.Result.InvitationFailed(failure))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(
      listOf("This Briar invitation link has expired. Ask for a new one."),
      shownMessages,
    )
    assertTrue(loggedInvalidInvitations.isEmpty())
    assertTrue(loggedFailures.isEmpty())
    assertEquals(1, handledCount)
  }

  @Test
  fun `invalid invitation failure shows invalid-specific feedback`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val failure = BriarInvitationInvalidFailure(IllegalArgumentException("invalid"))

    handler.handle(BriarInvitationOnboardingCoordinator.Result.InvitationFailed(failure))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(listOf("Invalid Briar invitation link."), shownMessages)
    assertTrue(loggedInvalidInvitations.isEmpty())
    assertTrue(loggedFailures.isEmpty())
    assertEquals(1, handledCount)
  }

  @Test
  fun `parse invalid invitation logs invalid action and shows invalid feedback`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val invalidResult = BriarInvitationOnboardingCoordinator.Result.InvalidInvitation(
      BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
    )

    handler.handle(invalidResult)

    assertTrue(launchedIntents.isEmpty())
    assertEquals(listOf("Invalid Briar invitation link."), shownMessages)
    assertEquals(listOf(invalidResult), loggedInvalidInvitations)
    assertTrue(loggedFailures.isEmpty())
    assertEquals(1, handledCount)
  }

  @Test
  fun `generic invitation failure logs hard failure and shows fallback feedback`() {
    val launchedIntents = mutableListOf<Intent>()
    val shownMessages = mutableListOf<String>()
    val loggedInvalidInvitations = mutableListOf<BriarInvitationOnboardingCoordinator.Result.InvalidInvitation>()
    val loggedFailures = mutableListOf<Throwable>()
    var handledCount = 0
    val handler = BriarInvitationOnboardingResultHandler(
      launchChat = { intent -> launchedIntents += intent },
      showMessage = { message -> shownMessages += message },
      markHandled = { handledCount += 1 },
      logInvalidInvitation = { result -> loggedInvalidInvitations += result },
      logInvitationFailure = { error -> loggedFailures += error },
    )
    val failure = IllegalStateException("boom")

    handler.handle(BriarInvitationOnboardingCoordinator.Result.InvitationFailed(failure))

    assertTrue(launchedIntents.isEmpty())
    assertEquals(listOf("Unable to add Briar contact."), shownMessages)
    assertTrue(loggedInvalidInvitations.isEmpty())
    assertEquals(listOf(failure), loggedFailures)
    assertEquals(1, handledCount)
  }
}
