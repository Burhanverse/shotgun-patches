package dev.jason.gboardpatches.patches.gboard.features.theme

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal const val AMOLED_MONET_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.amoled_monet"

internal val gboardAmoledMonetFeatureMarkerPatch = resourcePatch(
    description = "Marks the AMOLED Monet key tint setting as present in the target APK.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(AMOLED_MONET_FEATURE_MARKER)
    }
}
