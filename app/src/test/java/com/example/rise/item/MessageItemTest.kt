package com.example.rise.item

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageItemTest {

  @Test
  fun `isCurrentUserMessage returns true when sender matches current user`() {
    assertTrue(isCurrentUserMessage(senderId = "self", currentUserId = "self"))
  }

  @Test
  fun `isCurrentUserMessage returns false when current user id is missing`() {
    assertFalse(isCurrentUserMessage(senderId = "self", currentUserId = null))
  }
}
