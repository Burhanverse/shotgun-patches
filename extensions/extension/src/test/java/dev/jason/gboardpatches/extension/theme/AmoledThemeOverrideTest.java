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
        AmoledThemeOverride.setTestOverrideEnabled(null);
        application = RuntimeEnvironment.getApplication();
        GboardPatchesSettings.preferences(application).edit().clear().commit();
    }

    @Test
    public void preferencesDefaultToDisabled() {
        assertFalse(AmoledThemePreferences.isAmoledEnabled(application));
        assertFalse(AmoledThemeOverride.isAmoledThemeActive());
    }

    @Test
    public void isAmoledThemeActiveFollowsTestOverride() {
        AmoledThemeOverride.setTestOverrideEnabled(true);
        assertTrue(AmoledThemeOverride.isAmoledThemeActive());

        AmoledThemeOverride.setTestOverrideEnabled(false);
        assertFalse(AmoledThemeOverride.isAmoledThemeActive());

        AmoledThemeOverride.setTestOverrideEnabled(null);
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

    @Test
    public void scaleTowardsBlackKeepsStockAtFullTint() {
        int monet = 0xFF406080;
        assertEquals(0xFF000000, AmoledThemeOverride.scaleTowardsBlack(monet, 0));
        assertEquals(monet, AmoledThemeOverride.scaleTowardsBlack(monet, 100));
        assertEquals(0xFF203040, AmoledThemeOverride.scaleTowardsBlack(monet, 50));
    }

    @Test
    public void scaleKeyBrightensAboveStockAndDarkensBelow() {
        int stock = 0xFF406080;
        assertEquals(stock, AmoledThemeOverride.scaleKey(stock, 100));
        assertEquals(0xFF203040, AmoledThemeOverride.scaleKey(stock, 50));
        assertEquals(0xFFFFFFFF, AmoledThemeOverride.scaleKey(stock, 200));
        assertEquals(0xFF9FAFBF, AmoledThemeOverride.scaleKey(stock, 150));
    }

    @Test
    public void mixBlendsAccentIntoText() {
        int stock = 0xFFE0E0E0;
        int accent = 0xFF2040C0;
        assertEquals(stock, AmoledThemeOverride.mix(stock, accent, 0));
        assertEquals(accent, AmoledThemeOverride.mix(stock, accent, 100));
        assertEquals(0xFF8090D0, AmoledThemeOverride.mix(stock, accent, 50));
    }

    private Context createContextWithNightMode(int nightMode) {
        Configuration config = new Configuration(application.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return application.createConfigurationContext(config);
    }
}
