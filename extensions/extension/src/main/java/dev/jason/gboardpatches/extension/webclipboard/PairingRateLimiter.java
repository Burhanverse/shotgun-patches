package dev.jason.gboardpatches.extension.webclipboard;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class PairingRateLimiter {
    static final int MAX_ENTRIES = 1024;
    static final int FAILURE_THRESHOLD = 3;
    static final long MAX_BACKOFF_SECONDS = 60L;
    static final int GLOBAL_CAP_FAILURES_PER_MINUTE = 10;
    static final long GLOBAL_WINDOW_MS = 60_000L;
    static final long ENTRY_EXPIRY_MS = 15 * 60_000L;
    static final long RECENT_AUTH_WINDOW_MS = 60 * 60_000L;

    private static final class Entry {
        int failures;
        long blockedUntilMs;
        long lastSeenMs;
        long lastSuccessfulAuthMs;

        Entry(long nowMs) {
            this.failures = 1;
            this.lastSeenMs = nowMs;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final ArrayDeque<Long> globalFailureTimestamps = new ArrayDeque<>();

    PairingRateLimiter() {
    }

    synchronized long getRetryAfterSeconds(InetAddress address) {
        return getRetryAfterSeconds(address, System.currentTimeMillis());
    }

    synchronized long getRetryAfterSeconds(InetAddress address, long nowMs) {
        cleanGlobalFailuresLocked(nowMs);

        String key = address != null ? address.getHostAddress() : null;
        Entry entry = key != null ? entries.get(key) : null;
        if (entry != null) {
            entry.lastSeenMs = nowMs;
            if (entry.blockedUntilMs > nowMs) {
                long remainingMs = entry.blockedUntilMs - nowMs;
                return Math.min(MAX_BACKOFF_SECONDS, Math.max(1L, (remainingMs + 999L) / 1000L));
            }
        }

        boolean isRecentlyAuthenticated = entry != null
                && entry.lastSuccessfulAuthMs > 0L
                && (nowMs - entry.lastSuccessfulAuthMs) < RECENT_AUTH_WINDOW_MS;

        if (!isRecentlyAuthenticated && globalFailureTimestamps.size() >= GLOBAL_CAP_FAILURES_PER_MINUTE) {
            long oldest = globalFailureTimestamps.peekFirst();
            long waitMs = (oldest + GLOBAL_WINDOW_MS) - nowMs;
            return Math.min(MAX_BACKOFF_SECONDS, Math.max(1L, (waitMs + 999L) / 1000L));
        }

        return 0L;
    }

    synchronized void recordFailure(InetAddress address) {
        recordFailure(address, System.currentTimeMillis());
    }

    synchronized void recordFailure(InetAddress address, long nowMs) {
        cleanGlobalFailuresLocked(nowMs);
        globalFailureTimestamps.addLast(nowMs);

        if (address == null) {
            return;
        }

        pruneExpiredEntriesLocked(nowMs);

        String key = address.getHostAddress();
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES) {
                Iterator<String> iterator = entries.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            entry = new Entry(nowMs);
            entries.put(key, entry);
        } else {
            entry.failures++;
            entry.lastSeenMs = nowMs;
        }

        if (entry.failures >= FAILURE_THRESHOLD) {
            int exponent = entry.failures - FAILURE_THRESHOLD;
            long backoffSeconds = 1L << Math.min(exponent, 6);
            if (backoffSeconds > MAX_BACKOFF_SECONDS) {
                backoffSeconds = MAX_BACKOFF_SECONDS;
            }
            entry.blockedUntilMs = nowMs + (backoffSeconds * 1000L);
        }
    }

    synchronized void recordSuccess(InetAddress address) {
        recordSuccess(address, System.currentTimeMillis());
    }

    synchronized void recordSuccess(InetAddress address, long nowMs) {
        if (address == null) {
            return;
        }
        String key = address.getHostAddress();
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES) {
                Iterator<String> iterator = entries.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            entry = new Entry(nowMs);
            entry.failures = 0;
            entries.put(key, entry);
        } else {
            entry.failures = 0;
            entry.blockedUntilMs = 0L;
            entry.lastSeenMs = nowMs;
        }
        entry.lastSuccessfulAuthMs = nowMs;
    }

    synchronized int getFailureCount(InetAddress address) {
        if (address == null) {
            return 0;
        }
        Entry entry = entries.get(address.getHostAddress());
        return entry != null ? entry.failures : 0;
    }

    synchronized int getEntryCount() {
        return entries.size();
    }

    synchronized void clear() {
        entries.clear();
        globalFailureTimestamps.clear();
    }

    private void cleanGlobalFailuresLocked(long nowMs) {
        while (!globalFailureTimestamps.isEmpty()
                && (nowMs - globalFailureTimestamps.peekFirst()) >= GLOBAL_WINDOW_MS) {
            globalFailureTimestamps.pollFirst();
        }
    }

    private void pruneExpiredEntriesLocked(long nowMs) {
        if (entries.size() < MAX_ENTRIES / 2) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            boolean isBlocked = entry.blockedUntilMs > nowMs;
            boolean isRecentAuth = entry.lastSuccessfulAuthMs > 0L
                    && (nowMs - entry.lastSuccessfulAuthMs) < RECENT_AUTH_WINDOW_MS;
            boolean isExpired = (nowMs - entry.lastSeenMs) >= ENTRY_EXPIRY_MS;
            if (!isBlocked && !isRecentAuth && isExpired) {
                iterator.remove();
            }
        }
    }
}
