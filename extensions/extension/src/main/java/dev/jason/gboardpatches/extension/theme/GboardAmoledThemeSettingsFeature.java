package dev.jason.gboardpatches.extension.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardAmoledThemeSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String entryTitle;
    private final String entrySummary;
    private final String toggleTitle;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final String backgroundTitle;
    private final String backgroundSummary;
    private final String keyTitle;
    private final String keySummary;
    private final String functionTitle;
    private final String functionSummary;
    private final String textTitle;
    private final String textSummary;

    public GboardAmoledThemeSettingsFeature(Context context) {
        entryTitle = text(context, R.string.gboard_patches_amoled_theme_title);
        entrySummary = text(context, R.string.gboard_patches_amoled_theme_summary);
        toggleTitle = text(context, R.string.gboard_patches_amoled_theme_toggle_title);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        errorTitle = text(context, R.string.gboard_patches_amoled_theme_error_title);
        errorSummary = text(context, R.string.gboard_patches_amoled_theme_error_summary);
        sectionTitle = text(context, R.string.gboard_patches_amoled_theme_section);
        backgroundTitle = text(context, R.string.gboard_patches_amoled_background_tint_title);
        backgroundSummary = text(context, R.string.gboard_patches_amoled_background_tint_summary);
        keyTitle = text(context, R.string.gboard_patches_amoled_key_fill_tint_title);
        keySummary = text(context, R.string.gboard_patches_amoled_key_fill_tint_summary);
        functionTitle = text(context, R.string.gboard_patches_amoled_function_key_tint_title);
        functionSummary = text(context, R.string.gboard_patches_amoled_function_key_tint_summary);
        textTitle = text(context, R.string.gboard_patches_amoled_text_tint_title);
        textSummary = text(context, R.string.gboard_patches_amoled_text_tint_summary);
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_AMOLED_THEME);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            AmoledThemePreferences.ensureDefault(preferences);
            boolean enabled = AmoledThemePreferences.isAmoledEnabled(preferences);
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    toggleTitle, entrySummary, true, enabled, value -> {
                        AmoledThemePreferences.setAmoledEnabled(preferences, value);
                        GboardPatchesSettingsContract.refresh(host);
                    }));
            if (GboardPatchesFeatureAvailability.hasFeature(
                    context, GboardPatchesFeatureAvailability.FEATURE_AMOLED_MONET)) {
                addTintRow(rows, host, preferences, enabled, backgroundTitle, backgroundSummary,
                        AmoledThemePreferences.PREF_KEY_AMOLED_BACKGROUND_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_BACKGROUND_TINT, 100);
                addTintRow(rows, host, preferences, enabled, keyTitle, keySummary,
                        AmoledThemePreferences.PREF_KEY_AMOLED_KEY_FILL_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_KEY_FILL_TINT,
                        AmoledThemePreferences.MAX_AMOLED_KEY_FILL_TINT);
                addTintRow(rows, host, preferences, enabled, functionTitle, functionSummary,
                        AmoledThemePreferences.PREF_KEY_AMOLED_FUNCTION_KEY_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_FUNCTION_KEY_TINT,
                        AmoledThemePreferences.MAX_AMOLED_KEY_FILL_TINT);
                addTintRow(rows, host, preferences, enabled, textTitle, textSummary,
                        AmoledThemePreferences.PREF_KEY_AMOLED_TEXT_TINT,
                        AmoledThemePreferences.DEFAULT_AMOLED_TEXT_TINT, 100);
            }
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    "",
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            sectionTitle, rows)));
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to render AMOLED settings", throwable);
            } catch (Throwable ignored) {
                // Rendering the warning screen is the only remaining action.
            }
            return errorScreen();
        }
    }

    private void addTintRow(
            List<GboardPatchesSettingsContract.Row> rows,
            GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences,
            boolean enabled,
            String title,
            String summary,
            String key,
            int fallback,
            int max) {
        int tint = AmoledThemePreferences.readTint(preferences, key, fallback, max);
        rows.add(new GboardPatchesSettingsContract.SliderRow(
                title, summary, enabled, tint, 0, max, 5, "%",
                value -> {
                    AmoledThemePreferences.writeTint(preferences, key, value, max);
                    GboardPatchesSettingsContract.refresh(host);
                }));
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle,
                        errorSummary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
