package com.example.rise.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.Assert.assertTrue
import org.w3c.dom.Element

class SplashActivityDeepLinkIntentFilterTest {

    @Test
    fun `splash activity exposes arise invitation deep link intent filter`() {
        assertTrue(
            hasViewBrowsableFilter(scheme = "arise", host = "briar", path = "/invite"),
        )
    }

    @Test
    fun `splash activity exposes https invitation deep link intent filters`() {
        assertTrue(
            hasViewBrowsableFilter(scheme = "https", host = "arise.app", path = "/briar/invite"),
        )
        assertTrue(
            hasViewBrowsableFilter(scheme = "https", host = "www.arise.app", path = "/briar/invite"),
        )
    }

    private fun hasViewBrowsableFilter(
        scheme: String,
        host: String,
        path: String,
    ): Boolean {
        val splash = splashActivityElement() ?: return false
        val filterNodes = splash.getElementsByTagName("intent-filter")

        for (index in 0 until filterNodes.length) {
            val filter = filterNodes.item(index) as? Element ?: continue
            if (!hasAction(filter, "android.intent.action.VIEW")) continue
            if (!hasCategory(filter, "android.intent.category.DEFAULT")) continue
            if (!hasCategory(filter, "android.intent.category.BROWSABLE")) continue
            if (hasData(filter, scheme = scheme, host = host, path = path)) return true
        }

        return false
    }

    private fun splashActivityElement(): Element? {
        val manifest = parseManifest()
        val activities = manifest.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val activity = activities.item(index) as? Element ?: continue
            if (activity.getAttributeNS(ANDROID_NS, "name") == ".ui.SplashActivity") return activity
        }
        return null
    }

    private fun hasAction(filter: Element, actionName: String): Boolean {
        val actions = filter.getElementsByTagName("action")
        for (index in 0 until actions.length) {
            val action = actions.item(index) as? Element ?: continue
            if (action.getAttributeNS(ANDROID_NS, "name") == actionName) return true
        }
        return false
    }

    private fun hasCategory(filter: Element, categoryName: String): Boolean {
        val categories = filter.getElementsByTagName("category")
        for (index in 0 until categories.length) {
            val category = categories.item(index) as? Element ?: continue
            if (category.getAttributeNS(ANDROID_NS, "name") == categoryName) return true
        }
        return false
    }

    private fun hasData(filter: Element, scheme: String, host: String, path: String): Boolean {
        val dataNodes = filter.getElementsByTagName("data")
        for (index in 0 until dataNodes.length) {
            val data = dataNodes.item(index) as? Element ?: continue
            if (
                data.getAttributeNS(ANDROID_NS, "scheme") == scheme &&
                data.getAttributeNS(ANDROID_NS, "host") == host &&
                data.getAttributeNS(ANDROID_NS, "path") == path
            ) {
                return true
            }
        }
        return false
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
        .newDocumentBuilder()
        .parse(manifestFile())

    private fun manifestFile(): File {
        val candidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Unable to locate app/src/main/AndroidManifest.xml for manifest intent filter test.")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
