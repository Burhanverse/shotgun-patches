package dev.jason.gboardpatches.patches.gboard.features.shotgunkeyboard

import dev.jason.gboardpatches.patches.gboard.registry.gboardShotgunKeyboardPatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GboardShotgunKeyboardPatchShapeTest {
    @Test
    fun `public patch remains applied by default`() {
        assertTrue(gboardShotgunKeyboardPatch.default)
    }

    @Test
    fun `gesture patch points to gesture family composer feature`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/shotgunkeyboard/" +
                "GboardShotgunKeyboardGesturePatch.kt"
        )
        assertTrue(source.contains("GboardGestureFamilyFeature.SHOTGUN_KEYBOARD"))
        assertTrue(source.contains("gboardGestureFamilyFeaturePatch"))
        assertFalse(source.contains("addInstructions"))
    }

    @Test
    fun `feature marker patch specifies canonical shotgun keyboard marker`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/shotgunkeyboard/" +
                "GboardShotgunKeyboardFeatureMarkerPatch.kt"
        )
        assertTrue(source.contains("dev.jason.gboardpatches.feature.shotgun_keyboard"))
        assertTrue(source.contains("applyFeatureMarker"))
    }

    @Test
    fun `gesture family composer includes shotgun helper topology`() {
        val source = readSource(
            "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/" +
                "GboardGestureFamilyComposer.kt"
        )
        assertTrue(source.contains("jasondevDispatchWithShotgun"))
        assertTrue(source.contains("SHOTGUN_KEYBOARD_RUNTIME_MAYBE_PLAY_SHOTGUN_SOUND"))
    }

    private fun readSource(path: String): String =
        Files.readString(Path.of(path)).replace("\r\n", "\n")
}
