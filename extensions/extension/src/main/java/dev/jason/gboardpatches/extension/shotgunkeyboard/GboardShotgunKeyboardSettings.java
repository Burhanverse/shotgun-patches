package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardShotgunKeyboardSettings {
    public static final String PREF_KEY_ENABLED = "shotgun_keyboard_enabled";
    public static final String PREF_KEY_VOLUME = "shotgun_keyboard_volume";
    public static final String PREF_KEY_PUMP_ON_SPACE = "shotgun_keyboard_pump_on_space";
    public static final String PREF_KEY_PUMP_ON_ENTER = "shotgun_keyboard_pump_on_enter";
    public static final String PREF_KEY_PUMP_ON_BACKSPACE = "shotgun_keyboard_pump_on_backspace";
    public static final String PREF_KEY_PUMP_ON_SHIFT = "shotgun_keyboard_pump_on_shift";
    public static final String PREF_KEY_PUMP_ON_TAB = "shotgun_keyboard_pump_on_tab";
    public static final String PREF_KEY_PUMP_ON_SYMBOLS = "shotgun_keyboard_pump_on_symbols";
    public static final String PREF_KEY_PUMP_ON_GLOBE = "shotgun_keyboard_pump_on_globe";

    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_VOLUME = 100;
    public static final boolean DEFAULT_PUMP_ON_SPACE = true;
    public static final boolean DEFAULT_PUMP_ON_ENTER = true;
    public static final boolean DEFAULT_PUMP_ON_BACKSPACE = true;
    public static final boolean DEFAULT_PUMP_ON_SHIFT = false;
    public static final boolean DEFAULT_PUMP_ON_TAB = true;
    public static final boolean DEFAULT_PUMP_ON_SYMBOLS = false;
    public static final boolean DEFAULT_PUMP_ON_GLOBE = false;

    private static volatile SettingsSnapshot cachedSnapshot;

    private GboardShotgunKeyboardSettings() {
    }

    public static final class SettingsSnapshot {
        public final boolean enabled;
        public final int volume;
        public final float volumeMultiplier;
        public final boolean pumpOnSpace;
        public final boolean pumpOnEnter;
        public final boolean pumpOnBackspace;
        public final boolean pumpOnShift;
        public final boolean pumpOnTab;
        public final boolean pumpOnSymbols;
        public final boolean pumpOnGlobe;

        public SettingsSnapshot(boolean enabled, int volume, boolean pumpOnSpace,
                boolean pumpOnEnter, boolean pumpOnBackspace, boolean pumpOnShift,
                boolean pumpOnTab, boolean pumpOnSymbols, boolean pumpOnGlobe) {
            this.enabled = enabled;
            this.volume = Math.max(0, Math.min(100, volume));
            this.volumeMultiplier = this.volume / 100.0f;
            this.pumpOnSpace = pumpOnSpace;
            this.pumpOnEnter = pumpOnEnter;
            this.pumpOnBackspace = pumpOnBackspace;
            this.pumpOnShift = pumpOnShift;
            this.pumpOnTab = pumpOnTab;
            this.pumpOnSymbols = pumpOnSymbols;
            this.pumpOnGlobe = pumpOnGlobe;
        }
    }

    public static SettingsSnapshot snapshot(Context context) {
        SettingsSnapshot current = cachedSnapshot;
        if (current != null) {
            return current;
        }
        Context effectiveContext = context != null ? context : GboardShotgunKeyboardRuntime.resolveApplicationContext();
        if (effectiveContext == null) {
            return new SettingsSnapshot(DEFAULT_ENABLED, DEFAULT_VOLUME, DEFAULT_PUMP_ON_SPACE,
                    DEFAULT_PUMP_ON_ENTER, DEFAULT_PUMP_ON_BACKSPACE, DEFAULT_PUMP_ON_SHIFT,
                    DEFAULT_PUMP_ON_TAB, DEFAULT_PUMP_ON_SYMBOLS, DEFAULT_PUMP_ON_GLOBE);
        }
        SharedPreferences preferences = GboardPatchesSettings.preferences(effectiveContext);
        SettingsSnapshot created = readSnapshot(preferences);
        cachedSnapshot = created;
        return created;
    }

    public static SettingsSnapshot readSnapshot(SharedPreferences preferences) {
        if (preferences == null) {
            return new SettingsSnapshot(DEFAULT_ENABLED, DEFAULT_VOLUME, DEFAULT_PUMP_ON_SPACE,
                    DEFAULT_PUMP_ON_ENTER, DEFAULT_PUMP_ON_BACKSPACE, DEFAULT_PUMP_ON_SHIFT,
                    DEFAULT_PUMP_ON_TAB, DEFAULT_PUMP_ON_SYMBOLS, DEFAULT_PUMP_ON_GLOBE);
        }
        return new SettingsSnapshot(
                preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED),
                preferences.getInt(PREF_KEY_VOLUME, DEFAULT_VOLUME),
                preferences.getBoolean(PREF_KEY_PUMP_ON_SPACE, DEFAULT_PUMP_ON_SPACE),
                preferences.getBoolean(PREF_KEY_PUMP_ON_ENTER, DEFAULT_PUMP_ON_ENTER),
                preferences.getBoolean(PREF_KEY_PUMP_ON_BACKSPACE, DEFAULT_PUMP_ON_BACKSPACE),
                preferences.getBoolean(PREF_KEY_PUMP_ON_SHIFT, DEFAULT_PUMP_ON_SHIFT),
                preferences.getBoolean(PREF_KEY_PUMP_ON_TAB, DEFAULT_PUMP_ON_TAB),
                preferences.getBoolean(PREF_KEY_PUMP_ON_SYMBOLS, DEFAULT_PUMP_ON_SYMBOLS),
                preferences.getBoolean(PREF_KEY_PUMP_ON_GLOBE, DEFAULT_PUMP_ON_GLOBE));
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor = preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_VOLUME)) {
            if (editor == null) editor = preferences.edit();
            editor.putInt(PREF_KEY_VOLUME, DEFAULT_VOLUME);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_SPACE)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_SPACE, DEFAULT_PUMP_ON_SPACE);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_ENTER)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_ENTER, DEFAULT_PUMP_ON_ENTER);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_BACKSPACE)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_BACKSPACE, DEFAULT_PUMP_ON_BACKSPACE);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_SHIFT)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_SHIFT, DEFAULT_PUMP_ON_SHIFT);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_TAB)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_TAB, DEFAULT_PUMP_ON_TAB);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_SYMBOLS)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_SYMBOLS, DEFAULT_PUMP_ON_SYMBOLS);
        }
        if (!preferences.contains(PREF_KEY_PUMP_ON_GLOBE)) {
            if (editor == null) editor = preferences.edit();
            editor.putBoolean(PREF_KEY_PUMP_ON_GLOBE, DEFAULT_PUMP_ON_GLOBE);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static void writeEnabled(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_ENABLED, value).apply();
        cachedSnapshot = null;
    }

    public static int readVolume(SharedPreferences preferences) {
        return preferences != null ? preferences.getInt(PREF_KEY_VOLUME, DEFAULT_VOLUME) : DEFAULT_VOLUME;
    }

    public static void writeVolume(Context context, int value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putInt(PREF_KEY_VOLUME, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnSpace(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(PREF_KEY_PUMP_ON_SPACE, DEFAULT_PUMP_ON_SPACE);
    }

    public static void writePumpOnSpace(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_SPACE, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnEnter(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(PREF_KEY_PUMP_ON_ENTER, DEFAULT_PUMP_ON_ENTER);
    }

    public static void writePumpOnEnter(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_ENTER, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnBackspace(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(PREF_KEY_PUMP_ON_BACKSPACE, DEFAULT_PUMP_ON_BACKSPACE);
    }

    public static void writePumpOnBackspace(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_BACKSPACE, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnShift(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(PREF_KEY_PUMP_ON_SHIFT, DEFAULT_PUMP_ON_SHIFT);
    }

    public static void writePumpOnShift(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_SHIFT, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnTab(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(PREF_KEY_PUMP_ON_TAB, DEFAULT_PUMP_ON_TAB);
    }

    public static void writePumpOnTab(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_TAB, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnSymbols(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(PREF_KEY_PUMP_ON_SYMBOLS, DEFAULT_PUMP_ON_SYMBOLS);
    }

    public static void writePumpOnSymbols(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_SYMBOLS, value).apply();
        cachedSnapshot = null;
    }

    public static boolean readPumpOnGlobe(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(PREF_KEY_PUMP_ON_GLOBE, DEFAULT_PUMP_ON_GLOBE);
    }

    public static void writePumpOnGlobe(Context context, boolean value) {
        if (context == null) return;
        GboardPatchesSettings.preferences(context).edit().putBoolean(PREF_KEY_PUMP_ON_GLOBE, value).apply();
        cachedSnapshot = null;
    }

    public static void invalidateCache() {
        cachedSnapshot = null;
    }
}
