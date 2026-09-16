package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardAmoledThemeSettingsFeature
        extends GboardBooleanFlagSettingsFeature {

    public GboardAmoledThemeSettingsFeature(Context context) {
        super(
                GboardPatchesFeatureAvailability.FEATURE_AMOLED_THEME,
                text(context, R.string.gboard_patches_amoled_theme_title),
                text(context, R.string.gboard_patches_amoled_theme_summary),
                text(context, R.string.gboard_patches_amoled_theme_toggle_title),
                text(context, R.string.gboard_patches_header_badge),
                text(context, R.string.gboard_patches_amoled_theme_error_title),
                text(context, R.string.gboard_patches_amoled_theme_error_summary),
                text(context, R.string.gboard_patches_amoled_theme_section),
                null,
                new SettingsStore() {
                    @Override
                    public void ensureDefault(SharedPreferences preferences) {
                        AmoledThemePreferences.ensureDefault(preferences);
                    }

                    @Override
                    public boolean readEnabled(SharedPreferences preferences) {
                        return AmoledThemePreferences.isAmoledEnabled(preferences);
                    }

                    @Override
                    public boolean writeEnabled(
                            SharedPreferences preferences,
                            boolean enabled) {
                        return AmoledThemePreferences.setAmoledEnabled(preferences, enabled);
                    }
                });
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
