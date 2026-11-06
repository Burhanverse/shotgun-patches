package dev.jason.gboardpatches.patches.gboard.features.webclipboard

import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardWebClipShareManifestPatchTest {
    @Test
    fun manifestInjectsWebClipShareActivityWithRequiredFilters() {
        val manifest = parse(MINIMAL_MANIFEST)
        applyGboardWebClipboardManifest(manifest)

        val application = manifest.documentElement.childElements("application").first()
        val shareActivity = application.childElements("activity").firstOrNull {
            it.manifestAndroidAttribute("name") == "dev.jason.gboardpatches.extension.filetransfer.WebClipShareActivity"
        }

        assertNotNull(shareActivity)
        val activity = shareActivity!!
        assertEquals("true", activity.manifestAndroidAttribute("exported"))
        assertEquals("Send to PC (WebClip)", activity.manifestAndroidAttribute("label"))
        assertEquals("@android:style/Theme.Translucent.NoTitleBar", activity.manifestAndroidAttribute("theme"))

        val filters = activity.childElements("intent-filter").toList()
        assertEquals(2, filters.size)

        val sendFilter = filters.firstOrNull { f ->
            f.childElements("action").any { it.manifestAndroidAttribute("name") == "android.intent.action.SEND" }
        }
        assertNotNull(sendFilter)
        assertEquals(
            "android.intent.category.DEFAULT",
            sendFilter!!.childElements("category").first().manifestAndroidAttribute("name")
        )
        assertEquals(
            "*/*",
            sendFilter.childElements("data").first().manifestAndroidAttribute("mimeType")
        )

        val sendMultipleFilter = filters.firstOrNull { f ->
            f.childElements("action").any { it.manifestAndroidAttribute("name") == "android.intent.action.SEND_MULTIPLE" }
        }
        assertNotNull(sendMultipleFilter)
        assertEquals(
            "android.intent.category.DEFAULT",
            sendMultipleFilter!!.childElements("category").first().manifestAndroidAttribute("name")
        )
        assertEquals(
            "*/*",
            sendMultipleFilter.childElements("data").first().manifestAndroidAttribute("mimeType")
        )
    }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val MINIMAL_MANIFEST =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.google.android.inputmethod.latin\"><application /></manifest>"
    }
}
