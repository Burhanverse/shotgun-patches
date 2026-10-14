package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class AmoledThemeOverride {
    private static final String TAG = "GboardPatches";
    private static final int AMOLED_BLACK = 0xFF000000;

    private static volatile Boolean testOverrideEnabled;

    private AmoledThemeOverride() {
    }

    public static boolean isAmoledThemeActive() {
        if (testOverrideEnabled != null) {
            return testOverrideEnabled;
        }
        try {
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return AmoledThemePreferences.isAmoledEnabled(preferences);
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to check AMOLED theme active state", throwable);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /**
     * Key-fill colour for the AMOLED surface tokens. Tint 0 is pure black (the original
     * behaviour); higher values blend the stock Monet colour back in.
     */
    public static int keyColor(int original) {
        try {
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return blendKeyColor(original, AmoledThemePreferences.readKeyTint(preferences));
        } catch (Throwable throwable) {
            return AMOLED_BLACK;
        }
    }

    static int blendKeyColor(int original, int tintPercent) {
        int tint = Math.max(0, Math.min(100, tintPercent));
        if (tint == 0) {
            return AMOLED_BLACK;
        }
        int red = (((original >> 16) & 0xFF) * tint) / 100;
        int green = (((original >> 8) & 0xFF) * tint) / 100;
        int blue = ((original & 0xFF) * tint) / 100;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public static void setTestOverrideEnabled(Boolean enabled) {
        testOverrideEnabled = enabled;
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
