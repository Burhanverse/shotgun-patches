package dev.jason.gboardpatches.patches.gboard.features.theme

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAmoledThemePatchTest {
    @Test
    fun `amoled theme catalog declares expected feature metadata and contributions`() {
        val catalog = JsonParser.parseString(
            Files.readString(root().resolve(CATALOG), StandardCharsets.UTF_8),
        ).asJsonObject
        val feature = catalog.getAsJsonArray("features")
            .map { it.asJsonObject }
            .single { it["feature_id"].asString == "amoled_theme" }

        assertEquals("AMOLED Pure Black Theme", feature["public_patch_name"].asString)
        assertEquals(AMOLED_THEME_FEATURE_MARKER, feature["feature_marker"].asString)
        assertEquals("version-sensitive", feature["migration_scope"].asString)

        val contributions = feature.getAsJsonArray("contributions").map { it.asJsonObject }
        assertEquals(1, contributions.size)
        val contribution = contributions.single()
        assertEquals("amoled_theme.color_token", contribution["contribution_id"].asString)
        assertEquals("dedicated_bytecode", contribution["anchor_family_id"].asString)
        assertEquals(1820, contribution["order"].asInt)
        assertEquals(
            listOf("amoled_theme_color_token_constructor"),
            contribution.getAsJsonArray("required_bindings").map { it.asString },
        )
        assertEquals(
            listOf(RuntimeCallId.AMOLED_THEME_RUNTIME_IS_ACTIVE.name),
            contribution.getAsJsonArray("runtime_calls").map { it.asString },
        )
    }

    @Test
    fun `reviewed bindings declare the exact 1854 rmt constructor target`() {
        val constructorTarget = GboardVersionBindings.amoledThemeColorTokenConstructor
        assertEquals("Lrmt;", constructorTarget.ownerDescriptor)
        assertEquals("<init>", constructorTarget.name)
        assertEquals(listOf("Landroid/content/Context;", "Z"), constructorTarget.parameterTypes)
        assertEquals("V", constructorTarget.returnType)
        assertEquals("Lrmt;-><init>(Landroid/content/Context;Z)V", constructorTarget.reference)
    }

    @Test
    fun `bytecode patch source wires runtime abi call and updates target fields`() {
        val source = Files.readString(root().resolve(BYTECODE), StandardCharsets.UTF_8)
        assertTrue(source.contains("RuntimeCallId.AMOLED_THEME_RUNTIME_IS_ACTIVE"))
        assertTrue(source.contains("Lrmt;->V:I"))
        assertTrue(source.contains("Lrmt;->a:Z"))
        assertTrue(source.contains("Lrmt;->Q:I"))
        assertTrue(source.contains("Lrmt;->I:I"))
        assertTrue(source.contains("Lrmt;->R:I"))
        assertTrue(source.contains("Lrmt;->S:I"))
        assertTrue(source.contains("Lrmt;->T:I"))
        assertTrue(source.contains("Lrmt;->U:I"))
        assertTrue(source.contains("Lrmt;->P:I"))
        assertTrue(source.contains("Lrmt;->G:I"))
        assertTrue(source.contains("RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR"))
        assertTrue(source.contains("jasondevApplyAmoledTheme"))
        assertTrue(source.contains("installAmoledThemeHelper"))
        assertTrue(source.contains("if-nez v0, :cond_return"))
        assertTrue(source.contains("if-eqz v0, :cond_return"))
        assertTrue(source.contains(".catch Ljava/lang/Throwable;"))
        assertTrue(source.contains("invoke-direct {p0}, \$helperReference"))
    }

    private fun root(): Path {
        val working = Path.of("").toAbsolutePath().normalize()
        return generateSequence(working) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }

    private companion object {
        const val CATALOG =
            "patches/src/main/resources/gboard/gboard-port-product-catalog.json"
        const val BYTECODE =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "theme/GboardAmoledThemeBytecodePatch.kt"
    }
}
