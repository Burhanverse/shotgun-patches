package dev.jason.gboardpatches.extension.filetransfer;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class FileStreamHandlerTest {
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
    }

    private static HttpResponse readResponse(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
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

    @Test
    public void streamSeekableFileFullDownload() throws Exception {
        File tempFile = File.createTempFile("stream-test", ".bin");
        tempFile.deleteOnExit();
        byte[] data = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(data);
        }

        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "seekable-token";
        FileChannel channel = FileChannel.open(tempFile.toPath(), StandardOpenOption.READ);
        FileOffer offer = FileOffer.forTesting(
                token,
                channel,
                "data.bin",
                data.length,
                "application/octet-stream",
                "batch-1",
                0,
                1,
                "",
                true);
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, null, registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertEquals(String.valueOf(data.length), response.header("content-length"));
                Assert.assertEquals("bytes", response.header("accept-ranges"));
                Assert.assertArrayEquals(data, response.body);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
        Assert.assertTrue(offer.isCompleted());
        Assert.assertTrue(offer.isClosed());
    }

    @Test
    public void streamSeekableFileRangeRequest() throws Exception {
        File tempFile = File.createTempFile("stream-range", ".bin");
        tempFile.deleteOnExit();
        byte[] data = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(data);
        }

        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "range-token";
        FileChannel channel = FileChannel.open(tempFile.toPath(), StandardOpenOption.READ);
        FileOffer offer = FileOffer.forTesting(
                token,
                channel,
                "data.bin",
                data.length,
                "application/octet-stream",
                "batch-1",
                0,
                1,
                "",
                true);
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, "bytes=10-19", registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(206, response.statusCode);
                Assert.assertEquals("10", response.header("content-length"));
                Assert.assertEquals("bytes 10-19/" + data.length, response.header("content-range"));
                byte[] expected = "abcdefghij".getBytes(StandardCharsets.UTF_8);
                Assert.assertArrayEquals(expected, response.body);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
        Assert.assertFalse(offer.isCompleted());
        offer.close();
    }

    @Test
    public void invalidRangeReturns416() throws Exception {
        File tempFile = File.createTempFile("stream-416", ".bin");
        tempFile.deleteOnExit();
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(data);
        }

        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "bad-range-token";
        FileChannel channel = FileChannel.open(tempFile.toPath(), StandardOpenOption.READ);
        FileOffer offer = FileOffer.forTesting(
                token,
                channel,
                "hello.txt",
                data.length,
                "text/plain",
                "batch-1",
                0,
                1,
                "",
                true);
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, "bytes=50-100", registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(416, response.statusCode);
                Assert.assertEquals("bytes */" + data.length, response.header("content-range"));
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
        offer.close();
    }

    @Test
    public void nonSeekableStreamSingleShotAndNoRange() throws Exception {
        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "non-seekable-token";
        byte[] data = "single-shot-data".getBytes(StandardCharsets.UTF_8);
        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream(data),
                "stream.bin",
                data.length,
                "application/octet-stream",
                "batch-1",
                0,
                1,
                "");
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();

        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, null, registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(200, response.statusCode);
                Assert.assertArrayEquals(data, response.body);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }

        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, null, registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(410, response.statusCode);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void nonSeekableRangeReturns416() throws Exception {
        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "non-seekable-range";
        byte[] data = "piped-data".getBytes(StandardCharsets.UTF_8);
        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream(data),
                "pipe.bin",
                data.length,
                "application/octet-stream",
                "batch-1",
                0,
                1,
                "");
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch serverLatch = new CountDownLatch(1);
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    handler.handleFileDownload(accepted, token, "bytes=0-4", registry, () -> false);
                } catch (Throwable ignored) {
                } finally {
                    serverLatch.countDown();
                }
            }).start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                HttpResponse response = readResponse(client);
                Assert.assertEquals(416, response.statusCode);
            }
            Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        }
        offer.close();
    }

    @Test
    public void concurrencyLimitReturns503WhenFull() throws Exception {
        FileOfferRegistry registry = new FileOfferRegistry();
        String token = "concurrency-token";
        byte[] data = new byte[1024 * 1024];
        FileOffer offer = FileOffer.forTestingStream(
                token,
                new ByteArrayInputStream(data),
                "big.bin",
                data.length,
                "application/octet-stream",
                "batch-1",
                0,
                1,
                "");
        registry.register(offer);

        FileStreamHandler handler = new FileStreamHandler();
        CountDownLatch stream1Started = new CountDownLatch(1);
        CountDownLatch stream2Started = new CountDownLatch(1);
        CountDownLatch releaseStreams = new CountDownLatch(1);

        try (ServerSocket server = new ServerSocket(0)) {
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    stream1Started.countDown();
                    releaseStreams.await();
                } catch (Throwable ignored) {
                }
            }).start();
            new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    stream2Started.countDown();
                    releaseStreams.await();
                } catch (Throwable ignored) {
                }
            }).start();

            Socket s1 = new Socket("127.0.0.1", server.getLocalPort());
            Socket s2 = new Socket("127.0.0.1", server.getLocalPort());
            Assert.assertTrue(stream1Started.await(5, TimeUnit.SECONDS));
            Assert.assertTrue(stream2Started.await(5, TimeUnit.SECONDS));

            FileStreamHandler.ActiveFileStream act1 = new FileStreamHandler.ActiveFileStream(s1, offer, System.currentTimeMillis());
            FileStreamHandler.ActiveFileStream act2 = new FileStreamHandler.ActiveFileStream(s2, offer, System.currentTimeMillis());

            java.lang.reflect.Field semField = FileStreamHandler.class.getDeclaredField("activeStreamSemaphore");
            semField.setAccessible(true);
            java.util.concurrent.Semaphore sem = (java.util.concurrent.Semaphore) semField.get(handler);
            sem.acquire(2);

            try (ServerSocket busyServer = new ServerSocket(0)) {
                CountDownLatch busyLatch = new CountDownLatch(1);
                new Thread(() -> {
                    try (Socket accepted = busyServer.accept()) {
                        handler.handleFileDownload(accepted, token, null, registry, () -> false);
                    } catch (Throwable ignored) {
                    } finally {
                        busyLatch.countDown();
                    }
                }).start();

                try (Socket client = new Socket("127.0.0.1", busyServer.getLocalPort())) {
                    HttpResponse response = readResponse(client);
                    Assert.assertEquals(503, response.statusCode);
                    Assert.assertEquals("5", response.header("retry-after"));
                }
                Assert.assertTrue(busyLatch.await(5, TimeUnit.SECONDS));
            }

            sem.release(2);
            releaseStreams.countDown();
            s1.close();
            s2.close();
        }
        offer.close();
    }

    @Test
    public void stallWatchdogAbortsStalledStream() throws Exception {
        FileStreamHandler handler = new FileStreamHandler();
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket accepted = server.accept();

            FileOffer offer = FileOffer.forTestingStream(
                    "tok",
                    new ByteArrayInputStream(new byte[0]),
                    "x",
                    0L,
                    "application/octet-stream",
                    "b",
                    0,
                    1,
                    "");
            FileStreamHandler.ActiveFileStream activeStream = new FileStreamHandler.ActiveFileStream(
                    accepted,
                    offer,
                    System.currentTimeMillis() - 50_000L);

            java.lang.reflect.Field streamsField = FileStreamHandler.class.getDeclaredField("activeStreams");
            streamsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<FileStreamHandler.ActiveFileStream> streams =
                    (java.util.Set<FileStreamHandler.ActiveFileStream>) streamsField.get(handler);
            streams.add(activeStream);

            handler.checkStalledStreams(System.currentTimeMillis(), 45_000L);
            Assert.assertTrue(accepted.isClosed());

            client.close();
            offer.close();
        }
    }
}
