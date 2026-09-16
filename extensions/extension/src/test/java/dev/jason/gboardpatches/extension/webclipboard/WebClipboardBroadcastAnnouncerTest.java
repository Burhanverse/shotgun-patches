package dev.jason.gboardpatches.extension.webclipboard;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    @Test
    public void filterBroadcastAddressReturnsBroadcastForValidIpv4() throws Exception {
        InetAddress ipv4 = InetAddress.getByName("192.168.1.42");
        InetAddress broadcast = InetAddress.getByName("192.168.1.255");
        assertEquals(broadcast, WebClipboardBroadcastAnnouncer.filterBroadcastAddress(ipv4, broadcast));
    }

    @Test
    public void filterBroadcastAddressReturnsNullForNullBroadcast() throws Exception {
        InetAddress ipv4 = InetAddress.getByName("10.0.0.1");
        assertNull(WebClipboardBroadcastAnnouncer.filterBroadcastAddress(ipv4, null));
    }

    @Test
    public void filterBroadcastAddressReturnsNullForIPv6() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("fe80::1");
        InetAddress broadcast = InetAddress.getByName("ff02::1");
        assertNull(WebClipboardBroadcastAnnouncer.filterBroadcastAddress(ipv6, broadcast));
    }
}