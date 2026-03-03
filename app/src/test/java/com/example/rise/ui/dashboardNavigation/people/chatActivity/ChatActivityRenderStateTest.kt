package com.example.rise.ui.dashboardNavigation.people.chatActivity

import com.example.rise.auth.AuthenticationService
import com.example.rise.models.TextMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ChatActivityRenderStateTest {

  @Test
  fun `shouldUpdateMessageRows returns true when current user id changes but message list stays same`() {
    val messages = listOf(
      TextMessage(
        text = "Hi",
        time = Date(1_700_000_000_000),
        senderId = "user-a",
        recipientId = "user-b",
        senderName = "Alice",
      )
    )

    val previousState = MessageListRenderState(messages = messages, currentUserId = null)
    val nextState = MessageListRenderState(messages = messages, currentUserId = "user-a")

    assertTrue(shouldUpdateMessageRows(previousState = previousState, nextState = nextState))
  }

  @Test
  fun `shouldUpdateMessageRows returns false when messages and current user id are unchanged`() {
    val messages = listOf(
      TextMessage(
        text = "Hi",
        time = Date(1_700_000_000_000),
        senderId = "user-a",
        recipientId = "user-b",
        senderName = "Alice",
      )
    )
    val state = MessageListRenderState(messages = messages, currentUserId = "user-a")

    assertFalse(shouldUpdateMessageRows(previousState = state, nextState = state))
  }

  @Test
  fun `resolveCurrentUserId prefers ui state identity over authenticated user`() {
    val authenticatedUser = AuthenticationService.User(
      id = "auth-user",
      displayName = "Auth Name",
      email = null,
      photoUrl = null,
    )

    val resolved = resolveCurrentUserId(
      stateCurrentUserId = "state-user",
      authenticatedUser = authenticatedUser,
    )

    assertEquals("state-user", resolved)
  }

  @Test
  fun `resolveCurrentUserId falls back to authenticated user when ui state is missing`() {
    val authenticatedUser = AuthenticationService.User(
      id = "auth-user",
      displayName = "Auth Name",
      email = null,
      photoUrl = null,
    )

    val resolved = resolveCurrentUserId(
      stateCurrentUserId = null,
      authenticatedUser = authenticatedUser,
    )

    assertEquals("auth-user", resolved)
  }

  @Test
  fun `resolveCurrentUserId returns null when state and authenticated user are missing`() {
    val resolved = resolveCurrentUserId(
      stateCurrentUserId = null,
      authenticatedUser = null,
    )

    assertNull(resolved)
  }

  @Test
  fun `resolveScheduledMessageSenderName uses authenticated display name when present`() {
    val authenticatedUser = AuthenticationService.User(
      id = "auth-user",
      displayName = "Auth Name",
      email = null,
      photoUrl = null,
    )

    val resolved = resolveScheduledMessageSenderName(authenticatedUser)

    assertEquals("Auth Name", resolved)
  }

  @Test
  fun `resolveScheduledMessageSenderName returns empty when display name is absent`() {
    val authenticatedUser = AuthenticationService.User(
      id = "auth-user",
      displayName = null,
      email = null,
      photoUrl = null,
    )

    val resolved = resolveScheduledMessageSenderName(authenticatedUser)

    assertEquals("", resolved)
  }
}
