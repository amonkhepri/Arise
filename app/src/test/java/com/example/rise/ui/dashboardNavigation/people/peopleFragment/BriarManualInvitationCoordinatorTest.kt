package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceResult
import com.example.rise.transport.briar.invite.BriarInvitationLink
import com.example.rise.transport.briar.invite.BriarInvitationLinkParseResult
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BriarManualInvitationCoordinatorTest {

  @Test
  fun accept_forwardsRawLinkToUseCase_andReturnsLaunchChatContract() = runTest {
    var acceptedRawLink: String? = null
    val coordinator = BriarManualInvitationCoordinator { rawLink ->
      acceptedRawLink = rawLink
      BriarInvitationAcceptanceResult.Accepted(
        invitation = BriarInvitationLink(
          briarLink = "briar://alice-link",
          alias = null,
          duplicateKey = "link:briar://alice-link",
        ),
        conversationId = "conversation-123",
      )
    }

    val result = coordinator.accept("briar://alice-link")

    assertEquals("briar://alice-link", acceptedRawLink)
    assertEquals(
      BriarManualInvitationCoordinator.Result.LaunchChat(
        ChatLaunchContract(
          userId = "link:briar://alice-link",
          userName = "link:briar://alice-link",
          conversationId = "conversation-123",
        ),
      ),
      result,
    )
  }

  @Test
  fun accept_returnsInvalidInvitation_whenUseCaseRejectsLink() = runTest {
    val coordinator = BriarManualInvitationCoordinator {
      BriarInvitationAcceptanceResult.InvalidLink(
        BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
      )
    }

    val result = coordinator.accept("not-a-briar-link")

    assertEquals(
      BriarManualInvitationCoordinator.Result.InvalidInvitation(
        BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
      ),
      result,
    )
  }

  @Test
  fun accept_returnsInvitationFailed_whenUseCaseThrowsDomainFailure() = runTest {
    val failure = IllegalStateException("boom")
    val coordinator = BriarManualInvitationCoordinator {
      BriarInvitationAcceptanceResult.Failed(
        invitation = BriarInvitationLink(
          briarLink = "briar://alice-link",
          alias = null,
          duplicateKey = "link:briar://alice-link",
        ),
        error = failure,
      )
    }

    val result = coordinator.accept("briar://alice-link")

    assertEquals(
      BriarManualInvitationCoordinator.Result.InvitationFailed(failure),
      result,
    )
  }
}
