package com.example.rise.ui.dashboardNavigation.people.chatActivity

import com.example.rise.helpers.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatActivityIntentContractTest {

    @Test
    fun `launch contract includes canonical conversation id when provided`() {
        val contract = ChatLaunchContract(
            userId = "user-123",
            userName = "Alex",
            conversationId = "conversation-456",
        )

        assertEquals(
            mapOf(
                AppConstants.USER_ID to "user-123",
                AppConstants.USER_NAME to "Alex",
                AppConstants.CONVERSATION_ID to "conversation-456",
            ),
            contract.toExtras(),
        )
    }

    @Test
    fun `launch contract keeps conversation id optional for legacy callers`() {
        val contract = resolveChatLaunchContract(
            userId = "user-123",
            userName = "Alex",
            conversationId = null,
        )

        assertEquals("user-123", contract.userId)
        assertEquals("Alex", contract.userName)
        assertNull(contract.conversationId)
        assertEquals(
            mapOf(
                AppConstants.USER_ID to "user-123",
                AppConstants.USER_NAME to "Alex",
            ),
            contract.toExtras(),
        )
    }

    @Test
    fun `launch contract keeps canonical conversation id when preparing chat initialisation`() {
        val contract = ChatLaunchContract(
            userId = "user-123",
            userName = "Alex",
            conversationId = "conversation-456",
        )

        assertEquals(
            ChatConversationInitialisation(
                userId = "user-123",
                userName = "Alex",
                conversationId = "conversation-456",
            ),
            contract.toConversationInitialisation(),
        )
    }
}
