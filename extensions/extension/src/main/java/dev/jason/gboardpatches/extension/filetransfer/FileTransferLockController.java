package dev.jason.gboardpatches.extension.filetransfer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class FileTransferLockController {
    private static final String TAG = "GboardFileTransferLock";
    private static final int RELEASE_ATTEMPTS = 2;
    private static final long SAFETY_TIMEOUT_MS = 15L * 60L * 1000L;
    private static final long TOUCH_RATE_LIMIT_MS = 1000L;

    private final Context context;
    private final AtomicInteger activeStreams = new AtomicInteger(0);
    private final ScheduledExecutorService timeoutExecutor;
    private ScheduledFuture<?> safetyTimeoutFuture;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private long lastTouchMs;

    public FileTransferLockController(Context context) {
        this(context, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebClipFileLockTimeout");
            t.setDaemon(true);
            return t;
        }));
    }

    public FileTransferLockController(Context context, ScheduledExecutorService timeoutExecutor) {
        this.context = context;
        this.timeoutExecutor = timeoutExecutor;
    }

    public synchronized void acquire() {
        int count = activeStreams.incrementAndGet();
        if (count == 1) {
            acquireLocks();
            scheduleSafetyTimeout();
        }
    }

    public synchronized void release() {
        int count = activeStreams.decrementAndGet();
        if (count <= 0) {
            activeStreams.set(0);
            lastTouchMs = 0L;
            cancelSafetyTimeout();
            releaseLocks();
        }
    }

    public synchronized void releaseAll() {
        activeStreams.set(0);
        lastTouchMs = 0L;
        cancelSafetyTimeout();
        releaseLocks();
    }

    public synchronized void touch() {
        if (activeStreams.get() > 0) {
            long now = System.currentTimeMillis();
            if (now - lastTouchMs < TOUCH_RATE_LIMIT_MS) {
                return;
            }
            lastTouchMs = now;
            cancelSafetyTimeout();
            scheduleSafetyTimeout();
        }
    }

    public int getActiveStreamsCount() {
        return activeStreams.get();
    }

    public synchronized boolean isWakeLockHeld() {
        return wakeLock != null && wakeLock.isHeld();
    }

    public synchronized boolean isWifiLockHeld() {
        return wifiLock != null && wifiLock.isHeld();
    }

    private void acquireLocks() {
        if (context == null) {
            return;
        }
        try {
            if (wakeLock == null) {
                PowerManager manager = context.getSystemService(PowerManager.class);
                if (manager != null) {
                    wakeLock = manager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK, "GboardPatches:WebClipFileLock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(SAFETY_TIMEOUT_MS);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not acquire file transfer wake lock", throwable);
        }

        try {
            if (wifiLock == null) {
                WifiManager manager = context.getSystemService(WifiManager.class);
                if (manager != null) {
                    wifiLock = manager.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GboardPatches:WebClipWifiLock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not acquire file transfer Wi-Fi lock", throwable);
        }
    }

    private void releaseLocks() {
        for (int attempt = 0; wakeLock != null && attempt < RELEASE_ATTEMPTS; attempt++) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
            } catch (Throwable throwable) {
                if (attempt + 1 == RELEASE_ATTEMPTS) {
                    Log.w(TAG, "Could not release file transfer wake lock", throwable);
                }
            }
        }
        for (int attempt = 0; wifiLock != null && attempt < RELEASE_ATTEMPTS; attempt++) {
            try {
                if (wifiLock.isHeld()) {
                    wifiLock.release();
                }
                wifiLock = null;
            } catch (Throwable throwable) {
                if (attempt + 1 == RELEASE_ATTEMPTS) {
                    Log.w(TAG, "Could not release file transfer Wi-Fi lock", throwable);
                }
            }
        }
    }

    private synchronized void scheduleSafetyTimeout() {
        cancelSafetyTimeout();
        if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
            try {
                safetyTimeoutFuture = timeoutExecutor.schedule(
                        this::onSafetyTimeout, SAFETY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
    }

    private synchronized void cancelSafetyTimeout() {
        if (safetyTimeoutFuture != null) {
            safetyTimeoutFuture.cancel(false);
            safetyTimeoutFuture = null;
        }
    }

    private synchronized void onSafetyTimeout() {
        activeStreams.set(0);
        releaseLocks();
    }
}
