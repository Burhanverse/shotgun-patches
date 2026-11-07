package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import dev.jason.gboardpatches.extension.filetransfer.ActiveUploadRegistry;
import dev.jason.gboardpatches.extension.filetransfer.FileOffer;
import dev.jason.gboardpatches.extension.filetransfer.FileOfferRegistry;
import dev.jason.gboardpatches.extension.filetransfer.FileUploadHandler;
import dev.jason.gboardpatches.extension.filetransfer.IncomingOfferRegistry;
import dev.jason.gboardpatches.extension.filetransfer.TempFileUploadSink;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public final class ClipboardSyncWebPortalFileTransferTest {
    private static final String LOOPBACK_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final class HttpResponse {
        final int statusCode;
        final Map<String, String> headers = new HashMap<>();
        final byte[] body;

        HttpResponse(int statusCode, Map<String, String> headers, byte[] body) {
            this.statusCode = statusCode;
            this.headers.putAll(headers);
            this.body = body;
        }

        String header(String name) {
            return headers.getOrDefault(name.toLowerCase(java.util.Locale.US), "");
        }

        String bodyString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static HttpResponse sendRequest(int port, String raw) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8));
             InputStream in = socket.getInputStream()) {
            writer.write(raw);
            writer.flush();

            java.io.ByteArrayOutputStream headerStream = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                headerStream.write(b);
                byte[] bytes = headerStream.toByteArray();
                int len = bytes.length;
                if (len >= 4 && bytes[len - 4] == '\r' && bytes[len - 3] == '\n'
                        && bytes[len - 2] == '\r' && bytes[len - 1] == '\n') {
                    break;
                }
            }
            String headerText = headerStream.toString(StandardCharsets.UTF_8);
            String[] lines = headerText.split("\r\n");
            String statusLine = lines.length > 0 ? lines[0] : "";
            int statusCode = 0;
            String[] parts = statusLine.split(" ");
            if (parts.length > 1) {
                statusCode = Integer.parseInt(parts[1]);
            }
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String k = line.substring(0, colon).trim().toLowerCase(java.util.Locale.US);
                    String v = line.substring(colon + 1).trim();
                    headers.put(k, v);
                    if ("content-length".equals(k)) {
                        contentLength = Integer.parseInt(v);
                    }
                }
            }
            byte[] body = contentLength > 0 ? in.readNBytes(contentLength) : new byte[0];
            return new HttpResponse(statusCode, headers, body);
        }
    }

    @Test
    public void missingPairingCodeReturns401OnFilesRoute() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            HttpResponse response = sendRequest(portal.getPort(),
                    "GET /files/some-token HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("pairing_required"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void invalidPairingCodeReturns401OnFilesRoute() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            HttpResponse response = sendRequest(portal.getPort(),
                    "GET /files/some-token?code=9999 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("pairing_required"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void validPairingCodeWithUnknownTokenReturns404() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            HttpResponse response = sendRequest(portal.getPort(),
                    "GET /files/non-existent-token?code=1234 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(404, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("not_found"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void validPairingCodeAndTokenServesFile() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            FileOfferRegistry registry = portal.getFileOfferRegistry();
            String token = FileOfferRegistry.generateToken();
            byte[] fileBytes = "hello-file-content".getBytes(StandardCharsets.UTF_8);
            FileOffer offer = FileOffer.forTestingStream(
                    token,
                    new ByteArrayInputStream(fileBytes),
                    "hello.txt",
                    fileBytes.length,
                    "text/plain",
                    "b-test",
                    0,
                    1,
                    "");
            registry.register(offer);

            HttpResponse response = sendRequest(portal.getPort(),
                    "GET /files/" + token + " HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "x-pairing-code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, response.statusCode);
            Assert.assertEquals("text/plain", response.header("content-type"));
            Assert.assertEquals(String.valueOf(fileBytes.length), response.header("content-length"));
            Assert.assertArrayEquals(fileBytes, response.body);
        } finally {
            portal.stop();
        }
    }

    @Test
    public void broadcastFileOfferTargetsDesktopClientAndExcludesBrowserClient() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(false, "0000", LOOPBACK_TOKEN));
        portal.start();
        try {
            Socket desktopSocket = new Socket("127.0.0.1", portal.getPort());
            BufferedWriter desktopWriter = new BufferedWriter(new OutputStreamWriter(
                    desktopSocket.getOutputStream(), StandardCharsets.UTF_8));
            desktopWriter.write("GET /events?clientId=linux-desktop-abc HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: keep-alive\r\n\r\n");
            desktopWriter.flush();

            Socket browserSocket = new Socket("127.0.0.1", portal.getPort());
            BufferedWriter browserWriter = new BufferedWriter(new OutputStreamWriter(
                    browserSocket.getOutputStream(), StandardCharsets.UTF_8));
            browserWriter.write("GET /events?clientId=web-browser-xyz HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: keep-alive\r\n\r\n");
            browserWriter.flush();

            CountDownLatch desktopLatch = new CountDownLatch(1);
            StringBuilder desktopReceived = new StringBuilder();
            Thread desktopReaderThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            desktopSocket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    boolean seenEvent = false;
                    while ((line = reader.readLine()) != null) {
                        desktopReceived.append(line).append('\n');
                        if (line.contains("event: file")) {
                            seenEvent = true;
                        }
                        if (seenEvent && line.startsWith("data:")) {
                            desktopLatch.countDown();
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
            desktopReaderThread.start();

            AtomicBoolean browserGotFileOffer = new AtomicBoolean(false);
            Thread browserReaderThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            browserSocket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("event: file")) {
                            browserGotFileOffer.set(true);
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
            browserReaderThread.start();

            long deadline = System.currentTimeMillis() + 5000L;
            while (portal.getConnectedClients().size() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            Assert.assertEquals(2, portal.getConnectedClients().size());

            String token = FileOfferRegistry.generateToken();
            FileOffer offer = FileOffer.forTestingStream(
                    token,
                    new ByteArrayInputStream(new byte[0]),
                    "photo.png",
                    1024L,
                    "image/png",
                    "batch-sse",
                    0,
                    1,
                    "");

            portal.broadcastFileOffer(offer);

            Assert.assertTrue(desktopLatch.await(5, TimeUnit.SECONDS));
            Assert.assertTrue(desktopReceived.toString().contains("event: file"));
            Assert.assertTrue(desktopReceived.toString().contains(token));
            Assert.assertTrue(desktopReceived.toString().contains("photo.png"));

            Thread.sleep(200L);
            Assert.assertFalse(browserGotFileOffer.get());

            desktopSocket.close();
            browserSocket.close();
            offer.close();
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalUploadStatusNotFoundReturns404() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();
            HttpResponse response = sendRequest(port,
                    "GET /files/upload/nonexistent HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(404, response.statusCode);
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalUploadCompleteReturns200AndWritesSink() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        File tempFile = File.createTempFile("portal_up_test_", ".part");
        tempFile.deleteOnExit();

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);
        portal.setFileUploadHandler(handler);

        portal.start();
        try {
            int port = portal.getPort();
            String request = "POST /files/upload HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "X-Pairing-Code: 1234\r\n"
                    + "X-Upload-Id: up-portal-1\r\n"
                    + "X-File-Name: doc.txt\r\n"
                    + "X-File-Size: 5\r\n"
                    + "X-File-Offset: 0\r\n"
                    + "Content-Length: 5\r\n"
                    + "Connection: close\r\n\r\n"
                    + "world";
            HttpResponse response = sendRequest(port, request);
            Assert.assertEquals(200, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("\"status\":\"complete\""));
            Assert.assertEquals("world", new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8));
        } finally {
            portal.stop();
            tempFile.delete();
        }
    }

    @Test
    public void portalUploadDisabledReturns403() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> false,
                () -> Long.MAX_VALUE,
                (name, mime) -> null,
                () -> 30);
        portal.setFileUploadHandler(handler);

        portal.start();
        try {
            int port = portal.getPort();
            HttpResponse response = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-portal-disabled\r\n"
                            + "X-File-Name: doc.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "world");
            Assert.assertEquals(403, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("uploads_disabled"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void imageLatestRequiresPairingAndStatusDoesNot() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();

            HttpResponse statusResp = sendRequest(port,
                    "GET /status HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, statusResp.statusCode);
            Assert.assertTrue(statusResp.bodyString().contains("\"pairingRequired\":true"));
            Assert.assertTrue(statusResp.bodyString().contains("\"pairingVerified\":false"));

            HttpResponse imageNoCode = sendRequest(port,
                    "GET /image/latest HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, imageNoCode.statusCode);

            HttpResponse imageWrongCode = sendRequest(port,
                    "GET /image/latest HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 0000\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, imageWrongCode.statusCode);

            HttpResponse imageCorrectCode = sendRequest(port,
                    "GET /image/latest HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(404, imageCorrectCode.statusCode);
            Assert.assertTrue(imageCorrectCode.bodyString().contains("no_image"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalUploadPostPairingAuthGating() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();

            HttpResponse respNoCode = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Expect: 100-continue\r\n"
                            + "Content-Length: 10\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, respNoCode.statusCode);
            Assert.assertEquals(0, portal.getPairingRateLimiter().getFailureCount(java.net.InetAddress.getByName("127.0.0.1")));

            HttpResponse respWrongCode = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 0000\r\n"
                            + "Expect: 100-continue\r\n"
                            + "Content-Length: 10\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, respWrongCode.statusCode);
            Assert.assertEquals(1, portal.getPairingRateLimiter().getFailureCount(java.net.InetAddress.getByName("127.0.0.1")));

            sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 0000\r\n"
                            + "Connection: close\r\n\r\n");
            sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 0000\r\n"
                            + "Connection: close\r\n\r\n");

            HttpResponse respBackoff = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(429, respBackoff.statusCode);
            Assert.assertFalse(respBackoff.header("Retry-After").isEmpty());
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalUploadGetStatusPairingAuthGating() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            int port = portal.getPort();

            HttpResponse respNoCode = sendRequest(port,
                    "GET /files/upload/test-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, respNoCode.statusCode);

            HttpResponse respWrongCode = sendRequest(port,
                    "GET /files/upload/test-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 9999\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(401, respWrongCode.statusCode);
            Assert.assertEquals(1, portal.getPairingRateLimiter().getFailureCount(java.net.InetAddress.getByName("127.0.0.1")));

            sendRequest(port,
                    "GET /files/upload/test-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 9999\r\n"
                            + "Connection: close\r\n\r\n");
            sendRequest(port,
                    "GET /files/upload/test-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 9999\r\n"
                            + "Connection: close\r\n\r\n");

            HttpResponse respBackoff = sendRequest(port,
                    "GET /files/upload/test-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(429, respBackoff.statusCode);
            Assert.assertFalse(respBackoff.header("Retry-After").isEmpty());
        } finally {
            portal.stop();
        }
    }

    @Test
    public void pairingDisabledAllowsRequestsWithoutCodeOnEventsClipboardAndFileUpload() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(false, "1234", LOOPBACK_TOKEN));
        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> null,
                () -> 30);
        portal.setFileUploadHandler(handler);
        portal.start();
        try {
            int port = portal.getPort();

            HttpResponse respState = sendRequest(port,
                    "GET /state HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, respState.statusCode);

            String clipBody = "{\"text\":\"hello\"}";
            HttpResponse respClipboard = sendRequest(port,
                    "POST /clipboard HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + clipBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + clipBody);
            Assert.assertEquals(200, respClipboard.statusCode);

            HttpResponse respUploadStatus = sendRequest(port,
                    "GET /files/upload/not-found-id HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(404, respUploadStatus.statusCode);

            HttpResponse respUpload = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertNotEquals(401, respUpload.statusCode);
            Assert.assertEquals(411, respUpload.statusCode);

            try (Socket socket = new Socket("127.0.0.1", port)) {
                OutputStream out = socket.getOutputStream();
                out.write(("GET /events HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Accept: text/event-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String statusLine = reader.readLine();
                Assert.assertNotNull(statusLine);
                Assert.assertTrue(statusLine.contains("200"));
            }
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileOfferLifecycleAndUploadGating() throws Exception {
        File tempFile = File.createTempFile("webclip-offer-test", ".bin");
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);
        portal.setFileUploadHandler(handler);
        portal.setNotificationsEnabledSupplier(() -> true);

        portal.start();
        try {
            int port = portal.getPort();
            String offerBody = "{\"batchId\":\"batch-lifecycle-1\",\"files\":[{\"name\":\"test.txt\",\"size\":5}],\"totalSize\":5}";
            HttpResponse offerResp = sendRequest(port,
                    "POST /files/offer HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + offerBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + offerBody);
            Assert.assertEquals(200, offerResp.statusCode);
            Assert.assertTrue(offerResp.bodyString().contains("\"state\":\"pending\""));

            HttpResponse statusResp = sendRequest(port,
                    "GET /files/offer/batch-lifecycle-1 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, statusResp.statusCode);
            Assert.assertTrue(statusResp.bodyString().contains("\"state\":\"pending\""));

            HttpResponse unacceptedUpload = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-lifecycle-1\r\n"
                            + "X-File-Name: test.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "X-Batch-Id: batch-lifecycle-1\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "hello");
            Assert.assertEquals(403, unacceptedUpload.statusCode);
            Assert.assertTrue(unacceptedUpload.bodyString().contains("batch_not_accepted"));

            String respondBody = "{\"accept\":true}";
            HttpResponse respondResp = sendRequest(port,
                    "POST /files/offer/batch-lifecycle-1/respond HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + respondBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + respondBody);
            Assert.assertEquals(200, respondResp.statusCode);
            Assert.assertTrue(respondResp.bodyString().contains("\"state\":\"accepted\""));

            HttpResponse acceptedStatusResp = sendRequest(port,
                    "GET /files/offer/batch-lifecycle-1 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, acceptedStatusResp.statusCode);
            Assert.assertTrue(acceptedStatusResp.bodyString().contains("\"state\":\"accepted\""));

            HttpResponse acceptedUpload = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-lifecycle-1\r\n"
                            + "X-File-Name: test.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "X-Batch-Id: batch-lifecycle-1\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "hello");
            Assert.assertEquals(200, acceptedUpload.statusCode);
            Assert.assertTrue(acceptedUpload.bodyString().contains("\"status\":\"complete\""));
        } finally {
            portal.stop();
            tempFile.delete();
        }
    }

    @Test
    public void portalFileOfferNotificationsDisabledReturns409() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.setNotificationsEnabledSupplier(() -> false);

        portal.start();
        try {
            int port = portal.getPort();
            String offerBody = "{\"batchId\":\"batch-notif-off\",\"files\":[{\"name\":\"test.txt\",\"size\":5}],\"totalSize\":5}";
            HttpResponse offerResp = sendRequest(port,
                    "POST /files/offer HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + offerBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + offerBody);
            Assert.assertEquals(409, offerResp.statusCode);
            Assert.assertTrue(offerResp.bodyString().contains("notifications_disabled"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileOfferAutoAcceptWhenAskDisabled() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.setAskReceiveFilesSupplier(() -> false);

        portal.start();
        try {
            int port = portal.getPort();
            String offerBody = "{\"batchId\":\"batch-auto-accept\",\"files\":[{\"name\":\"test.txt\",\"size\":5}],\"totalSize\":5}";
            HttpResponse offerResp = sendRequest(port,
                    "POST /files/offer HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + offerBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + offerBody);
            Assert.assertEquals(200, offerResp.statusCode);
            Assert.assertTrue(offerResp.bodyString().contains("\"state\":\"accepted\""));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileOfferExpiryReturnsExpiredAndRejectsUpload() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> null,
                () -> 30);
        portal.setFileUploadHandler(handler);
        portal.start();
        try {
            int port = portal.getPort();
            String offerBody = "{\"batchId\":\"batch-expire-test\",\"files\":[{\"name\":\"test.txt\",\"size\":5}],\"totalSize\":5}";
            HttpResponse offerResp = sendRequest(port,
                    "POST /files/offer HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + offerBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + offerBody);
            Assert.assertEquals(200, offerResp.statusCode);

            IncomingOfferRegistry.IncomingOffer offer = portal.getIncomingOfferRegistry().get("batch-expire-test");
            Assert.assertNotNull(offer);
            offer.setState(IncomingOfferRegistry.OfferState.EXPIRED);

            HttpResponse statusResp = sendRequest(port,
                    "GET /files/offer/batch-expire-test HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, statusResp.statusCode);
            Assert.assertTrue(statusResp.bodyString().contains("\"state\":\"expired\""));

            HttpResponse uploadResp = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-expired-1\r\n"
                            + "X-File-Name: test.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "X-Batch-Id: batch-expire-test\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "hello");
            Assert.assertEquals(403, uploadResp.statusCode);
            Assert.assertTrue(uploadResp.bodyString().contains("batch_not_accepted"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileUploadSinkFailureReturns500() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> {
                    throw new java.io.IOException("Failed to insert MediaStore record");
                },
                () -> 30);
        portal.setFileUploadHandler(handler);
        portal.setAskReceiveFilesSupplier(() -> false);

        portal.start();
        try {
            int port = portal.getPort();
            HttpResponse response = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-sink-fail\r\n"
                            + "X-File-Name: doc.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "world");
            Assert.assertEquals(500, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("sink_creation_failed"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileUploadSinkStorageFailureReturns507() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> {
                    throw new java.io.IOException("No storage space available");
                },
                () -> 30);
        portal.setFileUploadHandler(handler);
        portal.setAskReceiveFilesSupplier(() -> false);

        portal.start();
        try {
            int port = portal.getPort();
            HttpResponse response = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-sink-storage\r\n"
                            + "X-File-Name: doc.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "world");
            Assert.assertEquals(507, response.statusCode);
            Assert.assertTrue(response.bodyString().contains("insufficient_storage"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void portalFileOfferAcceptViaRegistryEnablesUpload() throws Exception {
        File tempFile = File.createTempFile("webclip-action-test", ".bin");
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));

        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);
        portal.setFileUploadHandler(handler);

        portal.start();
        try {
            int port = portal.getPort();
            String offerBody = "{\"batchId\":\"batch-action-accept\",\"files\":[{\"name\":\"test.txt\",\"size\":5}],\"totalSize\":5}";
            HttpResponse offerResp = sendRequest(port,
                    "POST /files/offer HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + offerBody.length() + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + offerBody);
            Assert.assertEquals(200, offerResp.statusCode);
            Assert.assertTrue(offerResp.bodyString().contains("\"state\":\"pending\""));

            portal.getIncomingOfferRegistry().accept("batch-action-accept");

            HttpResponse statusResp = sendRequest(port,
                    "GET /files/offer/batch-action-accept HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(200, statusResp.statusCode);
            Assert.assertTrue(statusResp.bodyString().contains("\"state\":\"accepted\""));

            HttpResponse uploadResp = sendRequest(port,
                    "POST /files/upload HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "X-Pairing-Code: 1234\r\n"
                            + "X-Upload-Id: up-action-accept-1\r\n"
                            + "X-File-Name: test.txt\r\n"
                            + "X-File-Size: 5\r\n"
                            + "X-File-Offset: 0\r\n"
                            + "X-Batch-Id: batch-action-accept\r\n"
                            + "Content-Length: 5\r\n"
                            + "Connection: close\r\n\r\n"
                            + "hello");
            Assert.assertEquals(200, uploadResp.statusCode);
            Assert.assertTrue(uploadResp.bodyString().contains("\"status\":\"complete\""));
        } finally {
            portal.stop();
            tempFile.delete();
        }
    }
}
