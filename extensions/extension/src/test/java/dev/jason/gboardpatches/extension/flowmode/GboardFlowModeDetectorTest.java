package dev.jason.gboardpatches.extension.flowmode;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public final class GboardFlowModeDetectorTest {
    @Test
    public void requiresEightPairsAndStrictlyFasterAverage() {
        GboardFlowModeDetector detector = new GboardFlowModeDetector();
        long now = 1L;
        Assert.assertFalse(detector.onKey(-10043, now));
        for (int pair = 1; pair <= 8; pair++) {
            now += 100_000_000L;
            boolean result = detector.onKey(-10043, now);
            if (pair < 8) {
                Assert.assertFalse(result);
            } else {
                Assert.assertTrue(result);
            }
        }
        Assert.assertEquals(8, detector.pairCount());
        Assert.assertTrue(detector.isFlowing());
        detector.reset();
        Assert.assertEquals(0, detector.pairCount());
        Assert.assertFalse(detector.isFlowing());
    }

    @Test
    public void twoRegularSpacesEnableRelaxation() {
        GboardFlowModeDetector detector = new GboardFlowModeDetector();
        long now = 1L;
        detector.onKey(-10043, now);
        for (int i = 0; i < 3; i++) {
            now += 160_000_000L;
            detector.onKey(-10043, now);
        }
        now += 160_000_000L;
        detector.onKey(62, now);
        for (int i = 0; i < 3; i++) {
            now += 160_000_000L;
            detector.onKey(-10043, now);
        }
        now += 160_000_000L;
        detector.onKey(62, now);
        for (int i = 0; i < 4; i++) {
            now += 160_000_000L;
            detector.onKey(-10043, now);
        }
        Assert.assertTrue(detector.isFlowing());
    }

    @Test
    public void pauseExceedingSpaceNanosResetsBurstAndNeverPoisonsSubsequentTyping() {
        GboardFlowModeDetector detector = new GboardFlowModeDetector();
        long now = 1L;
        detector.onKey(-10043, now);
        now += 100_000_000L;
        detector.onKey(-10043, now);
        Assert.assertFalse(detector.isFlowing());

        now += 3_000_000_000L;
        detector.onKey(-10043, now);
        Assert.assertFalse(detector.isFlowing());

        for (int i = 0; i < 8; i++) {
            now += 100_000_000L;
            detector.onKey(-10043, now);
        }
        Assert.assertTrue(detector.isFlowing());
    }

    @Test
    public void slowTypingStopsFlowing() {
        GboardFlowModeDetector detector = new GboardFlowModeDetector();
        long now = 1L;
        detector.onKey(-10043, now);
        for (int i = 0; i < 8; i++) {
            now += 100_000_000L;
            detector.onKey(-10043, now);
        }
        Assert.assertTrue(detector.isFlowing());

        for (int i = 0; i < 8; i++) {
            now += 300_000_000L;
            detector.onKey(-10043, now);
        }
        Assert.assertFalse(detector.isFlowing());
    }

    @Test
    public void sessionResetStopsAnActiveFlow() {
        GboardFlowModeDetector detector = new GboardFlowModeDetector();
        long now = 1L;
        detector.onKey(-10043, now);
        for (int i = 0; i < 8; i++) {
            now += 100_000_000L;
            detector.onKey(-10043, now);
        }
        Assert.assertTrue(detector.isFlowing());
        detector.onKey(-10019, now + 10_000_000L);
        Assert.assertFalse(detector.isFlowing());
        Assert.assertEquals(0, detector.pairCount());
    }

    @Test
    public void histogramUsesSortedTypedBinsAndNeverMakesUpMissingStats() {
        Assert.assertEquals(115_000_000L, GboardFlowModeDetector.thresholdFromHistogram(Map.of(2, 10L), 30));
        Assert.assertEquals(150_000_000L, GboardFlowModeDetector.thresholdFromHistogram(Map.of(2, 10L), 100));
        Assert.assertEquals(100_000_000L, GboardFlowModeDetector.thresholdFromHistogram(Map.of(2, 10L), 0));
        for (Map<?, ?> bad : new Map[]{Map.of(), Map.of(2, 0L), Map.of(2, 10), Map.of("2", 10L), Map.of(2, -1L)}) {
            Assert.assertEquals(GboardFlowModeDetector.FALLBACK_NANOS, GboardFlowModeDetector.thresholdFromHistogram(bad, 30));
        }
        Assert.assertEquals(GboardFlowModeDetector.FALLBACK_NANOS, GboardFlowModeDetector.thresholdFromHistogram(Map.of(2, 10L), 101));
    }
}
