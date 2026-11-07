package dev.jason.gboardpatches.extension.filetransfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IncomingOfferRegistry {
    public static final long EXPIRY_MS = 120_000L;
    public static final int MAX_OFFERS = 200;
    private static final IncomingOfferRegistry INSTANCE = new IncomingOfferRegistry();

    public enum OfferState {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    public static final class FileInfo {
        public final String name;
        public final long size;

        public FileInfo(String name, long size) {
            this.name = name != null ? name : "file";
            this.size = Math.max(0L, size);
        }
    }

    public static final class IncomingOffer {
        public final String batchId;
        public final List<FileInfo> files;
        public final long totalSize;
        public final long createdAtMs;
        private volatile OfferState state;

        public IncomingOffer(String batchId, List<FileInfo> files, long totalSize, long createdAtMs, OfferState initialState) {
            this.batchId = batchId != null ? batchId : "";
            this.files = files != null ? Collections.unmodifiableList(new ArrayList<>(files)) : Collections.emptyList();
            this.totalSize = totalSize;
            this.createdAtMs = createdAtMs;
            this.state = initialState != null ? initialState : OfferState.PENDING;
        }

        public OfferState getState(long nowMs) {
            if (state == OfferState.PENDING && (nowMs - createdAtMs > EXPIRY_MS)) {
                state = OfferState.EXPIRED;
            }
            return state;
        }

        public void setState(OfferState newState) {
            this.state = newState;
        }
    }

    private final Map<String, IncomingOffer> offers = new ConcurrentHashMap<>();

    public static IncomingOfferRegistry getInstance() {
        return INSTANCE;
    }

    public IncomingOfferRegistry() {
    }

    public void register(String batchId, List<FileInfo> files, long totalSize, boolean autoAccept) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        if (offers.size() >= MAX_OFFERS) {
            cleanExpiredOffers(System.currentTimeMillis());
            if (offers.size() >= MAX_OFFERS) {
                Iterator<String> it = offers.keySet().iterator();
                if (it.hasNext()) {
                    offers.remove(it.next());
                }
            }
        }
        OfferState initial = autoAccept ? OfferState.ACCEPTED : OfferState.PENDING;
        offers.put(batchId, new IncomingOffer(batchId, files, totalSize, System.currentTimeMillis(), initial));
    }

    public IncomingOffer get(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return null;
        }
        IncomingOffer offer = offers.get(batchId);
        if (offer != null) {
            offer.getState(System.currentTimeMillis());
        }
        return offer;
    }

    public OfferState getState(String batchId) {
        IncomingOffer offer = get(batchId);
        if (offer == null) {
            return null;
        }
        return offer.getState(System.currentTimeMillis());
    }

    public boolean isAccepted(String batchId) {
        OfferState state = getState(batchId);
        return state == OfferState.ACCEPTED;
    }

    public boolean accept(String batchId) {
        IncomingOffer offer = get(batchId);
        if (offer == null) {
            return false;
        }
        OfferState state = offer.getState(System.currentTimeMillis());
        if (state == OfferState.PENDING) {
            offer.setState(OfferState.ACCEPTED);
            return true;
        }
        return state == OfferState.ACCEPTED;
    }

    public boolean decline(String batchId) {
        IncomingOffer offer = get(batchId);
        if (offer == null) {
            return false;
        }
        OfferState state = offer.getState(System.currentTimeMillis());
        if (state == OfferState.PENDING) {
            offer.setState(OfferState.DECLINED);
            return true;
        }
        return state == OfferState.DECLINED;
    }

    public void cleanExpiredOffers(long nowMs) {
        Iterator<Map.Entry<String, IncomingOffer>> it = offers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IncomingOffer> entry = it.next();
            IncomingOffer offer = entry.getValue();
            OfferState state = offer.getState(nowMs);
            if (state == OfferState.EXPIRED && (nowMs - offer.createdAtMs > 10 * 60 * 1000L)) {
                it.remove();
            } else if ((state == OfferState.ACCEPTED || state == OfferState.DECLINED) && (nowMs - offer.createdAtMs > 10 * 60 * 1000L)) {
                it.remove();
            }
        }
    }

    public int size() {
        return offers.size();
    }

    public void clear() {
        offers.clear();
    }
}

