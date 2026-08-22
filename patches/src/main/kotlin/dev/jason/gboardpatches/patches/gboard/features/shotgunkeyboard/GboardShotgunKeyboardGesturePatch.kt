package dev.jason.gboardpatches.patches.gboard.features.shotgunkeyboard

import dev.jason.gboardpatches.patches.gboard.shared.GboardGestureFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardGestureFamilyFeaturePatch

internal val gboardShotgunKeyboardGesturePatch =
    gboardGestureFamilyFeaturePatch(
        description = "在 gesture dispatch 中播放散彈槍擊鍵音效。",
        feature = GboardGestureFamilyFeature.SHOTGUN_KEYBOARD,
    )
