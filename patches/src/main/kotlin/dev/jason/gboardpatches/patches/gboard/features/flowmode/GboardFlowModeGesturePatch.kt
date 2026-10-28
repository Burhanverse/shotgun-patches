package dev.jason.gboardpatches.patches.gboard.features.flowmode

import dev.jason.gboardpatches.patches.gboard.shared.GboardGestureFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardGestureFamilyFeaturePatch

internal val gboardFlowModeGesturePatch =
    gboardGestureFamilyFeaturePatch(
        description = "在 gesture dispatch 中觸發高速輸入動畫 (Hyperspeed Typing Animation)。",
        feature = GboardGestureFamilyFeature.FLOW_MODE_ANIMATION,
    )
