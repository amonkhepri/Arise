package com.example.rise.ui.dashboardNavigation.people.peopleFragment

import android.content.Intent
import com.example.rise.ui.dashboardNavigation.people.chatActivity.ChatLaunchContract
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Document
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
class PeopleFragmentInviteActionsTest {

    @Test
    fun `fragment_people layout exposes share and add by link actions`() {
        val document = parseLayout("app/src/main/res/layout/fragment_people.xml")

        assertButton(
            document = document,
            id = "@+id/button_share_raw_briar_link",
            text = "@string/people_share_raw_briar_link",
        )
        assertButton(
            document = document,
            id = "@+id/button_add_by_raw_briar_link",
            text = "@string/people_add_by_raw_briar_link",
        )
    }

    @Test
    fun `share event resolves to chooser with raw briar link text`() {
        val action = requireNotNull(
            resolvePeopleInviteUiAction(
                event = PeopleViewModel.PeopleEvent.ShareMyRawBriarLink("briar://raw-link"),
                chooserTitle = "Share Briar link",
                promptCopy = samplePromptCopy(),
            ),
        )

        assertTrue(action is PeopleInviteUiAction.ShareRawBriarLink)
        val chooserIntent = (action as PeopleInviteUiAction.ShareRawBriarLink).chooserIntent
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)

        val shareIntent = chooserIntent.requireParcelableIntentExtra(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals("text/plain", shareIntent.type)
        assertEquals("briar://raw-link", shareIntent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `invite events resolve to prompt and chat launch`() {
        val promptCopy = samplePromptCopy()
        val launchContract = ChatLaunchContract(
            userId = "user-1",
            userName = "Alice",
            conversationId = "conversation-1",
        )

        assertEquals(
            PeopleInviteUiAction.PromptAddByLink(promptCopy),
            resolvePeopleInviteUiAction(
                event = PeopleViewModel.PeopleEvent.PromptAddByLink,
                chooserTitle = "unused",
                promptCopy = promptCopy,
            ),
        )
        assertEquals(
            PeopleInviteUiAction.OpenAddedContactChat(launchContract),
            resolvePeopleInviteUiAction(
                event = PeopleViewModel.PeopleEvent.LaunchChatFromAddedLink(launchContract),
                chooserTitle = "unused",
                promptCopy = promptCopy,
            ),
        )
    }

    @Test
    fun `invite failures resolve to user visible messages`() {
        val promptCopy = samplePromptCopy()

        assertEquals(
            PeopleInviteUiAction.ShowMessage("Invalid Briar invitation link."),
            resolvePeopleInviteUiAction(
                event = PeopleViewModel.PeopleEvent.ShowMessage("Invalid Briar invitation link."),
                chooserTitle = "unused",
                promptCopy = promptCopy,
            ),
        )
        assertEquals(
            PeopleInviteUiAction.ShowMessage("Connector offline"),
            resolvePeopleInviteUiAction(
                event = PeopleViewModel.PeopleEvent.ShowMessage("Connector offline"),
                chooserTitle = "unused",
                promptCopy = promptCopy,
            ),
        )
    }

    private fun samplePromptCopy() = AddByRawBriarLinkPromptCopy(
        title = "Add contact by Briar link",
        message = "Paste a raw Briar invitation link to add a contact and start chatting.",
        hint = "briar://...",
        confirmLabel = "Add contact",
    )

    private fun parseLayout(path: String): Document {
        val layoutFile = listOf(
            File(path),
            File("src/main/res/layout/${File(path).name}"),
        ).firstOrNull { it.exists() } ?: error("Layout file not found: $path")
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(layoutFile)
            .apply { documentElement.normalize() }
    }

    private fun assertButton(document: Document, id: String, text: String) {
        val element = findElementById(document, id)
        assertNotNull(element)
        assertEquals("Button", element!!.tagName)
        assertEquals(text, element.getAttribute("android:text"))
    }

    private fun findElementById(document: Document, id: String): Element? {
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && node.getAttribute("android:id") == id) {
                return node
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun Intent.requireParcelableIntentExtra(key: String): Intent {
        return requireNotNull(getParcelableExtra(key) as? Intent)
    }
}
