package dev.jason.gboardpatches.extension.webclipboard;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class ScreenshotCaptureObserver extends ContentObserver {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[WebClipboardObserver]";
    private static final long MAX_SCREENSHOT_AGE_MS = 30_000L;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    public interface Callback {
        void onScreenshotCaptured(byte[] imageBytes, String mimeType, Uri contentUri);
    }

    private final Context context;
    private final Callback callback;
    private long lastHandledImageId = -1L;

    public ScreenshotCaptureObserver(Context context, Callback callback) {
        super(new Handler(Looper.getMainLooper()));
        this.context = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.callback = callback;
        seedInitialMaxId();
    }

    public static ScreenshotCaptureObserver register(Context context, Callback callback) {
        if (context == null || callback == null) {
            return null;
        }
        ScreenshotCaptureObserver observer = new ScreenshotCaptureObserver(context, callback);
        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer);
            Log.i(TAG, LOG_PREFIX + " registered MediaStore screenshot observer (seedMaxId=" + observer.lastHandledImageId + ")");
            return observer;
        } catch (Throwable throwable) {
            Log.w(TAG, LOG_PREFIX + " failed to register screenshot observer", throwable);
            return null;
        }
    }

    public void unregister() {
        try {
            context.getContentResolver().unregisterContentObserver(this);
            Log.i(TAG, LOG_PREFIX + " unregistered MediaStore screenshot observer");
        } catch (Throwable ignored) {
        }
    }

    private void seedInitialMaxId() {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) return;
            String[] projection = new String[] { MediaStore.Images.Media._ID };
            String sortOrder = MediaStore.Images.Media._ID + " DESC";
            try (Cursor cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder)) {
                if (cursor != null && cursor.moveToFirst()) {
                    lastHandledImageId = cursor.getLong(0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        processLatestScreenshot(uri);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        processLatestScreenshot(null);
    }

    public void processLatestScreenshot(Uri triggerUri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return;
            }

            String[] projection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection = new String[] {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.MIME_TYPE,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.RELATIVE_PATH,
                        MediaStore.Images.Media.IS_PENDING
                };
            } else {
                projection = new String[] {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.MIME_TYPE,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DATA
                };
            }

            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC, "
                    + MediaStore.Images.Media._ID + " DESC";

            try (Cursor cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder)) {

                if (cursor == null) {
                    return;
                }

                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE);
                int dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                int pendingCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING) : -1;
                int relCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) : -1;
                int dataCol = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        ? cursor.getColumnIndex(MediaStore.Images.Media.DATA) : -1;

                long now = System.currentTimeMillis();

                while (cursor.moveToNext()) {
                    long imageId = cursor.getLong(idCol);
                    if (imageId <= lastHandledImageId) {
                        // Reached already-processed historical images; stop scanning.
                        break;
                    }

                    if (pendingCol >= 0 && cursor.getInt(pendingCol) != 0) {
                        continue;
                    }

                    String displayName = nameCol >= 0 ? cursor.getString(nameCol) : "";
                    String pathOrRelative = relCol >= 0 ? cursor.getString(relCol)
                            : (dataCol >= 0 ? cursor.getString(dataCol) : "");

                    String lowerName = displayName != null ? displayName.toLowerCase(Locale.ROOT) : "";
                    if (lowerName.contains("webclip")) {
                        // Mark incoming webclip screenshot as handled so older screenshots below it are never rescanned
                        lastHandledImageId = Math.max(lastHandledImageId, imageId);
                        continue;
                    }

                    if (!isScreenshotFile(displayName, pathOrRelative)) {
                        continue;
                    }

                    long dateAddedSec = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;
                    long dateAddedMs = dateAddedSec > 0 ? dateAddedSec * 1000L : now;
                    if (Math.abs(now - dateAddedMs) > MAX_SCREENSHOT_AGE_MS) {
                        continue;
                    }

                    lastHandledImageId = Math.max(lastHandledImageId, imageId);

                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);

                    byte[] imageBytes = readImageBytes(resolver, contentUri, MAX_IMAGE_BYTES);
                    if (imageBytes == null || imageBytes.length == 0) {
                        continue;
                    }

                    String mimeType = mimeCol >= 0 ? cursor.getString(mimeCol) : "";
                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = "image/png";
                    }

                    Log.i(TAG, LOG_PREFIX + " new screenshot detected id=" + imageId
                            + ", name=" + displayName
                            + ", bytes=" + imageBytes.length
                            + ", mime=" + mimeType);

                    callback.onScreenshotCaptured(imageBytes, mimeType, contentUri);
                    break;
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, LOG_PREFIX + " error checking screenshot", throwable);
        }
    }

    public static boolean isScreenshotFile(String displayName, String pathOrRelative) {
        String name = displayName != null ? displayName.toLowerCase(Locale.ROOT) : "";
        String path = pathOrRelative != null ? pathOrRelative.toLowerCase(Locale.ROOT) : "";

        if (name.contains("pending") || name.endsWith(".tmp") || name.endsWith(".crdownload") || name.contains("webclip")) {
            return false;
        }

        return name.contains("screenshot")
                || name.startsWith("scr_")
                || path.contains("screenshot")
                || path.contains("screenshots");
    }

    private static byte[] readImageBytes(ContentResolver resolver, Uri uri, int maxBytes) {
        if (resolver == null || uri == null) {
            return null;
        }
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
