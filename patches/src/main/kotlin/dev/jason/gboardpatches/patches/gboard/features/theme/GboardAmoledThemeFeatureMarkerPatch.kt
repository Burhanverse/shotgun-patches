package dev.jason.gboardpatches.patches.gboard.features.theme

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal const val AMOLED_THEME_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.amoled_theme"

internal val gboardAmoledThemeFeatureMarkerPatch = resourcePatch(
    description = "標記 AMOLED Pure Black feature 已被打入 target APK。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(AMOLED_THEME_FEATURE_MARKER)
    }
}
