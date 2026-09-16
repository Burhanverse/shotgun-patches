package dev.jason.gboardpatches.patches.gboard.features.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val COLOR_GM3_SURFACE_CONTAINER_LOW_FIELD = "Lqyj;->R:I"

internal val gboardAmoledThemeBytecodePatch = bytecodePatch(
    description = "在 GM3 color token 計算管線注入 AMOLED 純黑覆寫邏輯。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val constructorTarget = GboardVersionBindings.amoledThemeColorTokenConstructor
        check(
            constructorTarget.returnType == "V" &&
                constructorTarget.parameterTypes == listOf("Landroid/content/Context;", "Z"),
        ) {
            "AMOLED theme color token constructor binding has an unexpected prototype"
        }

        findMutableMethodOrThrow(constructorTarget).injectAmoledThemeOverride()
    }
}

internal fun MutableMethod.injectAmoledThemeOverride() {
    val runtimeAbi = RuntimeAbiCatalog.abi(RuntimeCallId.AMOLED_THEME_RUNTIME_APPLY_OVERRIDE)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")

    if (instructions.any { it.isMethodReference(runtimeAbi.reference) }) {
        return
    }

    val targetIndex = instructions.indexOfFirst {
        it.isFieldReference(COLOR_GM3_SURFACE_CONTAINER_LOW_FIELD)
    }
    check(targetIndex >= 0) {
        "Could not find $COLOR_GM3_SURFACE_CONTAINER_LOW_FIELD write in $definingClass->$name"
    }

    addInstructions(
        targetIndex + 1,
        """
            iget v0, p0, Lqyj;->Q:I
            iget v1, p0, Lqyj;->I:I
            iget v2, p0, Lqyj;->R:I
            iget v3, p0, Lqyj;->K:I
            ${RuntimeCallEmitter.invoke(
                RuntimeCallId.AMOLED_THEME_RUNTIME_APPLY_OVERRIDE,
                "p1, v0, v1, v2, v3",
            )}
            move-result-object v0
            const/4 v1, 0x0
            aget v1, v0, v1
            iput v1, p0, Lqyj;->Q:I
            const/4 v1, 0x1
            aget v1, v0, v1
            iput v1, p0, Lqyj;->I:I
            const/4 v1, 0x2
            aget v1, v0, v1
            iput v1, p0, Lqyj;->R:I
            const/4 v1, 0x3
            aget v1, v0, v1
            iput v1, p0, Lqyj;->K:I
        """.trimIndent(),
    )
}
