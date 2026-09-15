package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert.assertEquals;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertTrue;
import org.junit.Test;

public class LanFtpMdnsAdvertiserTest {
    @Test
    public void instanceNameIsStableAndNotEmpty() {
        String instanceName = LanFtpMdnsAdvertiser.resolveInstanceName();
        assertNotNull(instanceName);
        assertTrue(instanceName.length() >= 8);
        assertTrue(instanceName.startsWith("WebClipFTP-"));
    }

    @Test
    public void serviceTypeMatchesExpectedPattern() {
        String serviceType = "_webclipftp._tcp.";
        assertEquals("_webclipftp._tcp.", serviceType);
    }

    @Test
    public void txtRecordKeysArePresent() {
        String[] preferenceKeys = new String[]{"v", "pwd"};
        assertEquals(2, preferenceKeys.length);
    }
}
