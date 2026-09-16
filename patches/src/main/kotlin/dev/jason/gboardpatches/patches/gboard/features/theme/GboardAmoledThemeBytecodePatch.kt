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

private const val COLOR_GM3_SCRIM_FIELD = "Lqyj;->V:I"

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
    val runtimeAbi = RuntimeAbiCatalog.abi(RuntimeCallId.AMOLED_THEME_RUNTIME_IS_ACTIVE)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")

    if (instructions.any { it.isMethodReference(runtimeAbi.reference) }) {
        return
    }

    val targetIndex = instructions.indexOfFirst {
        it.isFieldReference(COLOR_GM3_SCRIM_FIELD)
    }
    check(targetIndex >= 0) {
        "Could not find $COLOR_GM3_SCRIM_FIELD write in $definingClass->$name"
    }

    addInstructions(
        targetIndex + 1,
        """
            iget-boolean v0, p0, Lqyj;->a:Z
            if-eqz v0, :cond_amoled_done
            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_IS_ACTIVE, "")}
            move-result v0
            if-nez v0, :cond_amoled_done
            const/high16 v0, -0x1000000
            iput v0, p0, Lqyj;->Q:I
            iput v0, p0, Lqyj;->I:I
            iput v0, p0, Lqyj;->R:I
            iput v0, p0, Lqyj;->S:I
            iput v0, p0, Lqyj;->T:I
            iput v0, p0, Lqyj;->U:I
            iput v0, p0, Lqyj;->P:I
            iput v0, p0, Lqyj;->G:I
            :cond_amoled_done
        """.trimIndent(),
    )
}
