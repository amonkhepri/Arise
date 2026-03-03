package com.example.rise.ui.dashboardNavigation.people.chatActivity

import com.example.rise.models.TextMessage
import org.junit.Assert.assertFalse
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
}
