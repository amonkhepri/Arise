package com.example.rise.transport.briar.invite

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriarInvitationLinkParserTest {

    private val parser = BriarInvitationLinkParser()

    @Test
    fun `raw briar link is accepted and normalized`() {
        val briarLink = validBriarLink()

        val result = parser.parse("  $briarLink  ")

        assertTrue(result is BriarInvitationLinkParseResult.Success)
        val invitation = (result as BriarInvitationLinkParseResult.Success).invitation
        assertEquals(briarLink, invitation.briarLink)
        assertEquals(null, invitation.alias)
        assertEquals("link:$briarLink", invitation.duplicateKey)
    }

    @Test
    fun `wrapped arise invitation link requires encoded briar link`() {
        val result = parser.parse("arise://briar/invite")

        assertEquals(
            BriarInvitationLinkParseResult.InvalidReason.MISSING_REQUIRED_BRIAR_LINK,
            (result as BriarInvitationLinkParseResult.Invalid).reason,
        )
    }

    @Test
    fun `wrapped arise invitation link decodes alias and invite id`() {
        val result = parser.parse(wrappedAriseInvite(alias = "Alice Smith", inviteId = "Invite-01"))

        assertTrue(result is BriarInvitationLinkParseResult.Success)
        val invitation = (result as BriarInvitationLinkParseResult.Success).invitation
        assertEquals(validBriarLink(), invitation.briarLink)
        assertEquals("Alice Smith", invitation.alias)
        assertEquals("invite:invite-01", invitation.duplicateKey)
    }

    @Test
    fun `https invitation supports invite_id duplicate key`() {
        val invite = "https://arise.app/briar/invite?link=${encode(validBriarLink())}&invite_id=%20DUP-42%20"

        val result = parser.parse(invite)

        assertTrue(result is BriarInvitationLinkParseResult.Success)
        val invitation = (result as BriarInvitationLinkParseResult.Success).invitation
        assertEquals("invite:dup-42", invitation.duplicateKey)
    }

    @Test
    fun `wrapped invitation rejects non-briar nested link`() {
        val invite = "arise://briar/invite?link=${encode("https://example.com")}"

        val result = parser.parse(invite)

        assertEquals(
            BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
            (result as BriarInvitationLinkParseResult.Invalid).reason,
        )
    }

    @Test
    fun `unsupported schemes are rejected`() {
        val result = parser.parse("mailto:test@example.com")

        assertEquals(
            BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK,
            (result as BriarInvitationLinkParseResult.Invalid).reason,
        )
    }

    private fun validBriarLink(): String = "briar://${"b".repeat(53)}"

    private fun wrappedAriseInvite(
        nestedLink: String = validBriarLink(),
        alias: String? = null,
        inviteId: String? = null,
    ): String {
        val params = mutableListOf("link=${encode(nestedLink)}")
        if (alias != null) params += "alias=${encode(alias)}"
        if (inviteId != null) params += "inviteId=${encode(inviteId)}"
        return "arise://briar/invite?${params.joinToString("&")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
