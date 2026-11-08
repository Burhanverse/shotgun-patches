package dev.jason.gboardpatches.extension.filetransfer;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class DirectFileUploadSink implements UploadSink {
    private final Context context;
    private final File file;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private long currentPosition;
    private boolean published;
    private Uri scannedUri;

    public DirectFileUploadSink(Context context, String fileName) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        File targetDir = resolveTargetDirectory(this.context);
        if (targetDir == null) {
            throw new IOException("No writable directory available");
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDir.getAbsolutePath());
        }
        this.file = new File(targetDir, fileName);
        this.raf = new RandomAccessFile(this.file, "rw");
        this.channel = this.raf.getChannel();
    }

    private static File resolveTargetDirectory(Context context) {
        try {
            File publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDownload != null && (publicDownload.exists() || publicDownload.mkdirs())) {
                File webClipDir = new File(publicDownload, "WebClip");
                if ((webClipDir.exists() || webClipDir.mkdirs()) && webClipDir.canWrite()) {
                    return webClipDir;
                }
                if (publicDownload.canWrite()) {
                    return publicDownload;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            File extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (extFiles != null) {
                File webClipDir = new File(extFiles, "WebClip");
                if (webClipDir.exists() || webClipDir.mkdirs()) {
                    return webClipDir;
                }
                return extFiles;
            }
        } catch (Throwable ignored) {
        }
        File internal = new File(context.getFilesDir(), "WebClip");
        if (internal.exists() || internal.mkdirs()) {
            return internal;
        }
        return context.getFilesDir();
    }

    @Override
    public synchronized void writeAt(long offset, byte[] data, int length) throws IOException {
        if (published) {
            throw new IOException("Sink already published");
        }
        if (currentPosition != offset) {
            channel.position(offset);
        }
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
        try {
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    null,
                    (path, uri) -> {
                        if (uri != null) {
                            scannedUri = uri;
                        }
                    });
        } catch (Throwable ignored) {
        }
        published = true;
    }

    @Override
    public synchronized void abort() {
        try {
            close();
        } catch (Throwable ignored) {
        }
        if (!published && file != null && file.exists()) {
            try {
                file.delete();
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
            if (raf != null) {
                raf.close();
            }
        }
    }

    @Override
    public synchronized long getPosition() {
        return currentPosition;
    }

    @Override
    public synchronized Uri getUri() {
        if (scannedUri != null) {
            return scannedUri;
        }
        return Uri.fromFile(file);
    }
}
