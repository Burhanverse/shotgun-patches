package dev.jason.gboardpatches.patches.gboard.features.webclipboard

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardWebClipboardManifestPatchSourceTest {
    @Test
    fun manifestPatchUsesGenericMetadataDrivenTilePreferencesDispatcher() {
        val source = String(
            Files.readAllBytes(
                Path.of(
                    "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                        "webclipboard/GboardWebClipboardManifestPatch.kt"
                )
            ),
            StandardCharsets.UTF_8
        )

        assertFalse(source.contains("settingsActivity.setAndroidAttribute(\"exported\", \"true\")"))
        assertTrue(source.contains("GboardTilePreferencesActivity"))
        assertTrue(source.contains("TILE_NAVIGATION_PATH_META_DATA"))
        assertTrue(source.contains("GboardClipboardSettingsFeature;"))
        assertFalse(source.contains("WebClipboardTilePreferencesActivity"))
        assertTrue(source.contains("ACTION_QS_TILE_PREFERENCES"))
        assertTrue(source.contains("WebClipShareActivity"))
        assertTrue(source.contains("Send to PC (WebClip)"))
        assertTrue(source.contains("Theme.Translucent.NoTitleBar"))
        assertTrue(source.contains("ACTION_SEND"))
        assertTrue(source.contains("ACTION_SEND_MULTIPLE"))
    }
}
