package dev.jason.gboardpatches.extension.webclipboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebClipboardBroadcastAnnouncerTest {
    @Test
    public void payloadContainsDeviceIdVersionAndWebclipFields() {
        WebClipboardBroadcastAnnouncer.AnnouncementConfig config =
                new WebClipboardBroadcastAnnouncer.AnnouncementConfig(
                        "WebClip-deadbeef", 8080, "1234", "token-abcdef", false, 2121);
        String payload = WebClipboardBroadcastAnnouncer.buildPayloadJson(config);
        assertTrue(payload.contains("\"deviceId\":\"WebClip-deadbeef\""));
        assertTrue(payload.contains("\"v\":1"));
        assertTrue(payload.contains("\"webclip\":{"));
        assertTrue(payload.contains("\"port\":8080"));
        assertTrue(payload.contains("\"code\":\"1234\""));
        assertTrue(payload.contains("\"token\":\"token-abcdef\""));
    }

    @Test
    public void payloadOmitsLanFtpWhenDisabled() {
        WebClipboardBroadcastAnnouncer.AnnouncementConfig config =
                new WebClipboardBroadcastAnnouncer.AnnouncementConfig(
                        "WebClip-dev", 8080, "0000", "token", false, 2121);
        String payload = WebClipboardBroadcastAnnouncer.buildPayloadJson(config);
        assertFalse(payload.contains("\"lanftp\""));
    }

    @Test
    public void payloadIncludesLanFtpWhenEnabled() {
        WebClipboardBroadcastAnnouncer.AnnouncementConfig config =
                new WebClipboardBroadcastAnnouncer.AnnouncementConfig(
                        "WebClip-dev", 8080, "0000", "token", true, 2121);
        String payload = WebClipboardBroadcastAnnouncer.buildPayloadJson(config);
        assertTrue(payload.contains("\"lanftp\":{"));
        assertTrue(payload.contains("\"port\":2121"));
        assertTrue(payload.contains("\"enabled\":true"));
    }

    @Test
    public void broadcastPortDoesNotCollideWithHttpOrFtpDefaults() {
        assertTrue(WebClipboardBroadcastAnnouncer.BROADCAST_PORT != 8080);
        assertTrue(WebClipboardBroadcastAnnouncer.BROADCAST_PORT != 2121);
    }
}