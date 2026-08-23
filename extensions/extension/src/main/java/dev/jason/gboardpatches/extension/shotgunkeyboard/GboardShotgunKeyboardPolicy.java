package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.view.KeyEvent;

public final class GboardShotgunKeyboardPolicy {

    public enum SoundType {
        NONE,
        BLAST,
        PUMP
    }

    private GboardShotgunKeyboardPolicy() {
    }

    public static SoundType evaluateSoundType(
            int keyId,
            int keycode,
            String pressText,
            String keyName,
            GboardShotgunKeyboardSettings.SettingsSnapshot settings) {
        return evaluateSoundType(keyId, keycode, pressText, keyName, settings, false);
    }

    public static SoundType evaluateSoundType(
            int keyId,
            int keycode,
            String pressText,
            String keyName,
            GboardShotgunKeyboardSettings.SettingsSnapshot settings,
            boolean isExternalAudioConnected) {

        if (settings == null || !settings.enabled) {
            return SoundType.NONE;
        }

        if (isMutedByOutputDevice(settings.muteOnHeadphones, isExternalAudioConnected)) {
            return SoundType.NONE;
        }

        if (isSpaceKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnSpace ? SoundType.PUMP : SoundType.BLAST;
        }

        if (isEnterKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnEnter ? SoundType.PUMP : SoundType.BLAST;
        }

        if (isBackspaceKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnBackspace ? SoundType.PUMP : SoundType.BLAST;
        }

        if (isShiftKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnShift ? SoundType.PUMP : SoundType.NONE;
        }

        if (isTabKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnTab ? SoundType.PUMP : SoundType.BLAST;
        }

        if (isSymbolsKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnSymbols ? SoundType.PUMP : SoundType.BLAST;
        }

        if (isGlobeKey(keyId, keycode, pressText, keyName)) {
            return settings.pumpOnGlobe ? SoundType.PUMP : SoundType.BLAST;
        }

        // Default all other valid typing / character / symbol keys to BLAST
        return SoundType.BLAST;
    }

    public static boolean isMutedByOutputDevice(boolean muteOnHeadphones, boolean isExternalAudioConnected) {
        return muteOnHeadphones && isExternalAudioConnected;
    }

    public static boolean isSpaceKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_SPACE) {
            return true;
        }
        if (" ".equals(pressText)) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("space")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnterKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_ENTER || keycode == KeyEvent.KEYCODE_NUMPAD_ENTER || keycode == -10003) {
            return true;
        }
        if ("\n".equals(pressText) || "\r\n".equals(pressText)) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("enter") || lower.contains("ime_action") || lower.contains("action_key")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBackspaceKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_DEL || keycode == KeyEvent.KEYCODE_FORWARD_DEL) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("del") || lower.contains("backspace")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isShiftKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_SHIFT_LEFT || keycode == KeyEvent.KEYCODE_SHIFT_RIGHT || keycode == KeyEvent.KEYCODE_CAPS_LOCK) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("shift") || lower.contains("caps")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTabKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_TAB) {
            return true;
        }
        if ("\t".equals(pressText)) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("tab")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSymbolsKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == -10004 || keycode == -10005) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("symbol") || lower.contains("switch_to_more") || lower.contains("switch_to_symbol")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGlobeKey(int keyId, int keycode, String pressText, String keyName) {
        if (keycode == KeyEvent.KEYCODE_LANGUAGE_SWITCH || keycode == -10002) {
            return true;
        }
        if (keyName != null) {
            String lower = keyName.toLowerCase();
            if (lower.contains("globe") || lower.contains("language")) {
                return true;
            }
        }
        return false;
    }
}
