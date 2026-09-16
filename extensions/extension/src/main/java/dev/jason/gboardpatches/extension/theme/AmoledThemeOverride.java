package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

public final class AmoledThemeOverride {
    private static final String TAG = "GboardPatches";
    private static final int AMOLED_BLACK = 0xFF000000;

    private AmoledThemeOverride() {
    }

    public static boolean isDarkModeActive(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Configuration configuration = context.getResources().getConfiguration();
            int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int[] applyOverride(
            Context context,
            int surfaceContainer,
            int surface,
            int surfaceLow,
            int surfaceVariant) {
        try {
            if (!AmoledThemePreferences.isAmoledEnabled(context)) {
                return new int[] {surfaceContainer, surface, surfaceLow, surfaceVariant};
            }
            if (!isDarkModeActive(context)) {
                return new int[] {surfaceContainer, surface, surfaceLow, surfaceVariant};
            }
            return new int[] {AMOLED_BLACK, AMOLED_BLACK, AMOLED_BLACK, surfaceVariant};
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to apply AMOLED theme override", throwable);
            } catch (Throwable ignored) {
            }
            return new int[] {surfaceContainer, surface, surfaceLow, surfaceVariant};
        }
    }
}
