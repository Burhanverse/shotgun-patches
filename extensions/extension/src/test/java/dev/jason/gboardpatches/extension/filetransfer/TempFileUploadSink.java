package dev.jason.gboardpatches.extension.filetransfer;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class TempFileUploadSink implements UploadSink {
    private final File tempFile;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private long currentPosition;
    private boolean published;

    public TempFileUploadSink() throws IOException {
        this(File.createTempFile("webclip_upload_", ".part"));
    }

    public TempFileUploadSink(File file) throws IOException {
        this.tempFile = file;
        this.raf = new RandomAccessFile(this.tempFile, "rw");
        this.channel = this.raf.getChannel();
    }

    public File getFile() {
        return tempFile;
    }

    public boolean isPublished() {
        return published;
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
        published = true;
    }

    @Override
    public synchronized void abort() {
        try {
            close();
        } catch (Throwable ignored) {
        }
        if (!published && tempFile != null && tempFile.exists()) {
            tempFile.delete();
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
}

