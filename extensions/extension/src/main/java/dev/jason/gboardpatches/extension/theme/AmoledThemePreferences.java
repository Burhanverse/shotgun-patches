package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class AmoledThemePreferences {
    public static final String PREF_KEY_AMOLED_ENABLED = "pref_amoled_pure_black_enabled";
    public static final boolean DEFAULT_AMOLED_ENABLED = false;
    public static final String PREF_KEY_AMOLED_KEY_TINT = "pref_amoled_key_tint";
    /** 0 keeps keys pure black (legacy behaviour); 100 keeps the stock Monet surface. */
    public static final int DEFAULT_AMOLED_KEY_TINT = 0;

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

    public static int readKeyTint(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_AMOLED_KEY_TINT;
        }
        try {
            Object raw = preferences.getAll().get(PREF_KEY_AMOLED_KEY_TINT);
            int value = raw instanceof Number number
                    ? number.intValue() : Integer.parseInt(String.valueOf(raw));
            return Math.max(0, Math.min(100, value));
        } catch (Throwable ignored) {
            return DEFAULT_AMOLED_KEY_TINT;
        }
    }

    public static boolean writeKeyTint(SharedPreferences preferences, int tint) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit()
                    .putInt(PREF_KEY_AMOLED_KEY_TINT, Math.max(0, Math.min(100, tint)))
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
