package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class AmoledThemePreferences {
    public static final String PREF_KEY_AMOLED_ENABLED = "pref_amoled_pure_black_enabled";
    public static final boolean DEFAULT_AMOLED_ENABLED = false;

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
}
