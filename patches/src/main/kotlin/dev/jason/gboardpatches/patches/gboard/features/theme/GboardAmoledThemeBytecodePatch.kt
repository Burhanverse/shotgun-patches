package dev.jason.gboardpatches.patches.gboard.features.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val COLOR_GM3_SCRIM_FIELD = "Lqyj;->V:I"
private const val APPLY_AMOLED_THEME_HELPER = "jasondevApplyAmoledTheme"

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

        val qyjClass = mutableClass(constructorTarget.ownerDescriptor)
        qyjClass.installAmoledThemeHelper()

        val constructorMethod = findMutableMethodOrThrow(constructorTarget)
        constructorMethod.injectAmoledThemeOverride()
    }
}

internal fun MutableClass.installAmoledThemeHelper() {
    addHelperMethodIfMissing(
        name = APPLY_AMOLED_THEME_HELPER,
        parameterTypes = emptyList(),
        returnType = "V",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 3,
        body = """
            :try_start_0
            iget-boolean v0, p0, Lqyj;->a:Z

            if-nez v0, :cond_return

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_IS_ACTIVE, "")}

            move-result v0

            if-eqz v0, :cond_return

            iget v1, p0, Lqyj;->Q:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->Q:I

            iget v1, p0, Lqyj;->I:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->I:I

            iget v1, p0, Lqyj;->R:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->R:I

            iget v1, p0, Lqyj;->S:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->S:I

            iget v1, p0, Lqyj;->T:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->T:I

            iget v1, p0, Lqyj;->U:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->U:I

            iget v1, p0, Lqyj;->P:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->P:I

            iget v1, p0, Lqyj;->G:I

            const/4 v0, 0x0

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->G:I

            iget v1, p0, Lqyj;->O:I

            const/4 v0, 0x1

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->O:I

            iget v1, p0, Lqyj;->J:I

            iget v0, p0, Lqyj;->b:I

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_BLEND_TEXT_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->J:I

            iget v1, p0, Lqyj;->L:I

            iget v0, p0, Lqyj;->b:I

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_BLEND_TEXT_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->L:I

            iget v1, p0, Lqyj;->m:I

            const/4 v0, 0x2

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_ADJUST_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->m:I

            iget v1, p0, Lqyj;->n:I

            iget v0, p0, Lqyj;->b:I

            ${RuntimeCallEmitter.invoke(RuntimeCallId.AMOLED_THEME_RUNTIME_BLEND_TEXT_COLOR, "v1, v0")}

            move-result v1

            iput v1, p0, Lqyj;->n:I

            :cond_return
            return-void
            :try_end_0
            .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

            :catch_0
            return-void
        """.trimIndent(),
    )
}

internal fun MutableMethod.injectAmoledThemeOverride() {
    val helperReference = "$definingClass->$APPLY_AMOLED_THEME_HELPER()V"
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")

    if (instructions.any { it.isMethodReference(helperReference) }) {
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
            invoke-direct {p0}, $helperReference
        """.trimIndent(),
    )
}
