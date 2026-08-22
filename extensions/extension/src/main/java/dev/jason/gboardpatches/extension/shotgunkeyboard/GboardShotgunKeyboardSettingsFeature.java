package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardShotgunKeyboardSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String settingsSectionTitle;
    private final String volumeTitle;
    private final String volumeSummary;
    private final String pumpKeysSectionTitle;
    private final String pumpKeysSectionSummary;
    private final String pumpSpaceTitle;
    private final String pumpSpaceSummary;
    private final String pumpEnterTitle;
    private final String pumpEnterSummary;
    private final String pumpBackspaceTitle;
    private final String pumpBackspaceSummary;
    private final String pumpShiftTitle;
    private final String pumpShiftSummary;
    private final String pumpTabTitle;
    private final String pumpTabSummary;
    private final String pumpSymbolsTitle;
    private final String pumpSymbolsSummary;
    private final String pumpGlobeTitle;
    private final String pumpGlobeSummary;
    private final String testSoundSectionTitle;
    private final String testBlastTitle;
    private final String testBlastSummary;
    private final String testPumpTitle;
    private final String testPumpSummary;

    public GboardShotgunKeyboardSettingsFeature(Context context) {
        this.entryTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_title);
        this.entrySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_summary);
        this.headerBadge = GboardSettingsText.get(context,
                R.string.gboard_patches_header_badge);
        this.errorTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_error_title);
        this.errorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_error_summary);
        this.enabledTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_enabled_title);
        this.enabledSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_enabled_summary);
        this.settingsSectionTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_section_settings);
        this.volumeTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_volume_title);
        this.volumeSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_volume_summary);
        this.pumpKeysSectionTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_section_pump_keys);
        this.pumpKeysSectionSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_section_pump_keys_summary);
        this.pumpSpaceTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_space_title);
        this.pumpSpaceSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_space_summary);
        this.pumpEnterTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_enter_title);
        this.pumpEnterSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_enter_summary);
        this.pumpBackspaceTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_backspace_title);
        this.pumpBackspaceSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_backspace_summary);
        this.pumpShiftTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_shift_title);
        this.pumpShiftSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_shift_summary);
        this.pumpTabTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_tab_title);
        this.pumpTabSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_tab_summary);
        this.pumpSymbolsTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_symbols_title);
        this.pumpSymbolsSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_symbols_summary);
        this.pumpGlobeTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_globe_title);
        this.pumpGlobeSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_pump_globe_summary);
        this.testSoundSectionTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_test_sound_title);
        this.testBlastTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_test_blast_title);
        this.testBlastSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_test_blast_summary);
        this.testPumpTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_test_pump_title);
        this.testPumpSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_shotgun_keyboard_test_pump_summary);
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
                context,
                GboardPatchesFeatureAvailability.FEATURE_SHOTGUN_KEYBOARD);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            GboardShotgunKeyboardSettings.ensureDefault(preferences);
            GboardShotgunKeyboardSettings.SettingsSnapshot snapshot =
                    GboardShotgunKeyboardSettings.readSnapshot(preferences);

            List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();

            // Section 1: Main Toggle, Volume Slider & Audio Test
            List<GboardPatchesSettingsContract.Row> mainRows = new ArrayList<>();
            mainRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    enabledTitle,
                    enabledSummary,
                    true,
                    snapshot.enabled,
                    value -> {
                        GboardShotgunKeyboardSettings.writeEnabled(context, value);
                        GboardPatchesSettingsContract.refresh(host);
                    }));

            if (snapshot.enabled) {
                mainRows.add(new GboardPatchesSettingsContract.SliderRow(
                        volumeTitle,
                        volumeSummary,
                        true,
                        snapshot.volume,
                        0,
                        100,
                        5,
                        "%",
                        val -> {
                            GboardShotgunKeyboardSettings.writeVolume(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                mainRows.add(new GboardPatchesSettingsContract.CommandRow(
                        testBlastTitle,
                        testBlastSummary,
                        true,
                        () -> GboardShotgunAudioEngine.playBlast(context, snapshot.volumeMultiplier)));

                mainRows.add(new GboardPatchesSettingsContract.CommandRow(
                        testPumpTitle,
                        testPumpSummary,
                        true,
                        () -> GboardShotgunAudioEngine.playPump(context, snapshot.volumeMultiplier)));
            }
            sections.add(new GboardPatchesSettingsContract.Section(settingsSectionTitle, mainRows));

            // Section 2: Pump Sound Key Customizations
            if (snapshot.enabled) {
                List<GboardPatchesSettingsContract.Row> pumpRows = new ArrayList<>();
                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpSpaceTitle,
                        pumpSpaceSummary,
                        true,
                        snapshot.pumpOnSpace,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnSpace(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpEnterTitle,
                        pumpEnterSummary,
                        true,
                        snapshot.pumpOnEnter,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnEnter(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpBackspaceTitle,
                        pumpBackspaceSummary,
                        true,
                        snapshot.pumpOnBackspace,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnBackspace(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpShiftTitle,
                        pumpShiftSummary,
                        true,
                        snapshot.pumpOnShift,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnShift(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpTabTitle,
                        pumpTabSummary,
                        true,
                        snapshot.pumpOnTab,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnTab(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpSymbolsTitle,
                        pumpSymbolsSummary,
                        true,
                        snapshot.pumpOnSymbols,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnSymbols(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                pumpRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        pumpGlobeTitle,
                        pumpGlobeSummary,
                        true,
                        snapshot.pumpOnGlobe,
                        val -> {
                            GboardShotgunKeyboardSettings.writePumpOnGlobe(context, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }));

                sections.add(new GboardPatchesSettingsContract.Section(pumpKeysSectionTitle, pumpRows));
            }

            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    entrySummary,
                    Collections.emptyList(),
                    sections,
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render Shotgun Keyboard settings", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
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
}
