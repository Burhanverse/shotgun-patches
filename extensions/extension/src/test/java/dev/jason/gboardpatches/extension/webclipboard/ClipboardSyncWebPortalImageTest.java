package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public final class ClipboardSyncWebPortalImageTest {
    private static final String LOOPBACK_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void desktopImagePostAppliesToBridgeAndBroadcasts() throws Exception {
        byte[] testImageBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01 };
        String base64Image = Base64.getEncoder().encodeToString(testImageBytes);

        AtomicReference<byte[]> appliedBytes = new AtomicReference<>();
        AtomicReference<String> appliedMime = new AtomicReference<>();

        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                new ClipboardSyncWebPortal.ClipboardBridge() {
                    @Override
                    public void applyDesktopClipboard(String text) {
                    }

                    @Override
                    public void applyDesktopImage(byte[] imageBytes, String mimeType) {
                        appliedBytes.set(imageBytes);
                        appliedMime.set(mimeType);
                    }
                },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(false, "0000", LOOPBACK_TOKEN));
        portal.start();
        try {
            String jsonBody = "{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\"data:image/png;base64,"
                    + base64Image + "\"}";
            HttpResponse response = request(portal.getPort(), postRequest("/clipboard", jsonBody, "application/json"));
            Assert.assertEquals(200, response.statusCode);
            Assert.assertTrue(response.body.contains("\"ok\":true"));

            Assert.assertNotNull(appliedBytes.get());
            Assert.assertArrayEquals(testImageBytes, appliedBytes.get());
            Assert.assertEquals("image/png", appliedMime.get());

            // Check GET /image/latest?source=web
            HttpResponse imageResponse = request(portal.getPort(), getRequest("/image/latest?source=web"));
            Assert.assertEquals(200, imageResponse.statusCode);
            Assert.assertArrayEquals(testImageBytes, imageResponse.rawBody);
        } finally {
            portal.stop();
        }
    }

    @Test
    public void phoneImageIngressUpdatesStateAndServesLatestImage() throws Exception {
        byte[] testImageBytes = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 };
        String base64Image = Base64.getEncoder().encodeToString(testImageBytes);

        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                new ClipboardSyncWebPortal.ClipboardBridge() {
                    @Override
                    public void applyDesktopClipboard(String text) {
                    }
                },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(false, "0000", LOOPBACK_TOKEN));
        portal.start();
        try {
            String jsonBody = "{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\"" + base64Image + "\"}";
            HttpResponse response = request(portal.getPort(),
                    phoneImageClipboardRequest(jsonBody, LOOPBACK_TOKEN));
            Assert.assertEquals(200, response.statusCode);

            // Verify /state returns hasImage = true
            HttpResponse stateResponse = request(portal.getPort(), getRequest("/state"));
            Assert.assertEquals(200, stateResponse.statusCode);
            Assert.assertTrue(stateResponse.body.contains("\"hasImage\":true"));
            Assert.assertTrue(stateResponse.body.contains("\"type\":\"image\""));

            // Verify /image/latest returns the image bytes
            HttpResponse imageResponse = request(portal.getPort(), getRequest("/image/latest?source=phone"));
            Assert.assertEquals(200, imageResponse.statusCode);
            Assert.assertArrayEquals(testImageBytes, imageResponse.rawBody);
        } finally {
            portal.stop();
        }
    }

    private static String postRequest(String path, String body, String contentType) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        return "POST " + path + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + body;
    }

    private static String getRequest(String path) {
        return "GET " + path + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Connection: close\r\n"
                + "\r\n";
    }

    private static String phoneImageClipboardRequest(String rawBody, String token) {
        byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        return "POST /phone-clipboard HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "X-Loopback-Ingress-Token: " + token + "\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + rawBody;
    }

    private static HttpResponse request(int port, String rawRequest) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            InputStream input = socket.getInputStream();
            String statusLine = readLine(input);
            int statusCode = parseStatusCode(statusLine);

            String headerLine;
            int contentLength = -1;
            while (!(headerLine = readLine(input)).isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring("content-length:".length()).trim());
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
            return new HttpResponse(statusCode, bodyBytes);
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
        final byte[] rawBody;
        final String body;

        HttpResponse(int statusCode, byte[] rawBody) {
            this.statusCode = statusCode;
            this.rawBody = rawBody;
            this.body = new String(rawBody, StandardCharsets.UTF_8);
        }
    }
}
