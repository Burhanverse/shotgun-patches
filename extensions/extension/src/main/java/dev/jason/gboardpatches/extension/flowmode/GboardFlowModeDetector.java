package dev.jason.gboardpatches.extension.flowmode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public final class GboardFlowModeDetector {
    public static final int MIN_PAIRS = 8, MIN_SPACES = 2;
    public static final long FALLBACK_NANOS = 150_000_000L, SPACE_NANOS = 700_000_000L;
    public static final double RELAXED_FACTOR = 1.5;

    private long last, total, lastSpace;
    private int pairs, spaces;
    private boolean regularSpaces = true, flowing;
    private long threshold = FALLBACK_NANOS;

    public synchronized void configureThreshold(long value) {
        threshold = Math.max(0L, value);
        reset();
    }

    public synchronized void reset() {
        last = 0;
        total = 0;
        lastSpace = 0;
        pairs = 0;
        spaces = 0;
        regularSpaces = true;
        flowing = false;
    }

    public synchronized boolean isFlowing() {
        return flowing;
    }

    public synchronized int pairCount() {
        return pairs;
    }

    public synchronized boolean onKey(int keyCode, long now) {
        if (isSessionReset(keyCode)) {
            reset();
            return false;
        }
        if (flowing) {
            last = now;
            return true;
        }
        if (isBoundary(keyCode)) {
            if (keyCode == 62) {
                if (last != 0 && now - last > SPACE_NANOS) {
                    regularSpaces = false;
                }
                spaces++;
                lastSpace = now;
            } else {
                lastSpace = 0;
            }
            last = 0;
            return false;
        }
        if (keyCode != -10043) {
            return false;
        }
        if (last != 0) {
            long interval = now - last;
            if (interval < 0) {
                reset();
                return false;
            }
            total += interval;
            pairs++;
            long effective = threshold;
            if (regularSpaces && spaces >= MIN_SPACES) {
                effective = BigDecimal.valueOf(threshold).multiply(new BigDecimal(RELAXED_FACTOR))
                        .setScale(0, RoundingMode.HALF_EVEN).longValue();
            }
            if (pairs >= MIN_PAIRS && total / pairs < effective) {
                flowing = true;
            }
        } else if (lastSpace != 0) {
            if (now - lastSpace > SPACE_NANOS) {
                regularSpaces = false;
            }
            lastSpace = 0;
        }
        last = now;
        return flowing;
    }

    public static boolean isBoundary(int code) {
        return code == -10002 || code == 62 || code == 55 || code == 56 || code == 59
                || code == 60 || code == 66 || code == 67;
    }

    public static boolean isSessionReset(int code) {
        return code == -10019 || code == 176 || code == 82 || code == -40000
                || code == -10030 || code == -10004 || code == -10058;
    }

    public static long thresholdFromHistogram(Map<?, ?> histogram, int percentile) {
        if (histogram == null || histogram.isEmpty() || percentile < 0 || percentile > 100) {
            return FALLBACK_NANOS;
        }
        long totalCount = 0;
        ArrayList<Integer> bins = new ArrayList<>();
        for (Map.Entry<?, ?> entry : histogram.entrySet()) {
            if (!(entry.getKey() instanceof Integer bin) || !(entry.getValue() instanceof Long count)
                    || bin < 0 || count < 0 || Long.MAX_VALUE - totalCount < count) {
                return FALLBACK_NANOS;
            }
            bins.add(bin);
            totalCount += count;
        }
        if (totalCount == 0) return FALLBACK_NANOS;
        Collections.sort(bins);
        double rank = (totalCount * (double) percentile) / 100.0;
        long accumulated = 0;
        for (int bin : bins) {
            long count = (Long) histogram.get(bin);
            if (count == 0) continue;
            long previous = accumulated;
            accumulated += count;
            if (accumulated >= rank) {
                double factor = bin + (rank - previous) / count;
                if (!Double.isFinite(factor)) return FALLBACK_NANOS;
                BigDecimal nanos = BigDecimal.valueOf(50_000_000L).multiply(new BigDecimal(factor));
                try {
                    return Math.max(0L, nanos.setScale(0, RoundingMode.HALF_EVEN).longValueExact());
                } catch (ArithmeticException invalid) {
                    return FALLBACK_NANOS;
                }
            }
        }
        return FALLBACK_NANOS;
    }
}
