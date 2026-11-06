package dev.jason.gboardpatches.extension.filetransfer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncWebPortal;

public final class WebClipShareActivity extends Activity {
    private static final String TAG = "WebClipShareActivity";
    private static final long MAX_STAGED_FILE_SIZE = 500L * 1024L * 1024L;
    private static final long MIN_CACHE_FREE_BYTES = 100L * 1024L * 1024L;

    private static Executor testingWorkerExecutor;

    private Executor workerExecutor;

    public static void cleanOrphanedStagedFiles(Context context) {
        if (context == null) {
            return;
        }
        File cacheDir = context.getCacheDir();
        if (cacheDir == null || !cacheDir.isDirectory()) {
            return;
        }
        File[] files = cacheDir.listFiles((dir, name) -> name != null
                && name.startsWith("webclip_staged_") && name.endsWith(".tmp"));
        if (files != null) {
            for (File file : files) {
                try {
                    file.delete();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void setWorkerExecutorForTesting(Executor executor) {
        testingWorkerExecutor = executor;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        workerExecutor = testingWorkerExecutor != null
                ? testingWorkerExecutor
                : Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "WebClipShareWorker");
                    t.setDaemon(true);
                    return t;
                });

        handleShare();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void handleShare() {
        ClipboardSyncWebPortal portal = ClipboardSyncService.getActivePortal();
        if (portal == null || !portal.hasConnectedDesktopClients()) {
            Toast.makeText(this, "PC not connected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        List<Uri> uris = extractUris(intent);
        if (uris.isEmpty()) {
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                Toast.makeText(this, "WebClip only transfers files. Copy text to sync.", Toast.LENGTH_SHORT).show();
            }
            finish();
            return;
        }

        if (uris.size() > FileOfferRegistry.MAX_OFFERS_PER_BATCH) {
            uris = uris.subList(0, FileOfferRegistry.MAX_OFFERS_PER_BATCH);
        }

        List<Uri> targetUris = uris;
        String mimeType = intent.getType();

        workerExecutor.execute(() -> {
            try {
                processShareUris(portal, targetUris, mimeType);
            } finally {
                runOnUiThread(this::finish);
            }
        });
    }

    private void processShareUris(ClipboardSyncWebPortal portal, List<Uri> uris, String defaultMime) {
        int totalCount = uris.size();
        String batchId = FileOfferRegistry.generateToken();
        List<FileOffer> createdOffers = new ArrayList<>();
        boolean stagingRejected = false;

        for (int i = 0; i < totalCount; i++) {
            Uri uri = uris.get(i);
            try {
                FileOffer offer = processUri(uri, batchId, i, totalCount, defaultMime);
                if (offer != null) {
                    createdOffers.add(offer);
                }
            } catch (StagedLimitExceededException e) {
                stagingRejected = true;
            }
        }

        if (createdOffers.isEmpty()) {
            if (stagingRejected) {
                FileTransferNotificationManager.getInstance().onBatchFailed(
                        batchId, "File exceeds 500 MB limit or storage low");
                runOnUiThread(() -> Toast.makeText(
                        this, "File exceeds 500 MB limit or storage low", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(
                        this, "Failed to read files", Toast.LENGTH_SHORT).show());
            }
            return;
        }

        FileOfferRegistry registry = portal.getFileOfferRegistry();
        for (FileOffer offer : createdOffers) {
            registry.register(offer);
            portal.broadcastFileOffer(offer);
        }

        FileTransferNotificationManager.getInstance().onBatchStarted(this, batchId, createdOffers);
        runOnUiThread(() -> Toast.makeText(this, "Sending to PC...", Toast.LENGTH_SHORT).show());
    }

    private FileOffer processUri(Uri uri, String batchId, int index, int total, String defaultMime)
            throws StagedLimitExceededException {
        if (uri == null) {
            return null;
        }

        String scheme = uri.getScheme();
        if (!ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme)) {
            Log.w(TAG, "Rejected non-content URI: " + uri);
            return null;
        }

        String authority = uri.getAuthority();
        String myPackage = getPackageName();
        if (authority != null && (authority.equals(myPackage) || authority.startsWith(myPackage + "."))) {
            Log.w(TAG, "Rejected internal package authority URI: " + uri);
            return null;
        }

        ContentResolver resolver = getContentResolver();
        String name = queryFileName(resolver, uri);
        if (name == null || name.isBlank()) {
            name = uri.getLastPathSegment();
            if (name == null || name.isBlank()) {
                name = "file_" + (index + 1);
            }
        }

        String mime = resolver.getType(uri);
        if (mime == null || mime.isBlank()) {
            mime = defaultMime;
            if (mime == null || mime.isBlank() || "*/*".equals(mime)) {
                mime = "application/octet-stream";
            }
        }

        long size = queryFileSize(resolver, uri);
        ParcelFileDescriptor pfd = null;
        try {
            pfd = resolver.openFileDescriptor(uri, "r");
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to open ParcelFileDescriptor for " + uri, throwable);
        }

        if (pfd == null) {
            return null;
        }

        File stagedFile = null;
        if (size <= 0L) {
            long statSize = pfd.getStatSize();
            if (statSize > 0L) {
                size = statSize;
            } else {
                try {
                    stagedFile = stageToCache(pfd);
                    pfd.close();
                    size = stagedFile.length();
                    pfd = ParcelFileDescriptor.open(stagedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (Throwable throwable) {
                    try {
                        pfd.close();
                    } catch (Throwable ignored) {
                    }
                    if (stagedFile != null) {
                        stagedFile.delete();
                    }
                    if (throwable instanceof StagedLimitExceededException) {
                        throw (StagedLimitExceededException) throwable;
                    }
                    Log.w(TAG, "Failed to stage file to cache for " + uri, throwable);
                    return null;
                }
            }
        }

        if (size < 0L) {
            try {
                pfd.close();
            } catch (Throwable ignored) {
            }
            if (stagedFile != null) {
                stagedFile.delete();
            }
            return null;
        }

        String token = FileOfferRegistry.generateToken();
        return FileOffer.fromParcelFileDescriptor(
                token,
                pfd,
                name,
                size,
                mime,
                batchId,
                index,
                total,
                null,
                stagedFile);
    }

    private File stageToCache(ParcelFileDescriptor sourcePfd)
            throws IOException, StagedLimitExceededException {
        File cacheDir = getCacheDir();
        if (cacheDir == null || cacheDir.getUsableSpace() < MIN_CACHE_FREE_BYTES) {
            throw new StagedLimitExceededException();
        }
        File temp = File.createTempFile("webclip_staged_", ".tmp", cacheDir);
        try (FileInputStream in = new FileInputStream(sourcePfd.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            long totalWritten = 0L;
            while ((read = in.read(buffer)) != -1) {
                totalWritten += read;
                if (totalWritten > MAX_STAGED_FILE_SIZE || cacheDir.getUsableSpace() < MIN_CACHE_FREE_BYTES) {
                    temp.delete();
                    throw new StagedLimitExceededException();
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (Throwable t) {
            temp.delete();
            if (t instanceof StagedLimitExceededException) {
                throw (StagedLimitExceededException) t;
            }
            throw t;
        }
        return temp;
    }

    private String queryFileName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) {
                    return cursor.getString(col);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private long queryFileSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (col >= 0 && !cursor.isNull(col)) {
                    return cursor.getLong(col);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private static List<Uri> extractUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                uris.add(stream);
            } else if (intent.getData() != null) {
                uris.add(intent.getData());
            } else if (intent.getClipData() != null) {
                ClipData clipData = intent.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri itemUri = clipData.getItemAt(i).getUri();
                    if (itemUri != null) {
                        uris.add(itemUri);
                    }
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) {
                for (Uri uri : streams) {
                    if (uri != null) {
                        uris.add(uri);
                    }
                }
            }
            if (uris.isEmpty() && intent.getClipData() != null) {
                ClipData clipData = intent.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri itemUri = clipData.getItemAt(i).getUri();
                    if (itemUri != null) {
                        uris.add(itemUri);
                    }
                }
            }
        }
        return uris;
    }

    public static final class StagedLimitExceededException extends Exception {
    }
}
