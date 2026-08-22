package dev.jason.gboardpatches.patches.gboard.features.shotgunkeyboard

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardShotgunKeyboardFeatureMarkerPatch = resourcePatch(
    description = "標記 shotgun keyboard feature 已被打入 target APK，共用 settings UI 過濾"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(SHOTGUN_KEYBOARD_FEATURE_MARKER_NAME)
    }
}

private const val SHOTGUN_KEYBOARD_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.shotgun_keyboard"
