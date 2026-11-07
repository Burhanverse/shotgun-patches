package dev.jason.gboardpatches.extension.filetransfer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class MediaStoreUploadSink implements UploadSink {
    private final Context context;
    private final Uri uri;
    private final ParcelFileDescriptor pfd;
    private final FileOutputStream fos;
    private final FileChannel channel;
    private long currentPosition;
    private boolean published;

    public MediaStoreUploadSink(Context context, String fileName, String mimeType) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new IOException("API 29+ required");
        }
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        ContentResolver resolver = this.context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, (mimeType != null && !mimeType.isEmpty()) ? mimeType : "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/WebClip/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri createdUri = null;
        try {
            createdUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        } catch (Throwable ignored) {
        }
        if (createdUri == null) {
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/");
            try {
                createdUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            } catch (Throwable ignored) {
            }
        }
        if (createdUri == null) {
            values.remove(MediaStore.Downloads.RELATIVE_PATH);
            try {
                createdUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            } catch (Throwable ignored) {
            }
        }
        if (createdUri == null) {
            throw new IOException("Failed to create MediaStore entry");
        }
        this.uri = createdUri;
        try {
            ParcelFileDescriptor descriptor = null;
            try {
                descriptor = resolver.openFileDescriptor(this.uri, "rwt");
            } catch (Throwable ignored) {
                try {
                    descriptor = resolver.openFileDescriptor(this.uri, "w");
                } catch (Throwable ignored2) {
                    descriptor = resolver.openFileDescriptor(this.uri, "rw");
                }
            }
            if (descriptor == null) {
                throw new IOException("Failed to open file descriptor");
            }
            this.pfd = descriptor;
            this.fos = new FileOutputStream(this.pfd.getFileDescriptor());
            this.channel = this.fos.getChannel();
        } catch (Throwable throwable) {
            abort();
            throw new IOException("Failed to open sink output stream: " + throwable.getMessage(), throwable);
        }
    }

    public Uri getUri() {
        return uri;
    }

    @Override
    public synchronized void writeAt(long offset, byte[] data, int length) throws IOException {
        if (published) {
            throw new IOException("Sink already published");
        }
        channel.position(offset);
        channel.write(ByteBuffer.wrap(data, 0, length));
        currentPosition = offset + length;
    }

    @Override
    public synchronized void fsync() throws IOException {
        if (channel.isOpen()) {
            channel.force(true);
        }
    }

    @Override
    public synchronized void publish() throws IOException {
        if (published) {
            return;
        }
        fsync();
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        published = true;
    }

    @Override
    public synchronized void abort() {
        try {
            close();
        } catch (Throwable ignored) {
        }
        if (!published && uri != null) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } finally {
                if (pfd != null) {
                    pfd.close();
                }
            }
        }
    }

    @Override
    public synchronized long getPosition() {
        return currentPosition;
    }
}
