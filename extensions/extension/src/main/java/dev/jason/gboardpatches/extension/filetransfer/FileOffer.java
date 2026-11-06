package dev.jason.gboardpatches.extension.filetransfer;

import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class FileOffer implements Closeable {
    private final String token;
    private final ParcelFileDescriptor pfd;
    private final InputStream inputStream;
    private final FileChannel fileChannel;
    private final String name;
    private final long size;
    private final String mimeType;
    private final String batchId;
    private final int index;
    private final int total;
    private final String targetClientId;
    private final boolean seekable;
    private final File stagedFile;
    private final AtomicLong bytesServed = new AtomicLong(0L);
    private final AtomicLong lastActivityMs;
    private final AtomicBoolean singleShotConsumed = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public FileOffer(
            String token,
            ParcelFileDescriptor pfd,
            InputStream inputStream,
            FileChannel fileChannel,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId,
            boolean seekable) {
        this(
                token,
                pfd,
                inputStream,
                fileChannel,
                name,
                size,
                mimeType,
                batchId,
                index,
                total,
                targetClientId,
                seekable,
                null);
    }

    public FileOffer(
            String token,
            ParcelFileDescriptor pfd,
            InputStream inputStream,
            FileChannel fileChannel,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId,
            boolean seekable,
            File stagedFile) {
        this.token = token == null ? "" : token;
        this.pfd = pfd;
        this.inputStream = inputStream;
        this.fileChannel = fileChannel;
        this.name = name == null ? "" : name;
        this.size = Math.max(0L, size);
        this.mimeType = (mimeType == null || mimeType.isBlank())
                ? "application/octet-stream" : mimeType;
        this.batchId = batchId == null ? "" : batchId;
        this.index = Math.max(0, index);
        this.total = Math.max(1, total);
        this.targetClientId = targetClientId == null ? "" : targetClientId;
        this.seekable = seekable;
        this.stagedFile = stagedFile;
        this.lastActivityMs = new AtomicLong(System.currentTimeMillis());
    }

    public static FileOffer fromParcelFileDescriptor(
            String token,
            ParcelFileDescriptor pfd,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId) {
        return fromParcelFileDescriptor(
                token,
                pfd,
                name,
                size,
                mimeType,
                batchId,
                index,
                total,
                targetClientId,
                null);
    }

    public static FileOffer fromParcelFileDescriptor(
            String token,
            ParcelFileDescriptor pfd,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId,
            File stagedFile) {
        FileInputStream fis = null;
        FileChannel channel = null;
        boolean seekable = false;
        if (pfd != null) {
            try {
                fis = new FileInputStream(pfd.getFileDescriptor());
                channel = fis.getChannel();
                try {
                    channel.position();
                    seekable = true;
                } catch (Throwable ignored) {
                    seekable = false;
                }
            } catch (Throwable ignored) {
                seekable = false;
            }
        }
        return new FileOffer(
                token,
                pfd,
                fis,
                channel,
                name,
                size,
                mimeType,
                batchId,
                index,
                total,
                targetClientId,
                seekable,
                stagedFile);
    }

    public static FileOffer forTesting(
            String token,
            FileChannel channel,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId,
            boolean seekable) {
        return new FileOffer(
                token,
                null,
                null,
                channel,
                name,
                size,
                mimeType,
                batchId,
                index,
                total,
                targetClientId,
                seekable);
    }

    public static FileOffer forTestingStream(
            String token,
            InputStream stream,
            String name,
            long size,
            String mimeType,
            String batchId,
            int index,
            int total,
            String targetClientId) {
        return new FileOffer(
                token,
                null,
                stream,
                null,
                name,
                size,
                mimeType,
                batchId,
                index,
                total,
                targetClientId,
                false);
    }

    public String getToken() {
        return token;
    }

    public ParcelFileDescriptor getParcelFileDescriptor() {
        return pfd;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getBatchId() {
        return batchId;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    public String getTargetClientId() {
        return targetClientId;
    }

    public boolean isSeekable() {
        return seekable;
    }

    public long getBytesServed() {
        return bytesServed.get();
    }

    public void recordBytesServed(long bytes, long nowMs) {
        bytesServed.addAndGet(bytes);
        lastActivityMs.set(nowMs);
    }

    public long getLastActivityMs() {
        return lastActivityMs.get();
    }

    public void touch(long nowMs) {
        lastActivityMs.set(nowMs);
    }

    public boolean markSingleShotStarted() {
        return singleShotConsumed.compareAndSet(false, true);
    }

    public boolean isSingleShotConsumed() {
        return singleShotConsumed.get();
    }

    public void markCompleted() {
        completed.set(true);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (Throwable ignored) {
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable ignored) {
                }
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Throwable ignored) {
                }
            }
            if (stagedFile != null) {
                try {
                    stagedFile.delete();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}

