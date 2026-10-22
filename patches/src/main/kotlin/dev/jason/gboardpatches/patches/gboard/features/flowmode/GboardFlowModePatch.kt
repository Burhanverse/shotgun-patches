package dev.jason.gboardpatches.patches.gboard.features.flowmode

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.*
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val ACCESS_TYPE = "Ldev/jason/gboardpatches/extension/flowmode/GboardFlowModeAccess;"
private const val POINTER_CALLBACK_TYPE = "Ldev/jason/gboardpatches/extension/flowmode/GboardFlowModePointerCallback;"

internal val gboardFlowMode1853Patch = bytecodePatch(
    description = "Rebuild the reviewed Flow detector, overlay and request/native continuation.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        check(GboardVersionBindings.targetVersion == "18.5.3") { "Flow1853 requires the exact reviewed target" }
        val accessors = flowAccessors()
        accessors.forEach { it.preflight(this) }
        val hooks = flowHooks()
        hooks.forEach { requireFlowHook(it.target.resolve(this), it) }
        val constructor = GboardVersionBindings.flowTaskConstructorHook.resolve(this)
        check(constructor.name == "<init>" && constructor.returnType == "V")
        accessors.forEach { it.install(this) }
        installFlowTypeAccessors(this)
        mutableClass(POINTER_CALLBACK_TYPE).interfaces.add("Lnih;")
        hooks.forEach { installFlowWrapper(this, it) }
        constructor.returnInstructionIndices().asReversed().forEach { index ->
            constructor.addInstructions(index, RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_TAG_TASK, "p0, p1"))
        }
    }
}

private data class FlowAccessor(
    val name: String,
    val field: GboardFieldTarget? = null,
    val method: GboardMethodTarget? = null,
    val staticExpected: Boolean = false,
) {
    fun preflight(context: BytecodePatchContext) {
        val flags = field?.resolve(context)?.accessFlags ?: method!!.resolve(context).accessFlags
        check((flags and AccessFlags.STATIC.value != 0) == staticExpected) { "Flow target static/instance drift: $name" }
        val stubs = context.mutableClass(ACCESS_TYPE).methods.filter { it.name == name }
        check(stubs.size == 1) { "Flow access stub missing/ambiguous: $name" }
        val expectedParameters = (if (staticExpected) emptyList() else listOf("Ljava/lang/Object;")) +
            method?.parameterTypes.orEmpty().map(::flowErasedType)
        check(stubs.single().parameterTypes == expectedParameters &&
            stubs.single().returnType == flowErasedType(field?.type ?: method!!.returnType)) {
            "Flow erased adapter prototype drift: $name"
        }
        val owner = field?.ownerDescriptor ?: method!!.ownerDescriptor
        val helper = "jasondevFlowAccess" + name.replaceFirstChar(Char::uppercaseChar)
        check(context.mutableClass(owner).methods.none { it.name == helper }) { "Flow accessor helper collision: $name" }
    }
    fun install(context: BytecodePatchContext) {
        val owner = field?.ownerDescriptor ?: method!!.ownerDescriptor
        val ownerClass = context.mutableClass(owner)
        val flags = field?.resolve(context)?.accessFlags ?: method!!.resolve(context).accessFlags
        val static = flags and AccessFlags.STATIC.value != 0
        val actualParameters = (if (static) emptyList() else listOf(owner)) + method?.parameterTypes.orEmpty()
        val returnType = field?.type ?: method!!.returnType
        val helper = "jasondevFlowAccess" + name.replaceFirstChar(Char::uppercaseChar)
        val helperBody = if (field != null) {
            val opcode = (if (static) "sget" else "iget") + when {
                returnType.startsWith("L") || returnType.startsWith("[") -> "-object"
                returnType == "Z" -> "-boolean"
                returnType == "J" || returnType == "D" -> "-wide"
                else -> ""
            }
            "$opcode v0, " + (if (static) "" else "p0, ") + field.reference + "\n" + flowReturn(returnType, "v0")
        } else {
            val invoke = when {
                static -> "invoke-static"
                flags and AccessFlags.PRIVATE.value != 0 -> "invoke-direct"
                ownerClass.accessFlags and AccessFlags.INTERFACE.value != 0 -> "invoke-interface"
                else -> "invoke-virtual"
            }
            flowInvoke(invoke, actualParameters.sumOf(::flowWords), method!!.reference) + "\n" + flowResult(returnType)
        }
        ownerClass.addHelperMethodIfMissing(helper, actualParameters, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            2 + actualParameters.sumOf(::flowWords), helperBody)
        val adapter = context.mutableClass(ACCESS_TYPE)
        val stub = adapter.methods.single { it.name == name }
        val casts = buildString {
            var index = 1
            actualParameters.zip(stub.parameterTypes).forEach { (actual, erased) ->
                if (actual != erased) append("check-cast p$index, $actual\n")
                index += flowWords(actual)
            }
        }
        adapter.methods.remove(stub)
        val parameterWords = actualParameters.sumOf(::flowWords)
        val call = if (parameterWords == 0) "invoke-static {}, $owner->$helper()$returnType"
            else "invoke-static/range {p1 .. p$parameterWords}, $owner->$helper(${actualParameters.joinToString("")})$returnType"
        adapter.addHelperMethodIfMissing(name, stub.parameterTypes.map { it.toString() }, stub.returnType, stub.accessFlags,
            3 + parameterWords, casts + call + "\n" + flowResult(returnType))
    }
}

private fun flowWords(type: String): Int = if (type == "J" || type == "D") 2 else 1
private fun flowReturn(type: String, register: String): String = when {
    type == "V" -> "return-void"
    type.startsWith("L") || type.startsWith("[") -> "return-object $register"
    type == "J" || type == "D" -> "return-wide $register"
    else -> "return $register"
}
private fun flowResult(type: String): String = if (type == "V") "return-void" else {
    val suffix = when {
        type.startsWith("L") || type.startsWith("[") -> "-object"
        type == "J" || type == "D" -> "-wide"
        else -> ""
    }
    "move-result$suffix v0\n" + flowReturn(type, "v0")
}
private fun flowInvoke(opcode: String, words: Int, reference: String): String =
    if (words == 0) "$opcode {}, $reference" else "$opcode/range {p0 .. p${words - 1}}, $reference"

private fun flowAccessors(): List<FlowAccessor> = listOf(

    FlowAccessor("extensionEditor", field = GboardVersionBindings.flowExtensionEditor),
    FlowAccessor("extensionCoordinator", field = GboardVersionBindings.flowExtensionCoordinator),
    FlowAccessor("active", field = GboardVersionBindings.flowActive),
    FlowAccessor("color", field = GboardVersionBindings.flowColor),
    FlowAccessor("actions", field = GboardVersionBindings.flowActions),
    FlowAccessor("payload", field = GboardVersionBindings.flowPayload),
    FlowAccessor("eventKeyCode", field = GboardVersionBindings.flowEventKeyCode),
    FlowAccessor("requestType", field = GboardVersionBindings.flowRequestType),
    FlowAccessor("javaState", field = GboardVersionBindings.flowJavaState),
    FlowAccessor("currentProcessor", field = GboardVersionBindings.flowCurrentProcessor),
    FlowAccessor("prefilterCid", field = GboardVersionBindings.flowPrefilterCid),
    FlowAccessor("prefilterModule", field = GboardVersionBindings.flowPrefilterModule),
    FlowAccessor("prefilterRequest", field = GboardVersionBindings.flowPrefilterRequest),
    FlowAccessor("continuationCid", field = GboardVersionBindings.flowContinuationCid),
    FlowAccessor("continuationModule", field = GboardVersionBindings.flowContinuationModule),
    FlowAccessor("continuationRequest", field = GboardVersionBindings.flowContinuationRequest),
    FlowAccessor("continuationProcessor", field = GboardVersionBindings.flowContinuationProcessor),
    FlowAccessor("taskProcessor", field = GboardVersionBindings.flowTaskProcessor),
    FlowAccessor("nativeResponseCid", field = GboardVersionBindings.flowNativeResponseCid),
    FlowAccessor("nativeResponseProcessor", field = GboardVersionBindings.flowNativeResponseProcessor),
    FlowAccessor("nativeFilterProcessor", field = GboardVersionBindings.flowNativeFilterProcessor),
    FlowAccessor("setterCid", field = GboardVersionBindings.flowSetterCid),
    FlowAccessor("setterProcessor", field = GboardVersionBindings.flowSetterProcessor),
    FlowAccessor("processorPointer", field = GboardVersionBindings.flowProcessorPointer),
    FlowAccessor("processorQueue", field = GboardVersionBindings.flowProcessorQueue),
    FlowAccessor("providerKind", field = GboardVersionBindings.flowProviderKind),
    FlowAccessor("flowFlag", field = GboardVersionBindings.flowFlowFlag, staticExpected = true),
    FlowAccessor("percentileFlag", field = GboardVersionBindings.flowPercentileFlag, staticExpected = true),
    FlowAccessor("alphaFlag", field = GboardVersionBindings.flowAlphaFlag, staticExpected = true),
    FlowAccessor("exclusionFlag", field = GboardVersionBindings.flowExclusionFlag, staticExpected = true),
    FlowAccessor("spatialStats", field = GboardVersionBindings.flowSpatialStats, staticExpected = true),
    FlowAccessor("histogram", field = GboardVersionBindings.flowHistogram),
    FlowAccessor("suppressionStatus", field = GboardVersionBindings.flowSuppressionStatus, staticExpected = true),
    FlowAccessor("successStatus", field = GboardVersionBindings.flowSuccessStatus, staticExpected = true),
    FlowAccessor("keyboardContext", method = GboardVersionBindings.flowKeyboardContext),
    FlowAccessor("moduleContext", method = GboardVersionBindings.flowModuleContext),
    FlowAccessor("eventAllowed", method = GboardVersionBindings.flowEventAllowed, staticExpected = true),
    FlowAccessor("preferenceInstance", method = GboardVersionBindings.flowPreferenceInstance, staticExpected = true),
    FlowAccessor("preferenceRead", method = GboardVersionBindings.flowPreferenceRead),
    FlowAccessor("flagRead", method = GboardVersionBindings.flowFlagRead),
    FlowAccessor("moduleInstance", method = GboardVersionBindings.flowModuleInstance, staticExpected = true),
    FlowAccessor("moduleRead", method = GboardVersionBindings.flowModuleRead),
    FlowAccessor("cacheRead", method = GboardVersionBindings.flowCacheRead),
    FlowAccessor("cancel", method = GboardVersionBindings.flowCancel),
    FlowAccessor("suppressed", method = GboardVersionBindings.flowSuppressed),
    FlowAccessor("moduleStatus", method = GboardVersionBindings.flowModuleStatus),
    FlowAccessor("infer", method = GboardVersionBindings.flowInfer),
    FlowAccessor("immediate", method = GboardVersionBindings.flowImmediate, staticExpected = true),
    FlowAccessor("queue", method = GboardVersionBindings.flowQueue),
    FlowAccessor("pointerRead", method = GboardVersionBindings.flowPointerRead),
    FlowAccessor("nativeSet", method = GboardVersionBindings.flowNativeSet),
)

private fun installFlowTypeAccessors(context: BytecodePatchContext) {
    val adapter = context.mutableClass(ACCESS_TYPE)
    val types = mapOf(
        "Extension" to GboardVersionBindings.flowEventHook.ownerDescriptor,
        "Payload" to GboardVersionBindings.flowRequestType.ownerDescriptor,
        "Module" to GboardVersionBindings.flowModuleResetHook.ownerDescriptor,
        "Processor" to GboardVersionBindings.flowNativeSet.ownerDescriptor,
        "Request" to GboardVersionBindings.flowModuleRequestHook.parameterTypes.single(),
        "Action" to GboardVersionBindings.flowEventKeyCode.ownerDescriptor,
        "Stats" to GboardVersionBindings.flowHistogram.ownerDescriptor,
    )
    types.forEach { (name, type) ->
        val stub = adapter.methods.single { it.name == "is$name" }
        adapter.methods.remove(stub)
        adapter.addHelperMethodIfMissing(stub.name, stub.parameterTypes.map { it.toString() }, stub.returnType, stub.accessFlags,
            3, "instance-of v0, p1, $type\nreturn v0")
    }
    mapOf("cacheType" to GboardVersionBindings.flowCacheRead.ownerDescriptor,
        "statsType" to GboardVersionBindings.flowHistogram.ownerDescriptor).forEach { (name, type) ->
        val stub = adapter.methods.single { it.name == name }
        adapter.methods.remove(stub)
        adapter.addHelperMethodIfMissing(name, emptyList(), "Ljava/lang/Class;", stub.accessFlags,
            2, "const-class v0, $type\nreturn-object v0")
    }
}

internal data class FlowHook(val role: String, val target: GboardMethodTarget)
internal fun flowHooks(): List<FlowHook> = listOf(
    FlowHook("event", GboardVersionBindings.flowEventHook),
    FlowHook("attach", GboardVersionBindings.flowAttachHook),
    FlowHook("input", GboardVersionBindings.flowInputHook),
    FlowHook("availability", GboardVersionBindings.flowAvailabilityHook),
    FlowHook("deactivate", GboardVersionBindings.flowDeactivateHook),
    FlowHook("close", GboardVersionBindings.flowCloseHook),
    FlowHook("moduleReset", GboardVersionBindings.flowModuleResetHook),
    FlowHook("moduleRequest", GboardVersionBindings.flowModuleRequestHook),
    FlowHook("prefilter", GboardVersionBindings.flowPrefilterHook),
    FlowHook("continuation", GboardVersionBindings.flowContinuationHook),
    FlowHook("postprocess", GboardVersionBindings.flowPostprocessHook),
    FlowHook("nativeResponse", GboardVersionBindings.flowNativeResponseHook),
    FlowHook("stockSetter", GboardVersionBindings.flowStockSetterHook),
    FlowHook("filterProvider", GboardVersionBindings.flowFilterProviderHook),
    FlowHook("delight", GboardVersionBindings.flowDelightHook),
)

private fun installFlowWrapper(context: BytecodePatchContext, hook: FlowHook) {
    installFlowWrapper(hook.target.ownerClass(context), hook)
}

internal fun installFlowWrapper(owner: MutableClass, hook: FlowHook) {
    val stock = hook.target.resolve(owner)
    requireFlowHook(stock, hook)
    val static = stock.accessFlags and AccessFlags.STATIC.value != 0
    val helper = "jasondevFlowStock" + hook.role.replaceFirstChar(Char::uppercaseChar)
    check(owner.methods.none { it.name == helper }) { "Flow helper collision: $helper" }
    val helperFlags = (stock.accessFlags and AccessFlags.PUBLIC.value.inv() and AccessFlags.PROTECTED.value.inv()) or AccessFlags.PRIVATE.value
    owner.methods.add(ImmutableMethod(stock.definingClass, helper, stock.parameters, stock.returnType,
        helperFlags, stock.annotations, stock.hiddenApiRestrictions, stock.implementation).toMutable())
    owner.methods.remove(stock)
    val words = (if (static) 0 else 1) + stock.parameterTypes.sumOf { flowWords(it.toString()) }
    val callStock = flowInvoke(if (static) "invoke-static" else "invoke-direct", words,
        hook.target.referenceNamed(helper))
    val before = flowBefore(hook.role, stock.returnType)
    val after = flowAfter(hook.role)
    val result = if (stock.returnType == "V") "" else "move-result" +
        (if (stock.returnType.startsWith("L") || stock.returnType.startsWith("[")) "-object" else "") + " v0"
    val failureCleanup = if (hook.role == "availability")
        RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_RESET, "p0") else after
    val body = """
        $before
        :flow_stock
        :flow_try_start
        $callStock
        $result
        :flow_try_end
        .catchall {:flow_try_start .. :flow_try_end} :flow_failure
        $after
        ${flowReturn(stock.returnType, "v0")}
        :flow_failure
        move-exception v1
        $failureCleanup
        throw v1
    """.trimIndent()
    owner.addHelperMethodIfMissing(stock.name, stock.parameterTypes.map { it.toString() }, stock.returnType, stock.accessFlags,
        2 + words, body)

}

private fun flowBefore(role: String, returnType: String): String {
    fun call(id: RuntimeCallId, registers: String = "") = RuntimeCallEmitter.invoke(id, registers)
    fun skipBoolean(id: RuntimeCallId, registers: String, skipped: String): String = """
        ${call(id, registers)}
        move-result v0
        if-eqz v0, :flow_stock
        $skipped
    """.trimIndent()
    return when (role) {
        "event" -> skipBoolean(RuntimeCallId.FLOW_BEFORE_EVENT, "p0, p1",
            call(RuntimeCallId.FLOW_AFTER_EVENT) + "\nconst/4 v0, 0x1\nreturn v0")
        "attach", "input", "deactivate", "close" -> call(RuntimeCallId.FLOW_RESET, "p0")
        "moduleReset" -> skipBoolean(RuntimeCallId.FLOW_MODULE_RESET, "p0", "return-void")
        "moduleRequest" -> call(RuntimeCallId.FLOW_MODULE_REQUEST, "p0, p1")
        "prefilter" -> """
            ${call(RuntimeCallId.FLOW_PREFILTER, "p0, p1")}
            move-result-object v0
            if-eqz v0, :flow_stock
            check-cast v0, $returnType
            return-object v0
        """.trimIndent()
        "continuation" -> call(RuntimeCallId.FLOW_ENTER_CONTINUATION, "p0")
        "postprocess" -> call(RuntimeCallId.FLOW_ENTER_POSTPROCESS, "p0")
        "nativeResponse" -> call(RuntimeCallId.FLOW_ENTER_NATIVE, "p0, p1, p2")
        "stockSetter" -> call(RuntimeCallId.FLOW_ENTER_SETTER, "p0")
        "filterProvider" -> skipBoolean(RuntimeCallId.FLOW_EXCLUDE_OOV, "p0", "const/4 v0, 0x0\nreturn-object v0")
        "delight" -> skipBoolean(RuntimeCallId.FLOW_SKIP_OOV, "", "const/4 v0, 0x0\nreturn-object v0")
        else -> ""
    }
}
private fun flowAfter(role: String): String = when (role) {
    "event" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_AFTER_EVENT, "")
    "availability" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_AVAILABILITY, "p0, p1")
    "continuation" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_EXIT_CONTINUATION, "")
    "postprocess" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_EXIT_POSTPROCESS, "")
    "nativeResponse" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_EXIT_NATIVE, "")
    "stockSetter" -> RuntimeCallEmitter.invoke(RuntimeCallId.FLOW_EXIT_SETTER, "")
    else -> ""
}

private fun requireFlowHook(stock: MutableMethod, hook: FlowHook) {
    check(stock.implementation != null && stock.name != "<init>" &&
        ((stock.accessFlags and AccessFlags.STATIC.value != 0) == (hook.role == "delight"))) {
        "Flow stock implementation/modifier drift: ${hook.target.reference}"
    }
}
private fun flowErasedType(type: String): String = when (type) {
    "Landroid/content/Context;", "Landroid/view/inputmethod/EditorInfo;", "Ljava/lang/String;",
    "Ljava/lang/Class;", "Ljava/util/concurrent/Callable;", "Ljava/util/concurrent/atomic/AtomicBoolean;",
    "Ljava/util/Map;" -> type
    else -> if (type.length == 1) type else "Ljava/lang/Object;"
}
