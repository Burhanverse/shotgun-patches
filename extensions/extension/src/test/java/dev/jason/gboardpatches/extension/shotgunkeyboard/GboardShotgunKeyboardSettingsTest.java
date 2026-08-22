package dev.jason.gboardpatches.extension.shotgunkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;
import android.view.KeyEvent;

@RunWith(RobolectricTestRunner.class)
public class GboardShotgunKeyboardSettingsTest {

    @Test
    public void testDefaultSettings() {
        Context context = RuntimeEnvironment.getApplication();
        GboardShotgunKeyboardSettings.invalidateCache();
        GboardShotgunKeyboardSettings.SettingsSnapshot snapshot =
                GboardShotgunKeyboardSettings.snapshot(context);

        assertNotNull(snapshot);
        assertFalse(snapshot.enabled);
        assertEquals(100, snapshot.volume);
        assertEquals(1.0f, snapshot.volumeMultiplier, 0.01f);
        assertTrue(snapshot.pumpOnSpace);
        assertTrue(snapshot.pumpOnEnter);
        assertTrue(snapshot.pumpOnBackspace);
        assertFalse(snapshot.pumpOnShift);
        assertTrue(snapshot.pumpOnTab);
        assertFalse(snapshot.pumpOnSymbols);
        assertFalse(snapshot.pumpOnGlobe);
    }

    @Test
    public void testSettingsPersistence() {
        Context context = RuntimeEnvironment.getApplication();
        GboardShotgunKeyboardSettings.writeEnabled(context, true);
        GboardShotgunKeyboardSettings.writeVolume(context, 75);
        GboardShotgunKeyboardSettings.writePumpOnSpace(context, false);
        GboardShotgunKeyboardSettings.writePumpOnShift(context, true);

        GboardShotgunKeyboardSettings.SettingsSnapshot snapshot =
                GboardShotgunKeyboardSettings.snapshot(context);

        assertTrue(snapshot.enabled);
        assertEquals(75, snapshot.volume);
        assertEquals(0.75f, snapshot.volumeMultiplier, 0.01f);
        assertFalse(snapshot.pumpOnSpace);
        assertTrue(snapshot.pumpOnShift);
    }

    @Test
    public void testPolicySoundTypeEvaluation() {
        GboardShotgunKeyboardSettings.SettingsSnapshot enabledSettings =
                new GboardShotgunKeyboardSettings.SettingsSnapshot(
                        true, 100, true, true, true, false, true, false, false);

        // Spacebar -> PUMP
        assertEquals(
                GboardShotgunKeyboardPolicy.SoundType.PUMP,
                GboardShotgunKeyboardPolicy.evaluateSoundType(
                        0, KeyEvent.KEYCODE_SPACE, " ", "space_key", enabledSettings));

        // Enter -> PUMP
        assertEquals(
                GboardShotgunKeyboardPolicy.SoundType.PUMP,
                GboardShotgunKeyboardPolicy.evaluateSoundType(
                        0, KeyEvent.KEYCODE_ENTER, "\n", "enter_key", enabledSettings));

        // Backspace -> PUMP
        assertEquals(
                GboardShotgunKeyboardPolicy.SoundType.PUMP,
                GboardShotgunKeyboardPolicy.evaluateSoundType(
                        0, KeyEvent.KEYCODE_DEL, null, "del_key", enabledSettings));

        // Normal letter 'a' -> BLAST
        assertEquals(
                GboardShotgunKeyboardPolicy.SoundType.BLAST,
                GboardShotgunKeyboardPolicy.evaluateSoundType(
                        0, KeyEvent.KEYCODE_A, "a", "key_pos_1", enabledSettings));

        // Disabled settings -> NONE
        GboardShotgunKeyboardSettings.SettingsSnapshot disabledSettings =
                new GboardShotgunKeyboardSettings.SettingsSnapshot(
                        false, 100, true, true, true, false, true, false, false);
        assertEquals(
                GboardShotgunKeyboardPolicy.SoundType.NONE,
                GboardShotgunKeyboardPolicy.evaluateSoundType(
                        0, KeyEvent.KEYCODE_A, "a", "key_pos_1", disabledSettings));
    }

    @Test
    public void testAudioPayloadGeneration() {
        byte[] blast = GboardShotgunAudioPayload.getBlastAudioBytes();
        byte[] pump = GboardShotgunAudioPayload.getPumpAudioBytes();

        assertNotNull(blast);
        assertTrue(blast.length > 10000);
        assertNotNull(pump);
        assertTrue(pump.length > 10000);
    }
}
