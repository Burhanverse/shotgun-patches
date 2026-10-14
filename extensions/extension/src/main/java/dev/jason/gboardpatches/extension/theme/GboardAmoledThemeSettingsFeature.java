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
    private final String tintTitle;
    private final String tintSummary;
    private final String tintHint;

    public GboardAmoledThemeSettingsFeature(Context context) {
        entryTitle = text(context, R.string.gboard_patches_amoled_theme_title);
        entrySummary = text(context, R.string.gboard_patches_amoled_theme_summary);
        toggleTitle = text(context, R.string.gboard_patches_amoled_theme_toggle_title);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        errorTitle = text(context, R.string.gboard_patches_amoled_theme_error_title);
        errorSummary = text(context, R.string.gboard_patches_amoled_theme_error_summary);
        sectionTitle = text(context, R.string.gboard_patches_amoled_theme_section);
        tintTitle = text(context, R.string.gboard_patches_amoled_key_tint_title);
        tintSummary = text(context, R.string.gboard_patches_amoled_key_tint_summary);
        tintHint = text(context, R.string.gboard_patches_amoled_key_tint_hint);
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
            int tint = AmoledThemePreferences.readKeyTint(preferences);
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    toggleTitle, entrySummary, true, enabled, value -> {
                        AmoledThemePreferences.setAmoledEnabled(preferences, value);
                        GboardPatchesSettingsContract.refresh(host);
                    }));
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    tintTitle, tintSummary, tint + "%", enabled,
                    () -> GboardPatchesSettingsContract.showTextInputDialog(host,
                            tintTitle, tintHint, Integer.toString(tint), value -> {
                                try {
                                    AmoledThemePreferences.writeKeyTint(
                                            preferences, Integer.parseInt(value.trim()));
                                    GboardPatchesSettingsContract.refresh(host);
                                } catch (NumberFormatException invalid) {
                                    GboardPatchesSettingsContract.showMessage(host, tintHint);
                                }
                            })));
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
