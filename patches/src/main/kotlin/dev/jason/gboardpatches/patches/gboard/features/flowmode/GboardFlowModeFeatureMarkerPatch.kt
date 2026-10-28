package dev.jason.gboardpatches.patches.gboard.features.flowmode

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardFlowModeFeatureMarkerPatch = resourcePatch(
    description = "標記 flow mode animation feature 已被打入 target APK，共用 settings UI 過濾"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(FLOW_MODE_FEATURE_MARKER_NAME)
    }
}

private const val FLOW_MODE_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.flow_mode_animation"
