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
    private static final String TAG = "GboardWebClipboard";
    private static final String LOG_PREFIX = "[gboard-screenshot-sync]";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_SCREENSHOT_AGE_MS = 60_000L;

    public interface Callback {
        void onScreenshotCaptured(byte[] imageBytes, String mimeType, Uri uri);
    }

    private final Context context;
    private final Callback callback;
    private long lastHandledImageId = -1L;
    private long lastHandledTimeMs = 0L;

    public ScreenshotCaptureObserver(Context context, Callback callback) {
        super(new Handler(Looper.getMainLooper()));
        this.context = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.callback = callback;
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
            Log.i(TAG, LOG_PREFIX + " registered MediaStore screenshot observer");
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

                if (cursor == null || !cursor.moveToFirst()) {
                    return;
                }

                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE);
                int dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);

                long imageId = cursor.getLong(idCol);
                String displayName = nameCol >= 0 ? cursor.getString(nameCol) : "";
                String mimeType = mimeCol >= 0 ? cursor.getString(mimeCol) : "";
                long dateAddedSec = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int pendingCol = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING);
                    if (pendingCol >= 0 && cursor.getInt(pendingCol) != 0) {
                        return;
                    }
                }

                String pathOrRelative = "";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int relCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                    if (relCol >= 0) {
                        pathOrRelative = cursor.getString(relCol);
                    }
                } else {
                    int dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    if (dataCol >= 0) {
                        pathOrRelative = cursor.getString(dataCol);
                    }
                }

                if (!isScreenshotFile(displayName, pathOrRelative)) {
                    return;
                }

                long now = System.currentTimeMillis();
                long dateAddedMs = dateAddedSec > 0 ? dateAddedSec * 1000L : now;
                if (Math.abs(now - dateAddedMs) > MAX_SCREENSHOT_AGE_MS) {
                    return;
                }

                if (imageId == lastHandledImageId && (now - lastHandledTimeMs) < 3000L) {
                    return;
                }

                Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);

                byte[] imageBytes = readImageBytes(resolver, contentUri, MAX_IMAGE_BYTES);
                if (imageBytes == null || imageBytes.length == 0) {
                    return;
                }

                lastHandledImageId = imageId;
                lastHandledTimeMs = now;

                if (mimeType == null || mimeType.isEmpty()) {
                    mimeType = "image/png";
                }

                Log.i(TAG, LOG_PREFIX + " screenshot detected id=" + imageId
                        + ", name=" + displayName
                        + ", bytes=" + imageBytes.length
                        + ", mime=" + mimeType);

                callback.onScreenshotCaptured(imageBytes, mimeType, contentUri);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, LOG_PREFIX + " error checking screenshot", throwable);
        }
    }

    public static boolean isScreenshotFile(String displayName, String pathOrRelative) {
        String name = displayName != null ? displayName.toLowerCase(Locale.ROOT) : "";
        String path = pathOrRelative != null ? pathOrRelative.toLowerCase(Locale.ROOT) : "";

        if (name.contains("pending") || name.endsWith(".tmp") || name.endsWith(".crdownload")) {
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
