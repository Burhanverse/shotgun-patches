package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class PairingRateLimiterTest {
    private static final String LOOPBACK_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void countingFailedAttemptsDoesNotBlockBelowThreshold() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        InetAddress client = InetAddress.getByName("192.168.1.100");
        long baseTime = 1_000_000L;

        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, baseTime));
        Assert.assertEquals(0, limiter.getFailureCount(client));

        limiter.recordFailure(client, baseTime);
        Assert.assertEquals(1, limiter.getFailureCount(client));
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, baseTime));

        limiter.recordFailure(client, baseTime + 100);
        Assert.assertEquals(2, limiter.getFailureCount(client));
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, baseTime + 100));
    }

    @Test
    public void exponentialBackoffAndCapAtSixtySeconds() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        InetAddress client = InetAddress.getByName("10.0.0.5");
        long time = 2_000_000L;

        limiter.recordFailure(client, time);
        time += 10L;
        limiter.recordFailure(client, time);
        time += 10L;
        limiter.recordFailure(client, time);

        Assert.assertEquals(3, limiter.getFailureCount(client));
        Assert.assertEquals(1L, limiter.getRetryAfterSeconds(client, time));

        time += 1_001L;
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, time));

        limiter.recordFailure(client, time);
        Assert.assertEquals(4, limiter.getFailureCount(client));
        Assert.assertEquals(2L, limiter.getRetryAfterSeconds(client, time));

        time += 2_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(5, limiter.getFailureCount(client));
        Assert.assertEquals(4L, limiter.getRetryAfterSeconds(client, time));

        time += 4_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(6, limiter.getFailureCount(client));
        Assert.assertEquals(8L, limiter.getRetryAfterSeconds(client, time));

        time += 8_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(7, limiter.getFailureCount(client));
        Assert.assertEquals(16L, limiter.getRetryAfterSeconds(client, time));

        time += 16_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(8, limiter.getFailureCount(client));
        Assert.assertEquals(32L, limiter.getRetryAfterSeconds(client, time));

        time += 32_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(9, limiter.getFailureCount(client));
        Assert.assertEquals(60L, limiter.getRetryAfterSeconds(client, time));

        time += 60_001L;
        limiter.recordFailure(client, time);
        Assert.assertEquals(10, limiter.getFailureCount(client));
        Assert.assertEquals(60L, limiter.getRetryAfterSeconds(client, time));
    }

    @Test
    public void successResetsFailureCountAndClearsBlock() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        InetAddress client = InetAddress.getByName("172.16.0.42");
        long time = 3_000_000L;

        limiter.recordFailure(client, time);
        limiter.recordFailure(client, time + 10);
        limiter.recordFailure(client, time + 20);
        Assert.assertTrue(limiter.getRetryAfterSeconds(client, time + 20) > 0);

        limiter.recordSuccess(client);
        Assert.assertEquals(0, limiter.getFailureCount(client));
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, time + 20));

        limiter.recordFailure(client, time + 30);
        Assert.assertEquals(1, limiter.getFailureCount(client));
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(client, time + 30));
    }

    @Test
    public void loopbackAddressIsCountedAndRateLimited() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        InetAddress ipv4Loopback = InetAddress.getByName("127.0.0.1");
        InetAddress ipv6Loopback = InetAddress.getByName("::1");
        long time = 4_000_000L;

        limiter.recordFailure(ipv4Loopback, time);
        limiter.recordFailure(ipv4Loopback, time + 1);
        limiter.recordFailure(ipv4Loopback, time + 2);
        Assert.assertEquals(1L, limiter.getRetryAfterSeconds(ipv4Loopback, time + 2));

        limiter.recordFailure(ipv6Loopback, time);
        limiter.recordFailure(ipv6Loopback, time + 1);
        limiter.recordFailure(ipv6Loopback, time + 2);
        Assert.assertEquals(1L, limiter.getRetryAfterSeconds(ipv6Loopback, time + 2));
    }

    @Test
    public void boundedMapCapacityDoesNotExceedMaxEntries() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        long time = 5_000_000L;

        for (int i = 0; i < 1100; i++) {
            int b1 = 10 + (i / 65536);
            int b2 = (i / 256) % 256;
            int b3 = i % 256;
            InetAddress address = InetAddress.getByName(b1 + "." + b2 + "." + b3 + ".1");
            limiter.recordFailure(address, time);
        }

        Assert.assertTrue(limiter.getEntryCount() <= PairingRateLimiter.MAX_ENTRIES);
    }

    @Test
    public void expiredEntriesArePruned() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        long startTime = 6_000_000L;

        for (int i = 0; i < 600; i++) {
            InetAddress address = InetAddress.getByName("10.1." + (i / 256) + "." + (i % 256));
            limiter.recordFailure(address, startTime);
        }

        long futureTime = startTime + PairingRateLimiter.ENTRY_EXPIRY_MS + 1000L;
        InetAddress freshAddress = InetAddress.getByName("192.168.99.1");
        limiter.recordFailure(freshAddress, futureTime);

        Assert.assertTrue(limiter.getEntryCount() < 600);
    }

    @Test
    public void globalFailuresPerMinuteCapBlocksUnauthenticatedButExemptsRecentAuth() throws Exception {
        PairingRateLimiter limiter = new PairingRateLimiter();
        long time = 7_000_000L;

        InetAddress authenticatedClient = InetAddress.getByName("192.168.1.50");
        limiter.recordSuccess(authenticatedClient, time);

        InetAddress expiredAuthClient = InetAddress.getByName("192.168.1.60");
        limiter.recordSuccess(expiredAuthClient, time - PairingRateLimiter.RECENT_AUTH_WINDOW_MS - 1000L);

        for (int i = 0; i < PairingRateLimiter.GLOBAL_CAP_FAILURES_PER_MINUTE; i++) {
            InetAddress address = InetAddress.getByName("10.2." + (i / 256) + "." + (i % 256));
            limiter.recordFailure(address, time + (i * 100L));
        }

        long checkTime = time + 2000L;
        InetAddress freshClient = InetAddress.getByName("192.168.1.55");
        Assert.assertEquals(0, limiter.getFailureCount(freshClient));
        Assert.assertTrue(limiter.getRetryAfterSeconds(freshClient, checkTime) > 0);
        Assert.assertTrue(limiter.getRetryAfterSeconds(expiredAuthClient, checkTime) > 0);
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(authenticatedClient, checkTime));

        long afterWindow = time + PairingRateLimiter.GLOBAL_WINDOW_MS + 7000L;
        Assert.assertEquals(0L, limiter.getRetryAfterSeconds(freshClient, afterWindow));
    }

    @Test
    public void missingPairingCodeReturns401WithoutCountingFailure() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "5678", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();
            InetAddress loopback = InetAddress.getByName("127.0.0.1");

            for (int i = 0; i < 5; i++) {
                HttpResponse resp = request(port, getRequest("/state"));
                Assert.assertEquals(401, resp.statusCode);
            }

            Assert.assertEquals(0, portal.getPairingRateLimiter().getFailureCount(loopback));
            Assert.assertEquals(0L, portal.getPairingRateLimiter().getRetryAfterSeconds(loopback));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void requestsRejectedWith429DoNotIncrementOrExtendBackoff() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "5678", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();
            InetAddress loopback = InetAddress.getByName("127.0.0.1");

            request(port, getRequest("/state?code=0001"));
            request(port, getRequest("/state?code=0002"));
            request(port, getRequest("/state?code=0003"));

            Assert.assertEquals(3, portal.getPairingRateLimiter().getFailureCount(loopback));
            long initialRetryAfter = portal.getPairingRateLimiter().getRetryAfterSeconds(loopback);
            Assert.assertTrue(initialRetryAfter > 0);

            for (int i = 0; i < 5; i++) {
                HttpResponse resp = request(port, getRequest("/state?code=9999"));
                Assert.assertEquals(429, resp.statusCode);
            }

            Assert.assertEquals(3, portal.getPairingRateLimiter().getFailureCount(loopback));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void validCodeIsBlockedWhileAddressIsInBackoff() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "5678", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();

            HttpResponse resp1 = request(port, getRequest("/state?code=0000"));
            Assert.assertEquals(401, resp1.statusCode);

            HttpResponse resp2 = request(port, getRequest("/state?code=0001"));
            Assert.assertEquals(401, resp2.statusCode);

            HttpResponse resp3 = request(port, getRequest("/state?code=0002"));
            Assert.assertEquals(401, resp3.statusCode);

            HttpResponse blockedValidResp = request(port, getRequest("/state?code=5678"));
            Assert.assertEquals(429, blockedValidResp.statusCode);
            Assert.assertTrue(blockedValidResp.header("retry-after") != null
                    && !blockedValidResp.header("retry-after").isEmpty());
            Assert.assertTrue(blockedValidResp.body.contains("rate_limited"));

            portal.getPairingRateLimiter().clear();

            HttpResponse unblockedValidResp = request(port, getRequest("/state?code=5678"));
            Assert.assertEquals(200, unblockedValidResp.statusCode);
        } finally {
            portal.stop();
        }
    }

    @Test
    public void exemptLoopbackPhoneClipboardIgnoresPairingBackoff() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "5678", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            portal.getPairingRateLimiter().recordFailure(loopback);
            portal.getPairingRateLimiter().recordFailure(loopback);
            portal.getPairingRateLimiter().recordFailure(loopback);
            Assert.assertTrue(portal.getPairingRateLimiter().getRetryAfterSeconds(loopback) > 0);

            HttpResponse stateResp = request(port, getRequest("/state?code=5678"));
            Assert.assertEquals(429, stateResp.statusCode);

            String body = "text=phone-ingress-test";
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String rawRequest = "POST /phone-clipboard HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Content-Type: application/x-www-form-urlencoded\r\n"
                    + "X-Loopback-Ingress-Token: " + LOOPBACK_TOKEN + "\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + body;
            HttpResponse ingressResp = request(port, rawRequest);
            Assert.assertEquals(200, ingressResp.statusCode);
        } finally {
            portal.stop();
        }
    }

    private static String getRequest(String path) {
        return "GET " + path + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Connection: close\r\n"
                + "\r\n";
    }

    private static HttpResponse request(int port, String rawRequest) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            InputStream input = socket.getInputStream();
            String statusLine = readLine(input);
            int statusCode = parseStatusCode(statusLine);

            java.util.Map<String, String> headers = new java.util.HashMap<>();
            String headerLine;
            int contentLength = -1;
            while (!(headerLine = readLine(input)).isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String name = headerLine.substring(0, colonIndex).trim().toLowerCase(java.util.Locale.US);
                    String val = headerLine.substring(colonIndex + 1).trim();
                    headers.put(name, val);
                    if ("content-length".equals(name)) {
                        contentLength = Integer.parseInt(val);
                    }
                }
            }

            byte[] bodyBytes;
            if (contentLength >= 0) {
                bodyBytes = input.readNBytes(contentLength);
            } else {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int r;
                while ((r = input.read(buffer)) != -1) {
                    out.write(buffer, 0, r);
                }
                bodyBytes = out.toByteArray();
            }
            return new HttpResponse(statusCode, headers, bodyBytes);
        }
    }

    private static String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = input.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static int parseStatusCode(String statusLine) {
        if (statusLine == null) {
            return 0;
        }
        String[] parts = statusLine.split(" ");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    private static final class HttpResponse {
        final int statusCode;
        final java.util.Map<String, String> headers;
        final byte[] rawBody;
        final String body;

        HttpResponse(int statusCode, java.util.Map<String, String> headers, byte[] rawBody) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.rawBody = rawBody;
            this.body = new String(rawBody, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.get(name.toLowerCase(java.util.Locale.US));
        }
    }
}
