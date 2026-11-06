package dev.jason.gboardpatches.extension.filetransfer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class FileStreamHandler {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ACTIVE_STREAMS = 2;

    private final Semaphore activeStreamSemaphore = new Semaphore(MAX_ACTIVE_STREAMS);
    private final Set<ActiveFileStream> activeStreams = ConcurrentHashMap.newKeySet();
    private volatile FileTransferLockController lockController;
    private volatile FileTransferNotificationManager notificationManager;

    public FileStreamHandler() {
        this(null, null);
    }

    public FileStreamHandler(
            FileTransferLockController lockController,
            FileTransferNotificationManager notificationManager) {
        this.lockController = lockController;
        this.notificationManager = notificationManager;
    }

    public void setLockController(FileTransferLockController lockController) {
        this.lockController = lockController;
    }

    public void setNotificationManager(FileTransferNotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public static final class ActiveFileStream {
        private final Socket socket;
        private final FileOffer offer;
        private final AtomicLong lastWriteTimeMs;

        public ActiveFileStream(Socket socket, FileOffer offer, long nowMs) {
            this.socket = socket;
            this.offer = offer;
            this.lastWriteTimeMs = new AtomicLong(nowMs);
        }

        public void touch(long nowMs) {
            lastWriteTimeMs.set(nowMs);
        }

        public long getLastWriteTimeMs() {
            return lastWriteTimeMs.get();
        }

        public FileOffer getOffer() {
            return offer;
        }

        public void abort() {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public void handleFileDownload(
            Socket socket,
            String token,
            String rangeHeader,
            FileOfferRegistry registry,
            BooleanSupplier isStopped) throws Exception {
        if (token == null || token.isBlank()) {
            writeJson(socket, 404, "Not Found", "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        if (registry.isCompleted(token)) {
            writeJson(socket, 410, "Gone", "{\"ok\":false,\"error\":\"gone\"}");
            return;
        }
        FileOffer offer = registry.get(token);
        if (offer == null) {
            writeJson(socket, 404, "Not Found", "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        if (offer.isClosed() || offer.isCompleted()) {
            writeJson(socket, 410, "Gone", "{\"ok\":false,\"error\":\"gone\"}");
            return;
        }
        if (!offer.isSeekable()) {
            if (!offer.markSingleShotStarted()) {
                writeJson(socket, 410, "Gone", "{\"ok\":false,\"error\":\"gone\"}");
                return;
            }
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                writeRangeNotSatisfiable(socket, offer.getSize());
                return;
            }
        }
        HttpRange range = null;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            range = HttpRange.parse(rangeHeader, offer.getSize());
            if (range.isInvalid()) {
                writeRangeNotSatisfiable(socket, offer.getSize());
                return;
            }
        }
        if (!activeStreamSemaphore.tryAcquire()) {
            writeServiceUnavailable(socket, 5);
            return;
        }
        if (lockController != null) {
            lockController.acquire();
        }
        ActiveFileStream activeStream = new ActiveFileStream(socket, offer, System.currentTimeMillis());
        activeStreams.add(activeStream);
        try {
            streamFileResponse(socket, offer, range, activeStream, registry, isStopped);
        } finally {
            activeStreams.remove(activeStream);
            if (lockController != null) {
                lockController.release();
            }
            activeStreamSemaphore.release();
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public void streamFileResponse(
            Socket socket,
            FileOffer offer,
            HttpRange range,
            ActiveFileStream activeStream,
            FileOfferRegistry registry,
            BooleanSupplier isStopped) throws IOException {
        long bytesToTransfer = range != null ? range.length : offer.getSize();
        long currentOffset = range != null ? range.start : 0L;
        OutputStream outputStream = socket.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                outputStream, StandardCharsets.UTF_8));
        if (range == null) {
            writer.write("HTTP/1.1 200 OK\r\n");
            writer.write("Content-Type: " + offer.getMimeType() + "\r\n");
            writer.write("Content-Length: " + offer.getSize() + "\r\n");
            writer.write("Accept-Ranges: bytes\r\n");
            writer.write("Cache-Control: no-store\r\n");
            writer.write("Pragma: no-cache\r\n");
            writer.write("Expires: 0\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
        } else {
            writer.write("HTTP/1.1 206 Partial Content\r\n");
            writer.write("Content-Type: " + offer.getMimeType() + "\r\n");
            writer.write("Content-Range: bytes " + range.start + "-" + range.end + "/" + range.total + "\r\n");
            writer.write("Content-Length: " + range.length + "\r\n");
            writer.write("Accept-Ranges: bytes\r\n");
            writer.write("Cache-Control: no-store\r\n");
            writer.write("Pragma: no-cache\r\n");
            writer.write("Expires: 0\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
        }
        writer.flush();

        if (bytesToTransfer == 0L) {
            if (offer.getSize() == 0L) {
                offer.markCompleted();
                registry.completeOffer(offer.getToken());
            }
            return;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        byte[] array = byteBuffer.array();
        long remaining = bytesToTransfer;

        while (remaining > 0L && !isStopped.getAsBoolean()) {
            int toRead = (int) Math.min(remaining, BUFFER_SIZE);
            int read;
            if (offer.isSeekable()) {
                byteBuffer.clear();
                byteBuffer.limit(toRead);
                read = offer.getFileChannel().read(byteBuffer, currentOffset);
            } else {
                read = offer.getInputStream().read(array, 0, toRead);
            }
            if (read < 0) {
                break;
            }
            outputStream.write(array, 0, read);
            outputStream.flush();

            currentOffset += read;
            remaining -= read;

            long now = System.currentTimeMillis();
            activeStream.touch(now);
            offer.recordBytesServed(read, now);
            registry.touch(offer.getToken(), now);
            if (lockController != null) {
                lockController.touch();
            }
            if (notificationManager != null) {
                notificationManager.onBytesServed(offer.getBatchId(), read);
            }
        }

        if (offer.getBytesServed() >= offer.getSize() || currentOffset >= offer.getSize()) {
            offer.markCompleted();
            registry.completeOffer(offer.getToken());
            if (notificationManager != null) {
                notificationManager.onOfferCompleted(offer.getBatchId(), offer.getToken());
            }
        }
    }

    public void abortBatchStreams(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        for (ActiveFileStream stream : activeStreams) {
            if (batchId.equals(stream.getOffer().getBatchId())) {
                stream.abort();
            }
        }
    }

    public void checkStalledStreams(long nowMs, long timeoutMs) {
        for (ActiveFileStream stream : activeStreams) {
            if (nowMs - stream.getLastWriteTimeMs() > timeoutMs) {
                stream.abort();
            }
        }
    }

    public boolean hasActiveStreams() {
        return !activeStreams.isEmpty();
    }

    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    public void abortAll() {
        for (ActiveFileStream stream : activeStreams) {
            stream.abort();
        }
        activeStreams.clear();
    }

    private static void writeJson(Socket socket, int status, String reason, String jsonBody)
            throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + status + " " + reason + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Content-Length: " + body.length + "\r\n");
        writer.write("Cache-Control: no-store\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
        socket.getOutputStream().write(body);
        socket.getOutputStream().flush();
    }

    private static void writeRangeNotSatisfiable(Socket socket, long totalSize) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        byte[] body = "{\"ok\":false,\"error\":\"range_not_satisfiable\"}".getBytes(StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 416 Range Not Satisfiable\r\n");
        writer.write("Content-Range: bytes */" + totalSize + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Content-Length: " + body.length + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
        socket.getOutputStream().write(body);
        socket.getOutputStream().flush();
    }

    private static void writeServiceUnavailable(Socket socket, int retryAfterSeconds)
            throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        byte[] body = "{\"ok\":false,\"error\":\"busy\"}".getBytes(StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 503 Service Unavailable\r\n");
        writer.write("Retry-After: " + retryAfterSeconds + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Content-Length: " + body.length + "\r\n");
        writer.write("Cache-Control: no-store\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
        socket.getOutputStream().write(body);
        socket.getOutputStream().flush();
    }
}
