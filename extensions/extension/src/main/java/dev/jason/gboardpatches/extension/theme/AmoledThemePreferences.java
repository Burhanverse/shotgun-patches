package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class AmoledThemePreferences {
    public static final String PREF_KEY_AMOLED_ENABLED = "pref_amoled_pure_black_enabled";
    public static final boolean DEFAULT_AMOLED_ENABLED = false;
    public static final String PREF_KEY_AMOLED_BACKGROUND_TINT = "pref_amoled_background_tint";
    public static final String PREF_KEY_AMOLED_KEY_FILL_TINT = "pref_amoled_key_fill_tint";
    public static final String PREF_KEY_AMOLED_TEXT_TINT = "pref_amoled_text_tint";
    /** 0 keeps the background pure black (legacy behaviour); 100 keeps the stock Monet surface. */
    public static final int DEFAULT_AMOLED_BACKGROUND_TINT = 0;
    /** 100 keeps the stock key colour; lower values darken it, values above 100 brighten it. */
    public static final int DEFAULT_AMOLED_KEY_FILL_TINT = 100;
    public static final String PREF_KEY_AMOLED_FUNCTION_KEY_TINT = "pref_amoled_function_key_tint";
    /** 100 keeps the stock function-key colour; same 0-200 scale as the letter keys. */
    public static final int DEFAULT_AMOLED_FUNCTION_KEY_TINT = 100;
    public static final int MAX_AMOLED_KEY_FILL_TINT = 200;
    /** 0 keeps the stock text colour; 100 uses the full Monet accent colour. */
    public static final int DEFAULT_AMOLED_TEXT_TINT = 0;

    private AmoledThemePreferences() {
    }

    public static boolean isAmoledEnabled() {
        try {
            SharedPreferences preferences = dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext.preferencesOrNull();
            return isAmoledEnabled(preferences);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAmoledEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return isAmoledEnabled(GboardPatchesSettings.preferences(context));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAmoledEnabled(SharedPreferences preferences) {
        try {
            return GboardBooleanFlagSettings.readEnabled(
                    preferences,
                    PREF_KEY_AMOLED_ENABLED,
                    DEFAULT_AMOLED_ENABLED);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void ensureDefault(SharedPreferences preferences) {
        try {
            GboardBooleanFlagSettings.ensureDefault(
                    preferences,
                    PREF_KEY_AMOLED_ENABLED,
                    DEFAULT_AMOLED_ENABLED);
        } catch (Throwable ignored) {
        }
    }

    public static boolean setAmoledEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        try {
            return setAmoledEnabled(GboardPatchesSettings.preferences(context), enabled);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setAmoledEnabled(SharedPreferences preferences, boolean enabled) {
        try {
            return GboardBooleanFlagSettings.writeEnabled(
                    preferences,
                    PREF_KEY_AMOLED_ENABLED,
                    enabled);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int readTint(
            SharedPreferences preferences, String key, int fallback, int max) {
        if (preferences == null) {
            return fallback;
        }
        try {
            Object raw = preferences.getAll().get(key);
            int value = raw instanceof Number number
                    ? number.intValue() : Integer.parseInt(String.valueOf(raw));
            return Math.max(0, Math.min(max, value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean writeTint(
            SharedPreferences preferences, String key, int tint, int max) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit()
                    .putInt(key, Math.max(0, Math.min(max, tint)))
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
