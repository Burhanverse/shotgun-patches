package dev.jason.gboardpatches.extension.filetransfer;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService;

public final class FileTransferNotificationManager {
    public static final String CHANNEL_ID = "webclip_file_transfer";
    public static final String CHANNEL_ID_OFFERS = "webclip_file_offers";
    public static final int NOTIFICATION_ID_PROGRESS = 1022;
    public static final int NOTIFICATION_ID_RESULT = 1023;
    public static final int NOTIFICATION_ID_RECEIVE_PROGRESS = 1024;
    public static final int NOTIFICATION_ID_RECEIVE_RESULT = 1025;
    public static final int NOTIFICATION_ID_OFFER = 1026;
    private static final long PROGRESS_THROTTLE_MS = 500L;
    public static final long STALE_BATCH_TIMEOUT_MS = 2L * 60L * 1000L;
    private static final String TAG = "GboardFileTransferNotif";

    private static final FileTransferNotificationManager INSTANCE = new FileTransferNotificationManager();

    private final Map<String, BatchState> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, ReceiveBatchState> activeReceives = new ConcurrentHashMap<>();
    private final Map<String, ReceiveBatch> activeReceiveBatches = new ConcurrentHashMap<>();
    private volatile LongSupplier clock = System::currentTimeMillis;
    private volatile Context appContext;
    private volatile FileStreamHandler streamHandler;
    private volatile FileOfferRegistry offerRegistry = FileOfferRegistry.getInstance();
    private volatile ActiveUploadRegistry activeUploadRegistry = ActiveUploadRegistry.getInstance();

    public static FileTransferNotificationManager getInstance() {
        return INSTANCE;
    }

    FileTransferNotificationManager() {
    }

    public void setClock(LongSupplier clock) {
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    public void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            ensureChannel(appContext);
        }
    }

    public Context getAppContext() {
        return appContext;
    }

    public void setFileStreamHandler(FileStreamHandler handler) {
        this.streamHandler = handler;
    }

    public void setFileOfferRegistry(FileOfferRegistry registry) {
        this.offerRegistry = registry;
    }

    public void setActiveUploadRegistry(ActiveUploadRegistry registry) {
        this.activeUploadRegistry = registry;
    }

    public synchronized void onUploadStarted(
            String uploadId,
            String fileName,
            long fileSize,
            long fileOffset,
            String batchId,
            int batchIndex,
            int batchTotal) {
        if (uploadId == null) {
            return;
        }
        if (batchId != null && !batchId.isBlank()) {
            ReceiveBatch batch = activeReceiveBatches.computeIfAbsent(
                    batchId, id -> new ReceiveBatch(id, batchTotal, clock.getAsLong()));
            batch.lastActivityMs = clock.getAsLong();
        }
        ReceiveBatchState state = new ReceiveBatchState(
                uploadId, fileName, fileSize, fileOffset, batchId, batchIndex, batchTotal);
        activeReceives.put(uploadId, state);
        publishReceiveProgressNotification(state);
    }

    public void onUploadProgress(String uploadId, long currentOffset, long totalSize) {
        if (uploadId == null) {
            return;
        }
        ReceiveBatchState state = activeReceives.get(uploadId);
        if (state == null || state.cancelled) {
            return;
        }
        if (state.batchId != null && !state.batchId.isBlank()) {
            ReceiveBatch batch = activeReceiveBatches.get(state.batchId);
            if (batch != null) {
                batch.lastActivityMs = clock.getAsLong();
            }
        }
        state.bytesReceived.set(currentOffset);
        long now = SystemClock.elapsedRealtime();
        if (now - state.lastProgressUpdateElapsedMs >= PROGRESS_THROTTLE_MS) {
            state.lastProgressUpdateElapsedMs = now;
            publishReceiveProgressNotification(state);
        }
    }

    public synchronized void onUploadCompleted(
            String uploadId,
            String fileName,
            String batchId,
            int batchIndex,
            int batchTotal,
            Uri fileUri,
            String mimeType) {
        if (uploadId == null) {
            return;
        }
        ReceiveBatchState state = activeReceives.remove(uploadId);
        if (state == null || state.cancelled) {
            return;
        }
        int total = state.batchTotal > 0 ? state.batchTotal : Math.max(1, batchTotal);
        String bId = state.batchId != null ? state.batchId : batchId;
        String name = state.fileName != null ? state.fileName : (fileName != null ? fileName : "file");
        if (bId != null && total > 1) {
            ReceiveBatch batch = activeReceiveBatches.computeIfAbsent(
                    bId, id -> new ReceiveBatch(id, total, clock.getAsLong()));
            batch.completedUploadIds.add(uploadId);
            batch.lastActivityMs = clock.getAsLong();
            if (batch.completedUploadIds.size() >= total) {
                activeReceiveBatches.remove(bId);
                cancelReceiveProgressNotification();
                publishReceiveResultNotification(
                        "Transfer complete",
                        total + " files received",
                        null,
                        null);
            }
        } else {
            if (bId != null) {
                activeReceiveBatches.remove(bId);
            }
            cancelReceiveProgressNotification();
            publishReceiveResultNotification(
                    "Transfer complete",
                    "Received " + name,
                    fileUri,
                    mimeType);
        }
    }

    public synchronized void onUploadFailed(String uploadId, String batchId, String reason) {
        if (uploadId != null) {
            ReceiveBatchState state = activeReceives.remove(uploadId);
            if (state == null || state.cancelled) {
                return;
            }
            if (state.batchId != null) {
                ReceiveBatch batch = activeReceiveBatches.get(state.batchId);
                if (batch != null) {
                    batch.lastActivityMs = clock.getAsLong();
                }
            }
        }
        cancelReceiveProgressNotification();
        publishReceiveResultNotification(
                "Transfer failed",
                reason != null && !reason.isBlank() ? reason : "File transfer failed",
                null,
                null);
    }

    public synchronized void cancelUpload(String uploadId, String batchId) {
        if (activeUploadRegistry != null) {
            if (batchId != null && !batchId.isBlank()) {
                activeUploadRegistry.recordCancelledBatch(batchId);
                activeUploadRegistry.abortAndRemoveBatch(batchId);
            }
            if (uploadId != null && !uploadId.isBlank()) {
                ActiveUploadRegistry.UploadSession session = activeUploadRegistry.remove(uploadId);
                if (session != null) {
                    session.abortActiveConnection();
                    session.getSink().abort();
                }
            }
        }
        if (uploadId != null) {
            ReceiveBatchState state = activeReceives.remove(uploadId);
            if (state != null) {
                state.cancelled = true;
                if (state.batchId != null) {
                    activeReceiveBatches.remove(state.batchId);
                }
            }
        }
        if (batchId != null) {
            activeReceiveBatches.remove(batchId);
            for (Map.Entry<String, ReceiveBatchState> entry : activeReceives.entrySet()) {
                if (batchId.equals(entry.getValue().batchId)) {
                    entry.getValue().cancelled = true;
                    activeReceives.remove(entry.getKey());
                }
            }
        }
        cancelReceiveProgressNotification();
        publishReceiveResultNotification(
                "Transfer cancelled",
                "File upload was cancelled",
                null,
                null);
    }

    public synchronized void checkStaleBatches(long nowMs) {
        Iterator<Map.Entry<String, ReceiveBatch>> iterator = activeReceiveBatches.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ReceiveBatch> entry = iterator.next();
            ReceiveBatch batch = entry.getValue();
            if (nowMs - batch.lastActivityMs >= STALE_BATCH_TIMEOUT_MS) {
                iterator.remove();
                cancelReceiveProgressNotification();
                int completedCount = batch.completedUploadIds.size();
                if (completedCount > 0) {
                    publishReceiveResultNotification(
                            "Transfer complete",
                            "Received " + completedCount + " of " + batch.batchTotal + " files",
                            null,
                            null);
                } else {
                    publishReceiveResultNotification(
                            "Transfer interrupted",
                            "Transfer interrupted",
                            null,
                            null);
                }
                for (Map.Entry<String, ReceiveBatchState> receiveEntry : activeReceives.entrySet()) {
                    if (batch.batchId.equals(receiveEntry.getValue().batchId)) {
                        receiveEntry.getValue().cancelled = true;
                        activeReceives.remove(receiveEntry.getKey());
                    }
                }
            }
        }
    }

    public synchronized void onBatchStarted(Context context, String batchId, List<FileOffer> offers) {
        if (batchId == null || offers == null || offers.isEmpty()) {
            return;
        }
        init(context);
        long totalBytes = 0L;
        String firstName = offers.get(0).getName();
        for (FileOffer offer : offers) {
            totalBytes += offer.getSize();
        }
        BatchState state = new BatchState(batchId, offers.size(), totalBytes, firstName);
        activeBatches.put(batchId, state);
        publishProgressNotification(state);
    }

    public void onBytesServed(String batchId, long bytes) {
        if (batchId == null || bytes <= 0L) {
            return;
        }
        BatchState state = activeBatches.get(batchId);
        if (state == null || state.cancelled) {
            return;
        }
        state.bytesTransferred.addAndGet(bytes);
        long now = SystemClock.elapsedRealtime();
        if (now - state.lastProgressUpdateElapsedMs >= PROGRESS_THROTTLE_MS) {
            state.lastProgressUpdateElapsedMs = now;
            publishProgressNotification(state);
        }
    }

    public synchronized void onOfferCompleted(String batchId, String token) {
        if (batchId == null || token == null) {
            return;
        }
        BatchState state = activeBatches.get(batchId);
        if (state == null || state.cancelled) {
            return;
        }
        state.completedTokens.add(token);
        if (state.completedTokens.size() >= state.totalFiles) {
            activeBatches.remove(batchId);
            cancelProgressNotification();
            publishResultNotification(
                    "Transfer complete",
                    state.totalFiles == 1
                            ? state.firstFileName + " sent to PC"
                            : state.totalFiles + " files sent to PC");
        }
    }

    public synchronized void cancelBatch(String batchId) {
        if (batchId == null) {
            return;
        }
        BatchState state = activeBatches.remove(batchId);
        if (offerRegistry != null) {
            offerRegistry.cancelBatch(batchId);
        }
        if (streamHandler != null) {
            streamHandler.abortBatchStreams(batchId);
        }
        cancelProgressNotification();
        publishResultNotification(
                "Transfer cancelled",
                state != null && state.totalFiles == 1
                        ? "Transfer of " + state.firstFileName + " was cancelled"
                        : "File transfer was cancelled");
    }

    public synchronized void onBatchFailed(String batchId, String reason) {
        if (batchId == null) {
            return;
        }
        BatchState state = activeBatches.remove(batchId);
        if (offerRegistry != null) {
            offerRegistry.cancelBatch(batchId);
        }
        if (streamHandler != null) {
            streamHandler.abortBatchStreams(batchId);
        }
        cancelProgressNotification();
        publishResultNotification(
                "Transfer failed",
                reason != null && !reason.isBlank()
                        ? reason
                        : "File transfer failed");
    }

    public synchronized void cancelAll() {
        for (String batchId : activeBatches.keySet()) {
            if (offerRegistry != null) {
                offerRegistry.cancelBatch(batchId);
            }
            if (streamHandler != null) {
                streamHandler.abortBatchStreams(batchId);
            }
        }
        activeBatches.clear();
        cancelProgressNotification();
    }

    private void publishProgressNotification(BatchState state) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            String title = state.totalFiles == 1
                    ? "Sending " + state.firstFileName
                    : "Sending " + state.totalFiles + " files";

            long transferred = Math.min(state.bytesTransferred.get(), state.totalBytes);
            int percent = state.totalBytes > 0
                    ? (int) (transferred * 100 / state.totalBytes)
                    : 0;

            String content = state.totalBytes > 0
                    ? formatSize(transferred) + " / " + formatSize(state.totalBytes) + " (" + percent + "%)"
                    : formatSize(transferred);

            builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);

            if (state.totalBytes > 0) {
                builder.setProgress(100, percent, false);
            } else {
                builder.setProgress(0, 0, true);
            }

            Intent cancelIntent = new Intent(context, ClipboardSyncService.class);
            cancelIntent.setAction(ClipboardSyncService.ACTION_CANCEL_FILE_TRANSFER);
            cancelIntent.putExtra(ClipboardSyncService.EXTRA_BATCH_ID, state.batchId);
            PendingIntent pendingCancel = PendingIntent.getService(
                    context,
                    state.batchId.hashCode(),
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Action cancelAction = new Notification.Action.Builder(
                    0, "Cancel", pendingCancel).build();
            builder.addAction(cancelAction);

            manager.notify(NOTIFICATION_ID_PROGRESS, builder.build());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to publish progress notification", throwable);
        }
    }

    private void cancelProgressNotification() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID_PROGRESS);
            }
        } catch (Throwable ignored) {
        }
    }

    private void publishResultNotification(String title, String message) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID_OFFERS)
                    : new Notification.Builder(context);

            builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setOngoing(false)
                    .setAutoCancel(true);

            manager.notify(NOTIFICATION_ID_RESULT, builder.build());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to publish result notification", throwable);
        }
    }

    private void publishReceiveProgressNotification(ReceiveBatchState state) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            String title = state.batchTotal > 1
                    ? "Receiving " + state.batchIndex + "/" + state.batchTotal
                    : "Receiving " + state.fileName;

            long received = state.fileSize > 0
                    ? Math.min(state.bytesReceived.get(), state.fileSize)
                    : state.bytesReceived.get();
            int percent = state.fileSize > 0
                    ? (int) (received * 100 / state.fileSize)
                    : 0;

            String content = state.batchTotal > 1
                    ? state.fileName + " (" + formatSize(received) + " / " + formatSize(state.fileSize) + ")"
                    : (state.fileSize > 0
                            ? formatSize(received) + " / " + formatSize(state.fileSize) + " (" + percent + "%)"
                            : formatSize(received));

            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);

            if (state.fileSize > 0) {
                builder.setProgress(100, percent, false);
            } else {
                builder.setProgress(0, 0, true);
            }

            Intent cancelIntent = new Intent(context, ClipboardSyncService.class);
            cancelIntent.setAction(ClipboardSyncService.ACTION_CANCEL_FILE_UPLOAD);
            if (state.uploadId != null) {
                cancelIntent.putExtra(ClipboardSyncService.EXTRA_UPLOAD_ID, state.uploadId);
            }
            if (state.batchId != null) {
                cancelIntent.putExtra(ClipboardSyncService.EXTRA_BATCH_ID, state.batchId);
            }
            PendingIntent pendingCancel = PendingIntent.getService(
                    context,
                    state.uploadId != null ? state.uploadId.hashCode() : 1024,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Action cancelAction = new Notification.Action.Builder(
                    0, "Cancel", pendingCancel).build();
            builder.addAction(cancelAction);

            manager.notify(NOTIFICATION_ID_RECEIVE_PROGRESS, builder.build());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to publish receive progress notification", throwable);
        }
    }

    private void cancelReceiveProgressNotification() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID_RECEIVE_PROGRESS);
            }
        } catch (Throwable ignored) {
        }
    }

    private void publishReceiveResultNotification(String title, String message, Uri fileUri, String mimeType) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID_OFFERS)
                    : new Notification.Builder(context);

            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setOngoing(false)
                    .setAutoCancel(true);

            PendingIntent contentIntent = null;
            if (fileUri != null) {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                String type = (mimeType != null && !mimeType.isBlank()) ? mimeType : "*/*";
                viewIntent.setDataAndType(fileUri, type);
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                contentIntent = PendingIntent.getActivity(
                        context,
                        fileUri.hashCode(),
                        viewIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                try {
                    Intent downloadsIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                    downloadsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    contentIntent = PendingIntent.getActivity(
                            context,
                            1025,
                            downloadsIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                } catch (Throwable ignored) {
                }
            }
            if (contentIntent != null) {
                builder.setContentIntent(contentIntent);
            }

            manager.notify(NOTIFICATION_ID_RECEIVE_RESULT, builder.build());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to publish receive result notification", throwable);
        }
    }

    public void showOfferNotification(String batchId, List<IncomingOfferRegistry.FileInfo> files, long totalSize) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            ensureChannel(context);
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID_OFFERS)
                    : new Notification.Builder(context);

            int fileCount = files != null ? files.size() : 1;
            String firstFileName = (files != null && !files.isEmpty()) ? files.get(0).name : "file";
            String title;
            String text;
            if (fileCount <= 1) {
                title = "Incoming file from PC";
                text = firstFileName + " (" + formatSize(totalSize) + ")";
            } else {
                title = "Incoming " + fileCount + " files from PC";
                text = firstFileName + " and " + (fileCount - 1) + " more (" + formatSize(totalSize) + ")";
            }

            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(false)
                    .setAutoCancel(true);

            Intent acceptIntent = new Intent(context, ClipboardSyncService.class);
            acceptIntent.setAction(ClipboardSyncService.ACTION_ACCEPT_FILE_OFFER);
            acceptIntent.putExtra(ClipboardSyncService.EXTRA_BATCH_ID, batchId);
            PendingIntent pendingAccept = PendingIntent.getService(
                    context,
                    batchId != null ? batchId.hashCode() : 1026,
                    acceptIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent declineIntent = new Intent(context, ClipboardSyncService.class);
            declineIntent.setAction(ClipboardSyncService.ACTION_DECLINE_FILE_OFFER);
            declineIntent.putExtra(ClipboardSyncService.EXTRA_BATCH_ID, batchId);
            PendingIntent pendingDecline = PendingIntent.getService(
                    context,
                    (batchId != null ? batchId.hashCode() : 1026) + 1,
                    declineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Action acceptAction = new Notification.Action.Builder(
                    0, "Accept", pendingAccept).build();
            Notification.Action declineAction = new Notification.Action.Builder(
                    0, "Decline", pendingDecline).build();
            builder.addAction(acceptAction);
            builder.addAction(declineAction);

            manager.notify(NOTIFICATION_ID_OFFER, builder.build());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to publish offer notification", throwable);
        }
    }

    public void dismissOfferNotification(String batchId) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID_OFFER);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to cancel offer notification", throwable);
        }
    }

    private static final class ReceiveBatchState {
        final String uploadId;
        final String fileName;
        final long fileSize;
        final String batchId;
        final int batchIndex;
        final int batchTotal;
        final AtomicLong bytesReceived = new AtomicLong(0L);
        long lastProgressUpdateElapsedMs;
        volatile boolean cancelled;

        ReceiveBatchState(String uploadId, String fileName, long fileSize, long fileOffset,
                String batchId, int batchIndex, int batchTotal) {
            this.uploadId = uploadId;
            this.fileName = fileName == null ? "file" : fileName;
            this.fileSize = fileSize;
            this.bytesReceived.set(Math.max(0L, fileOffset));
            this.batchId = batchId;
            this.batchIndex = Math.max(1, batchIndex);
            this.batchTotal = Math.max(1, batchTotal);
        }
    }

    private static final class ReceiveBatch {
        final String batchId;
        final int batchTotal;
        final Set<String> completedUploadIds = ConcurrentHashMap.newKeySet();
        volatile long lastActivityMs;

        ReceiveBatch(String batchId, int batchTotal, long nowMs) {
            this.batchId = batchId;
            this.batchTotal = Math.max(1, batchTotal);
            this.lastActivityMs = nowMs;
        }
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context == null) {
            return;
        }
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel transferChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebClip File Transfer",
                    NotificationManager.IMPORTANCE_LOW);
            transferChannel.setDescription("Progress for WebClip file transfers");
            manager.createNotificationChannel(transferChannel);

            NotificationChannel offersChannel = new NotificationChannel(
                    CHANNEL_ID_OFFERS,
                    "WebClip File Offers & Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            offersChannel.setDescription("Consent prompts and completion status for WebClip file transfers");
            manager.createNotificationChannel(offersChannel);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static final class BatchState {
        final String batchId;
        final int totalFiles;
        final long totalBytes;
        final String firstFileName;
        final AtomicLong bytesTransferred = new AtomicLong(0L);
        final Set<String> completedTokens = ConcurrentHashMap.newKeySet();
        long lastProgressUpdateElapsedMs;
        volatile boolean cancelled;

        BatchState(String batchId, int totalFiles, long totalBytes, String firstFileName) {
            this.batchId = batchId;
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
            this.firstFileName = firstFileName == null ? "file" : firstFileName;
        }
    }
}
