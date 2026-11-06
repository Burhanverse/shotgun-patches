package dev.jason.gboardpatches.extension.filetransfer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class FileUploadHandler {
    public static final long FREE_SPACE_MARGIN_BYTES = 50L * 1024L * 1024L;
    public static final long STALL_TIMEOUT_MS = 45_000L;
    public static final int BUFFER_SIZE = 64 * 1024;
    public static final int MAX_FILENAME_BYTES = 255;

    @FunctionalInterface
    public interface FreeSpaceProvider {
        long getAvailableBytes();
    }

    @FunctionalInterface
    public interface UploadSinkFactory {
        UploadSink create(String fileName, String mimeType) throws IOException;
    }

    @FunctionalInterface
    public interface ResponseWriter {
        void write(Socket socket, int status, String reason, String contentType,
                String body, Map<String, String> extraHeaders) throws Exception;
    }

    private volatile Context context;
    private final ActiveUploadRegistry activeUploadRegistry;
    private volatile FileTransferLockController lockController;
    private volatile FileTransferNotificationManager notificationManager;
    private final Semaphore uploadSemaphore = new Semaphore(2);
    private final ScheduledExecutorService scheduledExecutor;
    private volatile BooleanSupplier acceptFilesSupplier;
    private final FreeSpaceProvider freeSpaceProvider;
    private final UploadSinkFactory sinkFactory;
    private final IntSupplier sdkVersionSupplier;

    public FileUploadHandler(Context context, FileTransferLockController lockController) {
        this(context,
                ActiveUploadRegistry.getInstance(),
                lockController,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "WebClipUploadWatchdog");
                    t.setDaemon(true);
                    return t;
                }),
                () -> true,
                defaultFreeSpaceProvider(),
                (name, mime) -> new MediaStoreUploadSink(context, name, mime),
                () -> Build.VERSION.SDK_INT);
    }

    public FileUploadHandler(
            Context context,
            ActiveUploadRegistry activeUploadRegistry,
            FileTransferLockController lockController,
            ScheduledExecutorService scheduledExecutor,
            BooleanSupplier acceptFilesSupplier,
            FreeSpaceProvider freeSpaceProvider,
            UploadSinkFactory sinkFactory,
            IntSupplier sdkVersionSupplier) {
        this.context = context;
        this.activeUploadRegistry = activeUploadRegistry != null
                ? activeUploadRegistry
                : ActiveUploadRegistry.getInstance();
        this.lockController = lockController;
        this.scheduledExecutor = scheduledExecutor;
        this.acceptFilesSupplier = acceptFilesSupplier != null ? acceptFilesSupplier : () -> true;
        this.freeSpaceProvider = freeSpaceProvider != null ? freeSpaceProvider : defaultFreeSpaceProvider();
        this.sinkFactory = sinkFactory;
        this.sdkVersionSupplier = sdkVersionSupplier != null
                ? sdkVersionSupplier
                : () -> Build.VERSION.SDK_INT;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setLockController(FileTransferLockController lockController) {
        this.lockController = lockController;
    }

    public void setAcceptFilesSupplier(BooleanSupplier acceptFilesSupplier) {
        this.acceptFilesSupplier = acceptFilesSupplier != null ? acceptFilesSupplier : () -> true;
    }

    public void setNotificationManager(FileTransferNotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public ActiveUploadRegistry getActiveUploadRegistry() {
        return activeUploadRegistry;
    }

    public static void cleanOrphanedPendingRows(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Downloads.IS_PENDING + " = 1 AND "
                    + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
            String[] selectionArgs = new String[] { "Download/WebClip%" };
            String[] projection = new String[] { MediaStore.Downloads._ID };

            List<Uri> rowsToDelete = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bundle queryArgs = new Bundle();
                queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
                queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
                queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
                try (Cursor cursor = resolver.query(collection, projection, queryArgs, null)) {
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            rowsToDelete.add(ContentUris.withAppendedId(collection, id));
                        }
                    }
                }
            } else {
                Uri queryUri = MediaStore.setIncludePending(collection);
                try (Cursor cursor = resolver.query(queryUri, projection, selection, selectionArgs, null)) {
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            rowsToDelete.add(MediaStore.setIncludePending(ContentUris.withAppendedId(collection, id)));
                        }
                    }
                }
            }

            for (Uri rowUri : rowsToDelete) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bundle deleteArgs = new Bundle();
                        deleteArgs.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
                        resolver.delete(rowUri, deleteArgs);
                    } else {
                        resolver.delete(rowUri, null, null);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static FreeSpaceProvider defaultFreeSpaceProvider() {
        return () -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                StatFs stat = new StatFs(dir.getAbsolutePath());
                return stat.getAvailableBytes();
            } catch (Throwable ignored) {
                return Long.MAX_VALUE;
            }
        };
    }

    public void handleUploadStatus(Socket socket, String uploadId, ResponseWriter writer) throws Exception {
        Long completedSize = activeUploadRegistry.getCompletedUploadSize(uploadId);
        if (completedSize != null) {
            String body = "{\"offset\":" + completedSize + ",\"size\":" + completedSize + ",\"complete\":true}";
            writer.write(socket, 200, "OK", "application/json; charset=utf-8", body, null);
            return;
        }
        ActiveUploadRegistry.UploadSession session = activeUploadRegistry.get(uploadId);
        if (session == null) {
            writer.write(socket, 404, "Not Found", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"not_found\"}", null);
            return;
        }
        String body = "{\"offset\":" + session.getOffset() + ",\"size\":" + session.getTotalSize() + "}";
        writer.write(socket, 200, "OK", "application/json; charset=utf-8", body, null);
    }

    public void handleUpload(
            Socket socket,
            InputStream input,
            Map<String, String> headers,
            ResponseWriter writer) throws Exception {
        if (sdkVersionSupplier.getAsInt() < 29) {
            writer.write(socket, 403, "Forbidden", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"unsupported_android_version\"}", null);
            return;
        }

        if (!acceptFilesSupplier.getAsBoolean()) {
            writer.write(socket, 403, "Forbidden", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"uploads_disabled\"}", null);
            return;
        }

        String contentLengthHeader = header(headers, "content-length");
        if (contentLengthHeader == null || contentLengthHeader.isBlank()) {
            writer.write(socket, 411, "Length Required", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"length_required\"}", null);
            return;
        }

        long contentLength;
        try {
            contentLength = Long.parseLong(contentLengthHeader.trim());
            if (contentLength < 0) {
                writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"invalid_content_length\"}", null);
                return;
            }
        } catch (NumberFormatException exception) {
            writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"invalid_content_length\"}", null);
            return;
        }

        String uploadId = header(headers, "x-upload-id");
        if (uploadId == null || uploadId.isBlank()) {
            writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"missing_upload_id\"}", null);
            return;
        }

        String fileSizeHeader = header(headers, "x-file-size");
        long fileSize;
        try {
            fileSize = fileSizeHeader != null ? Long.parseLong(fileSizeHeader.trim()) : -1L;
            if (fileSize < 0) {
                writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"invalid_file_size\"}", null);
                return;
            }
        } catch (NumberFormatException exception) {
            writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"invalid_file_size\"}", null);
            return;
        }

        Long completedSize = activeUploadRegistry.getCompletedUploadSize(uploadId);
        if (completedSize != null && completedSize == fileSize) {
            writer.write(socket, 200, "OK", "application/json; charset=utf-8",
                    "{\"ok\":true,\"status\":\"complete\"}", null);
            return;
        }

        String offsetHeader = header(headers, "x-file-offset");
        long fileOffset = 0L;
        if (offsetHeader != null && !offsetHeader.isBlank()) {
            try {
                fileOffset = Long.parseLong(offsetHeader.trim());
                if (fileOffset < 0 || fileOffset > fileSize) {
                    writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                            "{\"ok\":false,\"error\":\"invalid_file_offset\"}", null);
                    return;
                }
            } catch (NumberFormatException exception) {
                writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"invalid_file_offset\"}", null);
                return;
            }
        }

        long expectedContentLength = fileSize - fileOffset;
        if (contentLength != expectedContentLength) {
            writer.write(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"content_length_mismatch\"}", null);
            return;
        }

        String batchId = header(headers, "x-batch-id");
        int batchIndex = parsePositiveInt(header(headers, "x-batch-index"), 1);
        int batchTotal = parsePositiveInt(header(headers, "x-batch-total"), 1);

        if (activeUploadRegistry.isBatchCancelled(batchId)) {
            writer.write(socket, 410, "Gone", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"batch_cancelled\"}", null);
            return;
        }

        ActiveUploadRegistry.UploadSession session = activeUploadRegistry.get(uploadId);
        boolean sessionWriterLocked = false;
        if (session != null) {
            session.abortActiveConnection();
            try {
                if (!session.tryAcquireWriter(3000, TimeUnit.MILLISECONDS)) {
                    writer.write(socket, 409, "Conflict", "application/json; charset=utf-8",
                            "{\"ok\":false,\"error\":\"upload_in_progress\"}",
                            Collections.singletonMap("Retry-After", "1"));
                    return;
                }
                sessionWriterLocked = true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                writer.write(socket, 409, "Conflict", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"upload_in_progress\"}",
                        Collections.singletonMap("Retry-After", "1"));
                return;
            }

            if (fileOffset == 0) {
                activeUploadRegistry.remove(uploadId);
                session.getSink().abort();
                session.releaseWriter();
                sessionWriterLocked = false;
                session = null;
            } else if (fileOffset != session.getOffset()) {
                long currentSessionOffset = session.getOffset();
                session.releaseWriter();
                sessionWriterLocked = false;
                writer.write(socket, 409, "Conflict", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"offset_mismatch\",\"offset\":" + currentSessionOffset + "}", null);
                return;
            }
        }

        long availableBytes = freeSpaceProvider.getAvailableBytes();
        if (availableBytes < fileSize + FREE_SPACE_MARGIN_BYTES) {
            if (sessionWriterLocked && session != null) {
                session.releaseWriter();
            }
            writer.write(socket, 507, "Insufficient Storage", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"insufficient_storage\"}", null);
            return;
        }

        if (!uploadSemaphore.tryAcquire()) {
            if (sessionWriterLocked && session != null) {
                session.releaseWriter();
            }
            writer.write(socket, 503, "Service Unavailable", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"upload_busy\"}",
                    Collections.singletonMap("Retry-After", "5"));
            return;
        }

        boolean lockAcquired = false;
        try {
            String expect = header(headers, "expect");
            if ("100-continue".equalsIgnoreCase(expect)) {
                OutputStream output = socket.getOutputStream();
                output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            socket.setSoTimeout(30_000);
            FileTransferLockController currentLockController = lockController;
            if (currentLockController != null) {
                currentLockController.acquire();
                lockAcquired = true;
            }

            if (session == null) {
                String rawFileName = header(headers, "x-file-name");
                String sanitizedName = sanitizeFileName(rawFileName);
                String mimeType = resolveMimeType(sanitizedName);
                UploadSink sink = sinkFactory != null
                        ? sinkFactory.create(sanitizedName, mimeType)
                        : new MediaStoreUploadSink(context, sanitizedName, mimeType);
                session = new ActiveUploadRegistry.UploadSession(
                        uploadId, sanitizedName, fileSize, sink, fileOffset, System.currentTimeMillis(), batchId);
                try {
                    session.tryAcquireWriter(3000, TimeUnit.MILLISECONDS);
                    sessionWriterLocked = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                activeUploadRegistry.put(session);
            }
            session.setActiveSocket(socket);

            FileTransferNotificationManager currentNotificationManager = notificationManager;
            if (currentNotificationManager != null) {
                currentNotificationManager.onUploadStarted(
                        uploadId, session.getFileName(), fileSize, fileOffset, batchId, batchIndex, batchTotal);
            }

            AtomicLong lastByteTimeMs = new AtomicLong(System.currentTimeMillis());
            ScheduledFuture<?> watchdog = null;
            if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
                watchdog = scheduledExecutor.scheduleWithFixedDelay(() -> {
                    if (System.currentTimeMillis() - lastByteTimeMs.get() > STALL_TIMEOUT_MS) {
                        closeQuietly(socket);
                    }
                }, 5, 5, TimeUnit.SECONDS);
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            long currentOffset = fileOffset;
            long remaining = contentLength;
            try {
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = input.read(buffer, 0, toRead);
                    if (read < 0) {
                        break;
                    }
                    session.getSink().writeAt(currentOffset, buffer, read);
                    currentOffset += read;
                    remaining -= read;
                    session.setOffset(currentOffset);
                    long now = System.currentTimeMillis();
                    lastByteTimeMs.set(now);
                    session.touch(now);
                    if (currentLockController != null) {
                        currentLockController.touch();
                    }
                    if (currentNotificationManager != null) {
                        currentNotificationManager.onUploadProgress(uploadId, currentOffset, fileSize);
                    }
                }
            } finally {
                if (watchdog != null) {
                    watchdog.cancel(false);
                }
            }

            if (currentOffset == fileSize) {
                session.getSink().publish();
                Uri fileUri = session.getSink().getUri();
                String mimeType = resolveMimeType(session.getFileName());
                activeUploadRegistry.recordCompletedUpload(uploadId, fileSize);
                activeUploadRegistry.remove(uploadId);
                if (currentNotificationManager != null) {
                    currentNotificationManager.onUploadCompleted(
                            uploadId, session.getFileName(), batchId, batchIndex, batchTotal, fileUri, mimeType);
                }
                writer.write(socket, 200, "OK", "application/json; charset=utf-8",
                        "{\"ok\":true,\"status\":\"complete\"}", null);
            } else {
                if (currentNotificationManager != null) {
                    currentNotificationManager.onUploadFailed(uploadId, batchId, "Upload incomplete");
                }
                closeQuietly(socket);
            }
        } finally {
            if (sessionWriterLocked && session != null) {
                session.releaseWriter(socket);
            }
            if (lockAcquired) {
                FileTransferLockController currentLockController = lockController;
                if (currentLockController != null) {
                    currentLockController.release();
                }
            }
            uploadSemaphore.release();
        }
    }

    public boolean hasActiveUploads() {
        return activeUploadRegistry.size() > 0 || uploadSemaphore.availablePermits() < 2;
    }

    public void cleanExpiredSessions(long nowMs) {
        activeUploadRegistry.cleanExpiredSessions(nowMs);
        FileTransferNotificationManager currentNotificationManager = notificationManager;
        if (currentNotificationManager != null) {
            currentNotificationManager.checkStaleBatches(nowMs);
        }
    }

    public void abortAll() {
        activeUploadRegistry.clear();
    }

    public static String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "file";
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            decoded = rawName;
        }
        decoded = decoded.replace("/", "").replace("\\", "");
        while (decoded.contains("..")) {
            decoded = decoded.replace("..", "");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c <= 31 || c == 127) {
                continue;
            }
            if (c == '"' || c == '*' || c == ':' || c == '<' || c == '>' || c == '?' || c == '|') {
                continue;
            }
            builder.append(c);
        }
        String cleaned = builder.toString().trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty()) {
            return "file";
        }
        byte[] utf8Bytes = cleaned.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_FILENAME_BYTES) {
            int dot = cleaned.lastIndexOf('.');
            String ext = (dot > 0 && dot < cleaned.length() - 1) ? cleaned.substring(dot) : "";
            if (!ext.isEmpty() && ext.getBytes(StandardCharsets.UTF_8).length < MAX_FILENAME_BYTES) {
                String base = cleaned.substring(0, dot);
                int baseEnd = base.length();
                while (baseEnd > 0 && (base.substring(0, baseEnd) + ext).getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES) {
                    baseEnd--;
                }
                cleaned = (base.substring(0, baseEnd) + ext).trim();
            } else {
                int end = cleaned.length();
                while (end > 0 && cleaned.substring(0, end).getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES) {
                    end--;
                }
                cleaned = cleaned.substring(0, end).trim();
            }
            if (cleaned.isEmpty()) {
                return "file";
            }
        }
        return cleaned;
    }

    public static String resolveMimeType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "application/octet-stream";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String extension = fileName.substring(dot + 1).toLowerCase(Locale.US);
            try {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null && !mime.isEmpty()) {
                    return mime;
                }
            } catch (Throwable ignored) {
            }
        }
        return "application/octet-stream";
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        return headers.get(name.toLowerCase(Locale.US));
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
