package dev.jason.gboardpatches.extension.theme;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
public final class AmoledThemeOverrideTest {
    private Application application;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        GboardPatchesSettings.preferences(application).edit().clear().commit();
    }

    @Test
    public void preferencesDefaultToDisabled() {
        assertFalse(AmoledThemePreferences.isAmoledEnabled(application));
    }

    @Test
    public void preferencesToggleRoundTrip() {
        AmoledThemePreferences.setAmoledEnabled(application, true);
        assertTrue(AmoledThemePreferences.isAmoledEnabled(application));

        AmoledThemePreferences.setAmoledEnabled(application, false);
        assertFalse(AmoledThemePreferences.isAmoledEnabled(application));
    }

    @Test
    public void applyOverrideReturnsInputsWhenToggleOff() {
        AmoledThemePreferences.setAmoledEnabled(application, false);
        Context darkContext = createContextWithNightMode(Configuration.UI_MODE_NIGHT_YES);

        int surfaceContainer = 0xFF1E1E1E;
        int surface = 0xFF121212;
        int surfaceLow = 0xFF1A1A1A;
        int surfaceVariant = 0xFF49454F;

        int[] result = AmoledThemeOverride.applyOverride(
                darkContext, surfaceContainer, surface, surfaceLow, surfaceVariant);

        assertArrayEquals(
                new int[] {surfaceContainer, surface, surfaceLow, surfaceVariant},
                result);
    }

    @Test
    public void applyOverrideNoOpsInLightModeEvenWhenToggleOn() {
        AmoledThemePreferences.setAmoledEnabled(application, true);
        Context lightContext = createContextWithNightMode(Configuration.UI_MODE_NIGHT_NO);

        int surfaceContainer = 0xFFF3EDF7;
        int surface = 0xFFFEF7FF;
        int surfaceLow = 0xFFF7F2FA;
        int surfaceVariant = 0xFFE7E0EC;

        int[] result = AmoledThemeOverride.applyOverride(
                lightContext, surfaceContainer, surface, surfaceLow, surfaceVariant);

        assertArrayEquals(
                new int[] {surfaceContainer, surface, surfaceLow, surfaceVariant},
                result);
    }

    @Test
    public void applyOverrideReplacesSurfacesWithPureBlackInDarkModeWhenToggleOn() {
        AmoledThemePreferences.setAmoledEnabled(application, true);
        Context darkContext = createContextWithNightMode(Configuration.UI_MODE_NIGHT_YES);

        int surfaceContainer = 0xFF211F26;
        int surface = 0xFF141218;
        int surfaceLow = 0xFF1D1B20;
        int surfaceVariant = 0xFF49454F;

        int[] result = AmoledThemeOverride.applyOverride(
                darkContext, surfaceContainer, surface, surfaceLow, surfaceVariant);

        int amoledBlack = 0xFF000000;
        assertArrayEquals(
                new int[] {amoledBlack, amoledBlack, amoledBlack, surfaceVariant},
                result);
        assertEquals(surfaceVariant, result[3]);
    }

    @Test
    public void applyOverrideHandlesNullContextSafely() {
        int[] result = AmoledThemeOverride.applyOverride(null, 1, 2, 3, 4);
        assertArrayEquals(new int[] {1, 2, 3, 4}, result);
    }

    private Context createContextWithNightMode(int nightMode) {
        Configuration config = new Configuration(application.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return application.createConfigurationContext(config);
    }
}
