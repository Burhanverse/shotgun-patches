package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert.assertEquals;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertTrue;
import org.junit.Test;

public class WebClipboardMdnsAdvertiserTest {
    @Test
    public void instanceNameIsStableAndNotEmpty() {
        String instanceName = WebClipboardMdnsAdvertiser.resolveInstanceName();
        assertNotNull(instanceName);
        assertTrue(instanceName.length() >= 8);
        assertTrue(instanceName.startsWith("WebClip-"));
    }

    @Test
    public void serviceTypeMatchesExpectedPattern() {
        String serviceType = "_webclip._tcp.";
        assertEquals("_webclip._tcp.", serviceType);
    }

    @Test
    public void txtRecordKeysArePresent() {
        String[] preferenceKeys = new String[]{"v", "code", "token"};
        assertEquals(3, preferenceKeys.length);
    }
}
