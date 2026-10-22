package dev.jason.gboardpatches.extension.flowmode;
import org.junit.Assert;
import org.junit.Test;
import java.util.Map;

public final class GboardFlowModeDetectorTest {
    @Test public void requiresFifteenPairsAndStrictlyFasterAverage() {
        GboardFlowModeDetector detector=new GboardFlowModeDetector();
        long now=1;Assert.assertFalse(detector.onKey(-10043,now));
        for(int pair=1;pair<=15;pair++) {
            now+=100_000_000L;Assert.assertFalse(detector.onKey(-10043,now));
        }
        Assert.assertEquals(15,detector.pairCount());
        Assert.assertTrue(detector.onKey(-10043,now+1_000_000L));
        Assert.assertTrue(detector.isFlowing());
        detector.reset();Assert.assertEquals(0,detector.pairCount());Assert.assertFalse(detector.isFlowing());
    }
    @Test public void threeRegularSpacesEnableRelaxationAndOneLongGapDisablesIt() {
        Assert.assertTrue(typeWithSpaces(false));Assert.assertFalse(typeWithSpaces(true));
    }
    private static boolean typeWithSpaces(boolean longGap) {
        GboardFlowModeDetector detector=new GboardFlowModeDetector();long now=1;
        for(int word=0;word<4;word++) {
            detector.onKey(-10043,now+=120_000_000L);
            for(int pair=0;pair<5;pair++) detector.onKey(-10043,now+=120_000_000L);
            if(word<3) detector.onKey(62,now+=(longGap&&word==1?501_000_000L:120_000_000L));
        }
        return detector.isFlowing();
    }
    @Test public void controlsBreakPairsAndSessionResetStopsAnActiveFlow() {
        GboardFlowModeDetector detector=new GboardFlowModeDetector();
        detector.onKey(-10043,1);detector.onKey(67,2);detector.onKey(-10043,3);
        Assert.assertEquals(0,detector.pairCount());
        for(int i=1;i<=15;i++) detector.onKey(-10043,3+i*10_000_000L);
        Assert.assertTrue(detector.isFlowing());detector.onKey(-10019,200_000_000L);
        Assert.assertFalse(detector.isFlowing());Assert.assertEquals(0,detector.pairCount());
    }
    @Test public void histogramUsesSortedTypedBinsAndNeverMakesUpMissingStats() {
        Assert.assertEquals(115_000_000L,GboardFlowModeDetector.thresholdFromHistogram(Map.of(2,10L),30));
        Assert.assertEquals(150_000_000L,GboardFlowModeDetector.thresholdFromHistogram(Map.of(2,10L),100));
        Assert.assertEquals(100_000_000L,GboardFlowModeDetector.thresholdFromHistogram(Map.of(2,10L),0));
        for(Map<?,?> bad:new Map[]{Map.of(),Map.of(2,0L),Map.of(2,10),Map.of("2",10L),Map.of(2,-1L)})
            Assert.assertEquals(100_000_000L,GboardFlowModeDetector.thresholdFromHistogram(bad,30));
        Assert.assertEquals(100_000_000L,GboardFlowModeDetector.thresholdFromHistogram(Map.of(2,10L),101));
    }
}
