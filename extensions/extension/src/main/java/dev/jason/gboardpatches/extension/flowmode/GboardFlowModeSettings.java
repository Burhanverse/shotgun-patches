package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardFlowModeSettings {
    public static final String PREF_KEY_ENABLED = "pref_flow_mode_animation_enabled";
    public static final boolean DEFAULT_ENABLED = true;

    private GboardFlowModeSettings() {
    }

    public static boolean readEnabled(Context context) {
        if (context == null || !GboardPatchesSettings.isUserUnlocked(context)) {
            return false;
        }
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        return preferences != null && readEnabled(preferences);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        try {
            return GboardBooleanFlagSettings.readEnabled(
                    preferences,
                    PREF_KEY_ENABLED,
                    DEFAULT_ENABLED);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void ensureDefault(SharedPreferences preferences) {
        try {
            GboardBooleanFlagSettings.ensureDefault(
                    preferences,
                    PREF_KEY_ENABLED,
                    DEFAULT_ENABLED);
        } catch (Throwable ignored) {
        }
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        try {
            return GboardBooleanFlagSettings.writeEnabled(
                    preferences,
                    PREF_KEY_ENABLED,
                    enabled);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
