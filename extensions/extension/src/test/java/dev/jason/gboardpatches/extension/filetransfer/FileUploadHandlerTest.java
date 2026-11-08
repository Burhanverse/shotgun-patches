package dev.jason.gboardpatches.extension.filetransfer;

import android.content.Context;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public final class FileUploadHandlerTest {
    private static final String LOOPBACK_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final class HttpResponse {
        final int statusCode;
        final String statusLine;
        final Map<String, String> headers = new HashMap<>();
        final byte[] body;

        HttpResponse(int statusCode, String statusLine, Map<String, String> headers, byte[] body) {
            this.statusCode = statusCode;
            this.statusLine = statusLine;
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

    private static HttpResponse readResponse(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
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
        if (headerText.startsWith("HTTP/1.1 100 Continue\r\n\r\n")) {
            headerText = headerText.substring("HTTP/1.1 100 Continue\r\n\r\n".length());
            if (headerText.isEmpty()) {
                headerStream.reset();
                while ((b = in.read()) != -1) {
                    headerStream.write(b);
                    byte[] bytes = headerStream.toByteArray();
                    int len = bytes.length;
                    if (len >= 4 && bytes[len - 4] == '\r' && bytes[len - 3] == '\n'
                            && bytes[len - 2] == '\r' && bytes[len - 1] == '\n') {
                        break;
                    }
                }
                headerText = headerStream.toString(StandardCharsets.UTF_8);
            }
        }
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
                String key = line.substring(0, colon).trim().toLowerCase(java.util.Locale.US);
                String val = line.substring(colon + 1).trim();
                headers.put(key, val);
                if ("content-length".equals(key)) {
                    contentLength = Integer.parseInt(val);
                }
            }
        }
        byte[] body = contentLength > 0 ? in.readNBytes(contentLength) : new byte[0];
        return new HttpResponse(statusCode, statusLine, headers, body);
    }

    private static Map<String, String> readHeadersFromStream(InputStream in) throws Exception {
        ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
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
        String headerText = new String(headerStream.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = headerText.split("\r\n");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                        line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    @Test
    public void nameSanitizing() {
        Assert.assertEquals("file", FileUploadHandler.sanitizeFileName(null));
        Assert.assertEquals("file", FileUploadHandler.sanitizeFileName(""));
        Assert.assertEquals("file", FileUploadHandler.sanitizeFileName("   "));
        Assert.assertEquals("hello world.txt", FileUploadHandler.sanitizeFileName("hello%20world.txt"));
        Assert.assertEquals("foobarbaz.txt", FileUploadHandler.sanitizeFileName("foo/bar\\baz.txt"));
        Assert.assertEquals("etcpasswd", FileUploadHandler.sanitizeFileName("../../etc/passwd"));
        Assert.assertEquals("file", FileUploadHandler.sanitizeFileName(":*?\"<>|"));
        Assert.assertEquals("document.pdf", FileUploadHandler.sanitizeFileName("...document.pdf"));

        String longName = "a".repeat(300) + ".txt";
        String sanitizedLong = FileUploadHandler.sanitizeFileName(longName);
        Assert.assertTrue(sanitizedLong.getBytes(StandardCharsets.UTF_8).length <= 255);
        Assert.assertTrue(sanitizedLong.endsWith(".txt"));
    }

    @Test
    public void completeUploadReturns200() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_complete_", ".part");
        tempFile.deleteOnExit();

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "11");
                    headers.put("x-upload-id", "up-test-1");
                    headers.put("x-file-name", "greeting.txt");
                    headers.put("x-file-size", "11");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                out.write("hello world".getBytes(StandardCharsets.UTF_8));
                out.flush();

                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("\"ok\":true"));
                Assert.assertTrue(response.bodyString().contains("\"status\":\"complete\""));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        Assert.assertEquals("hello world", new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8));
        Assert.assertNull(registry.get("up-test-1"));
        tempFile.delete();
    }

    @Test
    public void partialUploadThenResumeReturns200() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_resume_", ".part");
        tempFile.deleteOnExit();

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch latch1 = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "10");
                    headers.put("x-upload-id", "up-resume-1");
                    headers.put("x-file-name", "digits.txt");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    latch1.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                out.write("12345".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            Assert.assertTrue(latch1.await(5, TimeUnit.SECONDS));
        }

        ActiveUploadRegistry.UploadSession session = registry.get("up-resume-1");
        Assert.assertNotNull(session);
        Assert.assertEquals(5, session.getOffset());
        Assert.assertEquals(10, session.getTotalSize());

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch latch2 = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "5");
                    headers.put("x-upload-id", "up-resume-1");
                    headers.put("x-file-name", "digits.txt");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "5");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    latch2.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                out.write("67890".getBytes(StandardCharsets.UTF_8));
                out.flush();

                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("\"status\":\"complete\""));
            }
            Assert.assertTrue(latch2.await(5, TimeUnit.SECONDS));
        }

        Assert.assertEquals("1234567890", new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8));
        Assert.assertNull(registry.get("up-resume-1"));
        tempFile.delete();
    }

    @Test
    public void offsetMismatchReturns409() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_409_", ".part");
        tempFile.deleteOnExit();

        UploadSink sink = new TempFileUploadSink(tempFile);
        ActiveUploadRegistry.UploadSession session = new ActiveUploadRegistry.UploadSession(
                "up-mismatch", "file.txt", 100L, sink, 40L, System.currentTimeMillis());
        registry.put(session);

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> sink,
                () -> 30);

        Map<String, String> headers = new HashMap<>();
        headers.put("content-length", "80");
        headers.put("x-upload-id", "up-mismatch");
        headers.put("x-file-name", "file.txt");
        headers.put("x-file-size", "100");
        headers.put("x-file-offset", "20");

        AtomicBoolean wrote409 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headers,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 409 && body.contains("\"offset\":40")) {
                        wrote409.set(true);
                    }
                });

        Assert.assertTrue(wrote409.get());
        sink.abort();
    }

    @Test
    public void insufficientStorageReturns507() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> 100L,
                (name, mime) -> new TempFileUploadSink(),
                () -> 30);

        Map<String, String> headers = new HashMap<>();
        headers.put("content-length", "1000");
        headers.put("x-upload-id", "up-storage");
        headers.put("x-file-name", "big.bin");
        headers.put("x-file-size", "1000");
        headers.put("x-file-offset", "0");

        AtomicBoolean wrote507 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headers,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 507 && body.contains("insufficient_storage")) {
                        wrote507.set(true);
                    }
                });

        Assert.assertTrue(wrote507.get());
    }

    @Test
    public void uploadsDisabledReturns403() throws Exception {
        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> false,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(),
                () -> 30);

        Map<String, String> headers = new HashMap<>();
        headers.put("content-length", "10");
        headers.put("x-upload-id", "up-dis");

        AtomicBoolean wrote403 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headers,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 403 && body.contains("uploads_disabled")) {
                        wrote403.set(true);
                    }
                });

        Assert.assertTrue(wrote403.get());
    }

    @Test
    public void unsupportedAndroidVersionReturns403() throws Exception {
        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(),
                () -> 28);

        Map<String, String> headers = new HashMap<>();
        headers.put("content-length", "10");
        headers.put("x-upload-id", "up-old");

        AtomicBoolean wrote403 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headers,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 403 && body.contains("unsupported_android_version")) {
                        wrote403.set(true);
                    }
                });

        Assert.assertTrue(wrote403.get());
    }

    @Test
    public void badContentLengthReturns400Or411() throws Exception {
        FileUploadHandler handler = new FileUploadHandler(
                null,
                new ActiveUploadRegistry(),
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(),
                () -> 30);

        Map<String, String> headersMissing = new HashMap<>();
        headersMissing.put("x-upload-id", "up-bad");

        AtomicBoolean wrote411 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headersMissing,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 411) {
                        wrote411.set(true);
                    }
                });
        Assert.assertTrue(wrote411.get());

        Map<String, String> headersMismatch = new HashMap<>();
        headersMismatch.put("content-length", "50");
        headersMismatch.put("x-upload-id", "up-bad");
        headersMismatch.put("x-file-size", "100");
        headersMismatch.put("x-file-offset", "0");

        AtomicBoolean wrote400 = new AtomicBoolean(false);
        handler.handleUpload(null, new ByteArrayInputStream(new byte[0]), headersMismatch,
                (s, status, reason, ct, body, extra) -> {
                    if (status == 400 && body.contains("content_length_mismatch")) {
                        wrote400.set(true);
                    }
                });
        Assert.assertTrue(wrote400.get());
    }

    @Test
    public void restartAtOffsetZeroReplacesExistingSession() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File oldFile = File.createTempFile("test_old_", ".part");
        oldFile.deleteOnExit();
        TempFileUploadSink oldSink = new TempFileUploadSink(oldFile);

        ActiveUploadRegistry.UploadSession oldSession = new ActiveUploadRegistry.UploadSession(
                "up-restart", "file.txt", 100L, oldSink, 50L, System.currentTimeMillis());
        registry.put(oldSession);

        File newFile = File.createTempFile("test_new_", ".part");
        newFile.deleteOnExit();

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(newFile),
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "4");
                    headers.put("x-upload-id", "up-restart");
                    headers.put("x-file-name", "file.txt");
                    headers.put("x-file-size", "4");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                out.write("done".getBytes(StandardCharsets.UTF_8));
                out.flush();

                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
            }
            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        Assert.assertFalse(oldFile.exists());
        Assert.assertEquals("done", new String(Files.readAllBytes(newFile.toPath()), StandardCharsets.UTF_8));
        newFile.delete();
    }

    @Test
    public void idleExpiryCleanupAbortsSession() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File file = File.createTempFile("test_expiry_", ".part");
        file.deleteOnExit();

        TempFileUploadSink sink = new TempFileUploadSink(file);
        long startTime = 10_000_000L;
        ActiveUploadRegistry.UploadSession session = new ActiveUploadRegistry.UploadSession(
                "up-expire", "expire.txt", 100L, sink, 20L, startTime);
        registry.put(session);

        Assert.assertEquals(1, registry.size());

        registry.cleanExpiredSessions(startTime + 1000L);
        Assert.assertEquals(1, registry.size());
        Assert.assertTrue(file.exists());

        registry.cleanExpiredSessions(startTime + ActiveUploadRegistry.IDLE_EXPIRY_MS + 1000L);
        Assert.assertEquals(0, registry.size());
        Assert.assertFalse(file.exists());
    }

    @Test
    public void concurrentRetryTakesOverWriterAndCompletes() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_takeover_", ".part");
        tempFile.deleteOnExit();

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CountDownLatch server1Started = new CountDownLatch(1);
            CountDownLatch client1SentPartial = new CountDownLatch(1);
            CountDownLatch server1Ended = new CountDownLatch(1);

            new Thread(() -> {
                try (Socket socket = server.accept()) {
                    server1Started.countDown();
                    InputStream in = socket.getInputStream();
                    Map<String, String> headers = readHeadersFromStream(in);
                    handler.handleUpload(socket, in, headers, (s, status, reason, ct, b, h) -> { });
                } catch (Throwable ignored) {
                } finally {
                    server1Ended.countDown();
                }
            }).start();

            Socket client1 = new Socket("127.0.0.1", port);
            OutputStream out1 = client1.getOutputStream();
            String req1Headers = "POST /files/upload HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "X-Upload-Id: up-takeover-1\r\n"
                    + "X-File-Name: doc.bin\r\n"
                    + "X-File-Size: 10\r\n"
                    + "X-File-Offset: 0\r\n"
                    + "Content-Length: 10\r\n"
                    + "Connection: close\r\n\r\n";
            out1.write(req1Headers.getBytes(StandardCharsets.UTF_8));
            out1.write("hello".getBytes(StandardCharsets.UTF_8));
            out1.flush();
            Assert.assertTrue(server1Started.await(5, TimeUnit.SECONDS));

            long deadline = System.currentTimeMillis() + 3000L;
            while (registry.get("up-takeover-1") == null || registry.get("up-takeover-1").getOffset() < 5) {
                if (System.currentTimeMillis() > deadline) {
                    break;
                }
                Thread.sleep(10L);
            }
            Assert.assertNotNull(registry.get("up-takeover-1"));
            Assert.assertEquals(5L, registry.get("up-takeover-1").getOffset());

            CountDownLatch server2Ended = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket socket = server.accept()) {
                    InputStream in = socket.getInputStream();
                    Map<String, String> headers = readHeadersFromStream(in);
                    handler.handleUpload(socket, in, headers, (s, status, reason, ct, b, h) -> {
                        OutputStream out = s.getOutputStream();
                        out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                        out.write(("Content-Length: " + b.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        out.write(b.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    });
                } catch (Throwable ignored) {
                } finally {
                    server2Ended.countDown();
                }
            }).start();

            try (Socket client2 = new Socket("127.0.0.1", port)) {
                OutputStream out2 = client2.getOutputStream();
                String req2 = "POST /files/upload HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "X-Upload-Id: up-takeover-1\r\n"
                        + "X-File-Name: doc.bin\r\n"
                        + "X-File-Size: 10\r\n"
                        + "X-File-Offset: 5\r\n"
                        + "Content-Length: 5\r\n"
                        + "Connection: close\r\n\r\n"
                        + "world";
                out2.write(req2.getBytes(StandardCharsets.UTF_8));
                out2.flush();

                HttpResponse resp2 = readResponse(client2);
                Assert.assertEquals(200, resp2.statusCode);
                Assert.assertTrue(resp2.bodyString().contains("\"status\":\"complete\""));
            }

            Assert.assertTrue(server1Ended.await(5, TimeUnit.SECONDS));
            Assert.assertTrue(server2Ended.await(5, TimeUnit.SECONDS));
            client1.close();

            Assert.assertEquals("helloworld", new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8));
            Assert.assertNull(registry.get("up-takeover-1"));
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void lockControllerAcquiredAndReleasedDuringUpload() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_lock_", ".part");
        tempFile.deleteOnExit();

        FileTransferLockController lockController = new FileTransferLockController(
                null, Executors.newSingleThreadScheduledExecutor());

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                lockController,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> new TempFileUploadSink(tempFile),
                () -> 30);

        Assert.assertEquals(0, lockController.getActiveStreamsCount());

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);

            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    InputStream in = accepted.getInputStream();
                    Map<String, String> headers = readHeadersFromStream(in);
                    handler.handleUpload(accepted, in, headers, (s, status, reason, ct, body, h) -> {
                        s.getOutputStream().write(("HTTP/1.1 " + status + " " + reason + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
                    });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                String request = "POST /files/upload HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "X-Upload-Id: up-lock-1\r\n"
                        + "X-File-Name: locked.txt\r\n"
                        + "X-File-Size: 4\r\n"
                        + "X-File-Offset: 0\r\n"
                        + "Content-Length: 4\r\n"
                        + "Connection: close\r\n\r\n"
                        + "test";
                out.write(request.getBytes(StandardCharsets.UTF_8));
                out.flush();

                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        Assert.assertEquals(0, lockController.getActiveStreamsCount());
        tempFile.delete();
    }

    @Test
    public void cancelledBatchReturns410BeforeBodyRead() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        registry.recordCancelledBatch("batch-cancelled-1");

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                Executors.newSingleThreadScheduledExecutor(),
                () -> true,
                () -> Long.MAX_VALUE,
                (name, mime) -> null,
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);

            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "10");
                    headers.put("x-upload-id", "up-cancelled-1");
                    headers.put("x-file-name", "file.txt");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "0");
                    headers.put("x-batch-id", "batch-cancelled-1");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(410, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("batch_cancelled"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancelUploadAbortsOnlyBatchSessions() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();

        File fileBatchA = File.createTempFile("test_batch_a_", ".part");
        fileBatchA.deleteOnExit();
        TempFileUploadSink sinkA = new TempFileUploadSink(fileBatchA);
        ActiveUploadRegistry.UploadSession sessionA = new ActiveUploadRegistry.UploadSession(
                "up-a", "a.txt", 100L, sinkA, 0L, System.currentTimeMillis(), "batch-a");
        registry.put(sessionA);

        File fileBatchB = File.createTempFile("test_batch_b_", ".part");
        fileBatchB.deleteOnExit();
        TempFileUploadSink sinkB = new TempFileUploadSink(fileBatchB);
        ActiveUploadRegistry.UploadSession sessionB = new ActiveUploadRegistry.UploadSession(
                "up-b", "b.txt", 100L, sinkB, 0L, System.currentTimeMillis(), "batch-b");
        registry.put(sessionB);

        FileTransferNotificationManager notifManager = new FileTransferNotificationManager();
        notifManager.setActiveUploadRegistry(registry);

        notifManager.cancelUpload(null, "batch-a");

        Assert.assertNull(registry.get("up-a"));
        Assert.assertFalse(fileBatchA.exists());

        Assert.assertNotNull(registry.get("up-b"));
        Assert.assertTrue(fileBatchB.exists());

        fileBatchB.delete();
    }

    @Test
    public void completedUploadRememberedAndIdempotent() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_idem_", ".bin");
        tempFile.deleteOnExit();
        TempFileUploadSink sink = new TempFileUploadSink(tempFile);

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                null,
                () -> true,
                () -> 100_000_000L,
                (name, mime) -> sink,
                () -> 30);

        byte[] payload = "0123456789".getBytes(StandardCharsets.UTF_8);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "10");
                    headers.put("x-upload-id", "up-idem-1");
                    headers.put("x-file-name", "idem.bin");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream clientOut = client.getOutputStream();
                clientOut.write(payload);
                clientOut.flush();
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("complete"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleUploadStatus(accepted, "up-idem-1",
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                String body = response.bodyString();
                Assert.assertTrue(body.contains("\"complete\":true"));
                Assert.assertTrue(body.contains("\"offset\":10"));
                Assert.assertTrue(body.contains("\"size\":10"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "10");
                    headers.put("x-upload-id", "up-idem-1");
                    headers.put("x-file-name", "idem.bin");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("complete"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void zeroByteFileUploadPublishesSuccessfully() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        File tempFile = File.createTempFile("test_zero_", ".bin");
        tempFile.deleteOnExit();
        TempFileUploadSink sink = new TempFileUploadSink(tempFile);

        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                null,
                () -> true,
                () -> 100_000_000L,
                (name, mime) -> sink,
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "0");
                    headers.put("x-upload-id", "up-zero-1");
                    headers.put("x-file-name", "zero.bin");
                    headers.put("x-file-size", "0");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("complete"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        Assert.assertTrue(sink.isPublished());
        Assert.assertEquals(0L, tempFile.length());
    }

    @Test
    public void sinkCreationFailureReturns500() throws Exception {
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        FileUploadHandler handler = new FileUploadHandler(
                null,
                registry,
                null,
                null,
                () -> true,
                () -> 100_000_000L,
                (name, mime) -> {
                    throw new java.io.IOException("Failed to create MediaStore entry");
                },
                () -> 30);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "10");
                    headers.put("x-upload-id", "up-fail-sink");
                    headers.put("x-file-name", "fail.bin");
                    headers.put("x-file-size", "10");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(500, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("sink_creation_failed"));
                Assert.assertTrue(response.bodyString().contains("detail"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void directFileUploadSinkLifecycle() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DirectFileUploadSink sink = new DirectFileUploadSink(context, "direct_test.bin");
        try {
            byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
            sink.writeAt(0, data, data.length);
            sink.fsync();
            Assert.assertEquals(data.length, sink.getPosition());
            sink.publish();
            Assert.assertNotNull(sink.getUri());
        } finally {
            sink.close();
            sink.abort();
        }
    }

    @Test
    @Config(sdk = 33)
    public void constructWithNullContextThenSetContextUploadsSuccessfully() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ActiveUploadRegistry registry = new ActiveUploadRegistry();
        FileUploadHandler handler = new FileUploadHandler(null, null);
        handler.setContext(context);
        handler.setFreeSpaceProvider(() -> Long.MAX_VALUE);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("content-length", "11");
                    headers.put("x-upload-id", "up-test-deferred-ctx");
                    headers.put("x-file-name", "deferred.txt");
                    headers.put("x-file-size", "11");
                    headers.put("x-file-offset", "0");

                    handler.handleUpload(accepted, accepted.getInputStream(), headers,
                            (s, status, reason, ct, body, extra) -> {
                                OutputStream out = s.getOutputStream();
                                out.write(("HTTP/1.1 " + status + " " + reason + "\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(("Content-Length: " + body.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                out.write(body.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            });
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", port)) {
                OutputStream out = client.getOutputStream();
                out.write("hello world".getBytes(StandardCharsets.UTF_8));
                out.flush();
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertTrue(response.bodyString().contains("\"status\":\"complete\""));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
    }
}
