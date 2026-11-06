package dev.jason.gboardpatches.extension.filetransfer;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class ActiveUploadRegistry {
    public static final long IDLE_EXPIRY_MS = 15L * 60L * 1000L;
    public static final long CANCELLED_BATCH_EXPIRY_MS = 10L * 60L * 1000L;
    public static final long COMPLETED_UPLOAD_EXPIRY_MS = 10L * 60L * 1000L;

    private static final class CompletedUpload {
        final long size;
        final long completedAtMs;

        CompletedUpload(long size, long completedAtMs) {
            this.size = size;
            this.completedAtMs = completedAtMs;
        }
    }

    public static final class UploadSession {
        private final String uploadId;
        private final String fileName;
        private final long totalSize;
        private final UploadSink sink;
        private final String batchId;
        private final ReentrantLock writerLock = new ReentrantLock();
        private volatile Socket activeSocket;
        private volatile long currentOffset;
        private volatile long lastActivityMs;

        public UploadSession(String uploadId, String fileName, long totalSize, UploadSink sink,
                long initialOffset, long nowMs) {
            this(uploadId, fileName, totalSize, sink, initialOffset, nowMs, null);
        }

        public UploadSession(String uploadId, String fileName, long totalSize, UploadSink sink,
                long initialOffset, long nowMs, String batchId) {
            this.uploadId = uploadId;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.sink = sink;
            this.currentOffset = initialOffset;
            this.lastActivityMs = nowMs;
            this.batchId = batchId;
        }

        public String getUploadId() {
            return uploadId;
        }

        public String getFileName() {
            return fileName;
        }

        public String getBatchId() {
            return batchId;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public UploadSink getSink() {
            return sink;
        }

        public long getOffset() {
            return currentOffset;
        }

        public void setOffset(long offset) {
            this.currentOffset = offset;
        }

        public long getLastActivityMs() {
            return lastActivityMs;
        }

        public void touch(long nowMs) {
            this.lastActivityMs = nowMs;
        }

        public Socket getActiveSocket() {
            return activeSocket;
        }

        public void setActiveSocket(Socket socket) {
            this.activeSocket = socket;
        }

        public void abortActiveConnection() {
            Socket s = this.activeSocket;
            if (s != null && !s.isClosed()) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }

        public boolean tryAcquireWriter(long timeout, TimeUnit unit) throws InterruptedException {
            return writerLock.tryLock(timeout, unit);
        }

        public void releaseWriter(Socket socket) {
            if (this.activeSocket == socket) {
                this.activeSocket = null;
            }
            if (writerLock.isHeldByCurrentThread()) {
                writerLock.unlock();
            }
        }

        public void releaseWriter() {
            this.activeSocket = null;
            if (writerLock.isHeldByCurrentThread()) {
                writerLock.unlock();
            }
        }

        public boolean isWriterLockedByCurrentThread() {
            return writerLock.isHeldByCurrentThread();
        }
    }

    private static final ActiveUploadRegistry INSTANCE = new ActiveUploadRegistry();

    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> cancelledBatches = new ConcurrentHashMap<>();
    private final Map<String, CompletedUpload> completedUploads = new ConcurrentHashMap<>();

    public static ActiveUploadRegistry getInstance() {
        return INSTANCE;
    }

    public void recordCompletedUpload(String uploadId, long size) {
        if (uploadId != null && !uploadId.isBlank()) {
            completedUploads.put(uploadId, new CompletedUpload(size, System.currentTimeMillis()));
        }
    }

    public Long getCompletedUploadSize(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return null;
        }
        CompletedUpload completed = completedUploads.get(uploadId);
        if (completed == null) {
            return null;
        }
        if (System.currentTimeMillis() - completed.completedAtMs > COMPLETED_UPLOAD_EXPIRY_MS) {
            completedUploads.remove(uploadId);
            return null;
        }
        return completed.size;
    }

    public UploadSession get(String uploadId) {
        if (uploadId == null) {
            return null;
        }
        return sessions.get(uploadId);
    }

    public void put(UploadSession session) {
        if (session != null && session.getUploadId() != null) {
            sessions.put(session.getUploadId(), session);
        }
    }

    public UploadSession remove(String uploadId) {
        if (uploadId == null) {
            return null;
        }
        return sessions.remove(uploadId);
    }

    public synchronized List<UploadSession> abortAndRemoveBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Collections.emptyList();
        }
        List<UploadSession> aborted = new ArrayList<>();
        Iterator<Map.Entry<String, UploadSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UploadSession> entry = iterator.next();
            UploadSession session = entry.getValue();
            if (batchId.equals(session.getBatchId())) {
                iterator.remove();
                session.abortActiveConnection();
                session.getSink().abort();
                aborted.add(session);
            }
        }
        return aborted;
    }

    public void recordCancelledBatch(String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            cancelledBatches.put(batchId, System.currentTimeMillis());
        }
    }

    public boolean isBatchCancelled(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return false;
        }
        Long cancelledAt = cancelledBatches.get(batchId);
        if (cancelledAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - cancelledAt > CANCELLED_BATCH_EXPIRY_MS) {
            cancelledBatches.remove(batchId);
            return false;
        }
        return true;
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        for (UploadSession session : sessions.values()) {
            session.abortActiveConnection();
            session.getSink().abort();
        }
        sessions.clear();
        cancelledBatches.clear();
        completedUploads.clear();
    }

    public void cleanExpiredSessions(long nowMs) {
        Iterator<Map.Entry<String, UploadSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UploadSession> entry = iterator.next();
            UploadSession session = entry.getValue();
            if (nowMs - session.getLastActivityMs() >= IDLE_EXPIRY_MS) {
                iterator.remove();
                session.abortActiveConnection();
                session.getSink().abort();
            }
        }
        cancelledBatches.entrySet().removeIf(entry -> nowMs - entry.getValue() > CANCELLED_BATCH_EXPIRY_MS);
        completedUploads.entrySet().removeIf(entry -> nowMs - entry.getValue().completedAtMs > COMPLETED_UPLOAD_EXPIRY_MS);
    }
}

