package dev.jason.gboardpatches.extension.filetransfer;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class FileOfferRegistry {
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 10 * 60 * 1000L;
    public static final int MAX_OFFERS_PER_BATCH = 200;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final FileOfferRegistry INSTANCE = new FileOfferRegistry();

    private final ConcurrentHashMap<String, FileOffer> offers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> batchTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> batchLastActivityMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IncomingOfferRegistry.OfferState> batchConsent = new ConcurrentHashMap<>();
    private final Set<String> completedTokens = ConcurrentHashMap.newKeySet();
    private final long idleTimeoutMs;
    private final ScheduledExecutorService sweepExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FileOfferRegistrySweep");
        t.setDaemon(true);
        return t;
    });

    public FileOfferRegistry() {
        this(DEFAULT_IDLE_TIMEOUT_MS);
    }

    public FileOfferRegistry(long idleTimeoutMs) {
        this.idleTimeoutMs = Math.max(1000L, idleTimeoutMs);
        long sweepIntervalMs = Math.min(30_000L, this.idleTimeoutMs);
        sweepExecutor.scheduleWithFixedDelay(
                () -> cleanExpiredOffers(System.currentTimeMillis()),
                sweepIntervalMs,
                sweepIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public static FileOfferRegistry getInstance() {
        return INSTANCE;
    }

    public static String generateToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        char[] hexChars = new char[32];
        for (int i = 0; i < 16; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void register(FileOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("Offer cannot be null");
        }
        String token = offer.getToken();
        if (token == null || token.isBlank()) {
            offer.close();
            throw new IllegalArgumentException("Token cannot be blank");
        }
        String batchId = offer.getBatchId();
        Set<String> tokens = batchTokens.computeIfAbsent(
                batchId,
                k -> ConcurrentHashMap.newKeySet());
        if (tokens.size() >= MAX_OFFERS_PER_BATCH) {
            offer.close();
            throw new IllegalStateException("Max offers per batch reached: " + MAX_OFFERS_PER_BATCH);
        }
        tokens.add(token);
        offers.put(token, offer);
        long now = System.currentTimeMillis();
        offer.touch(now);
        batchLastActivityMs.computeIfAbsent(batchId, k -> new AtomicLong(now)).set(now);
    }

    public FileOffer get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        cleanExpiredOffers(System.currentTimeMillis());
        return offers.get(token);
    }

    public void touch(String token) {
        touch(token, System.currentTimeMillis());
    }

    public void touch(String token, long nowMs) {
        if (token == null || token.isBlank()) {
            return;
        }
        FileOffer offer = offers.get(token);
        if (offer != null) {
            offer.touch(nowMs);
            AtomicLong batchActivity = batchLastActivityMs.get(offer.getBatchId());
            if (batchActivity != null) {
                batchActivity.set(nowMs);
            }
        }
    }

    public boolean isCompleted(String token) {
        return token != null && completedTokens.contains(token);
    }

    public boolean hasBatch(String batchId) {
        return batchId != null && (batchTokens.containsKey(batchId) || batchConsent.containsKey(batchId));
    }

    public void registerBatch(String batchId, boolean autoAccept) {
        if (batchId != null && !batchId.isBlank()) {
            batchConsent.put(batchId, autoAccept ? IncomingOfferRegistry.OfferState.ACCEPTED : IncomingOfferRegistry.OfferState.PENDING);
        }
    }

    public void acceptBatch(String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            batchConsent.put(batchId, IncomingOfferRegistry.OfferState.ACCEPTED);
        }
    }

    public void declineBatch(String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            batchConsent.put(batchId, IncomingOfferRegistry.OfferState.DECLINED);
            cancelBatch(batchId);
        }
    }

    public boolean isBatchAccepted(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return true;
        }
        IncomingOfferRegistry.OfferState state = batchConsent.get(batchId);
        return state == null || state == IncomingOfferRegistry.OfferState.ACCEPTED;
    }

    public void cancelBatch(String batchId) {
        if (batchId == null) {
            return;
        }
        batchConsent.remove(batchId);
        batchLastActivityMs.remove(batchId);
        Set<String> tokens = batchTokens.remove(batchId);
        if (tokens != null) {
            for (String token : tokens) {
                completedTokens.remove(token);
                FileOffer offer = offers.remove(token);
                if (offer != null) {
                    offer.close();
                }
            }
        }
    }

    public void cancelOffer(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        completedTokens.remove(token);
        FileOffer offer = offers.remove(token);
        if (offer != null) {
            offer.close();
            String batchId = offer.getBatchId();
            Set<String> tokens = batchTokens.get(batchId);
            if (tokens != null) {
                tokens.remove(token);
                if (tokens.isEmpty()) {
                    batchTokens.remove(batchId);
                    batchLastActivityMs.remove(batchId);
                }
            }
        }
    }

    public void completeOffer(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        completedTokens.add(token);
        FileOffer offer = offers.remove(token);
        if (offer != null) {
            offer.markCompleted();
            offer.close();
            String batchId = offer.getBatchId();
            Set<String> tokens = batchTokens.get(batchId);
            if (tokens != null) {
                tokens.remove(token);
                if (tokens.isEmpty()) {
                    batchTokens.remove(batchId);
                    batchLastActivityMs.remove(batchId);
                }
            }
        }
    }

    public void cleanExpiredOffers(long nowMs) {
        List<String> expiredBatches = new ArrayList<>();
        for (Map.Entry<String, AtomicLong> entry : batchLastActivityMs.entrySet()) {
            if (nowMs - entry.getValue().get() > idleTimeoutMs) {
                expiredBatches.add(entry.getKey());
            }
        }
        for (String batchId : expiredBatches) {
            cancelBatch(batchId);
        }
        Iterator<Map.Entry<String, FileOffer>> it = offers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FileOffer> entry = it.next();
            FileOffer offer = entry.getValue();
            if (offer.isClosed()) {
                it.remove();
            } else if (offer.getBatchId().isBlank() && (nowMs - offer.getLastActivityMs() > idleTimeoutMs)) {
                it.remove();
                offer.close();
            }
        }
    }

    public int size() {
        return offers.size();
    }

    public void clear() {
        completedTokens.clear();
        batchConsent.clear();
        batchLastActivityMs.clear();
        batchTokens.clear();
        for (FileOffer offer : offers.values()) {
            offer.close();
        }
        offers.clear();
    }
}
