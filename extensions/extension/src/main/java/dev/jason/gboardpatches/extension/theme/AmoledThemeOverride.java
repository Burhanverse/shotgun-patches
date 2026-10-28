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

    public static final int SLOT_BACKGROUND = 0;
    public static final int SLOT_KEY = 1;
    public static final int SLOT_FUNCTION_KEY = 2;

    /**
     * Re-tints one GM3 surface token after the pure-black override. Background scales the
     * stock colour towards black (0 = black, 100 = stock). Keys do the same up to 100 and
     * brighten towards white above it (200 = white).
     */
    public static int adjustColor(int original, int slot) {
        try {
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            if (slot == SLOT_FUNCTION_KEY) {
                return scaleKey(original, AmoledThemePreferences.readTint(preferences,
                        AmoledThemePreferences.PREF_KEY_AMOLED_FUNCTION_KEY_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_FUNCTION_KEY_TINT,
                        AmoledThemePreferences.MAX_AMOLED_KEY_FILL_TINT));
            }
            if (slot == SLOT_KEY) {
                return scaleKey(original, AmoledThemePreferences.readTint(preferences,
                        AmoledThemePreferences.PREF_KEY_AMOLED_KEY_FILL_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_KEY_FILL_TINT,
                        AmoledThemePreferences.MAX_AMOLED_KEY_FILL_TINT));
            }
            return scaleTowardsBlack(original, AmoledThemePreferences.readTint(preferences,
                    AmoledThemePreferences.PREF_KEY_AMOLED_BACKGROUND_TINT,
                    AmoledThemePreferences.DEFAULT_AMOLED_BACKGROUND_TINT, 100));
        } catch (Throwable throwable) {
            return slot == SLOT_BACKGROUND ? AMOLED_BLACK : original;
        }
    }

    /** Mixes the Monet accent into the stock text colour (0 = stock, 100 = accent). */
    public static int blendTextColor(int stock, int accent) {
        try {
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return mix(stock, accent, AmoledThemePreferences.readTint(preferences,
                    AmoledThemePreferences.PREF_KEY_AMOLED_TEXT_TINT,
                    AmoledThemePreferences.DEFAULT_AMOLED_TEXT_TINT, 100));
        } catch (Throwable throwable) {
            return stock;
        }
    }

    static int scaleKey(int original, int tintPercent) {
        if (tintPercent <= 100) {
            return scaleTowardsBlack(original, tintPercent);
        }
        int up = Math.min(100, tintPercent - 100);
        int red = lift((original >> 16) & 0xFF, up);
        int green = lift((original >> 8) & 0xFF, up);
        int blue = lift(original & 0xFF, up);
        return (original & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    static int scaleTowardsBlack(int original, int tintPercent) {
        int tint = Math.max(0, Math.min(100, tintPercent));
        if (tint == 100) {
            return original;
        }
        int red = (((original >> 16) & 0xFF) * tint) / 100;
        int green = (((original >> 8) & 0xFF) * tint) / 100;
        int blue = ((original & 0xFF) * tint) / 100;
        return (original & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    static int mix(int stock, int accent, int accentPercent) {
        int amount = Math.max(0, Math.min(100, accentPercent));
        if (amount == 0) {
            return stock;
        }
        int red = blendChannel((stock >> 16) & 0xFF, (accent >> 16) & 0xFF, amount);
        int green = blendChannel((stock >> 8) & 0xFF, (accent >> 8) & 0xFF, amount);
        int blue = blendChannel(stock & 0xFF, accent & 0xFF, amount);
        return (stock & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int from, int to, int amount) {
        return (from * (100 - amount) + to * amount) / 100;
    }

    private static int lift(int channel, int percent) {
        return channel + ((255 - channel) * percent) / 100;
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
