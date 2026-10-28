package dev.jason.gboardpatches.extension.swipedismiss;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSwipeDismissGestureTest {
    @Test
    public void onlyVisibleHeaderStartCanDismiss() {
        Assert.assertTrue(GboardSwipeDismissGesture.isHeaderStart(
                450f, 1685f, 0, 1621, 1220, 1753));
        Assert.assertFalse(GboardSwipeDismissGesture.isHeaderStart(
                965f, 2530f, 0, 1621, 1220, 1753));
        Assert.assertFalse(GboardSwipeDismissGesture.isHeaderStart(
                450f, 1620f, 0, 1621, 1220, 1753));
    }

    @Test
    public void deleteKeyStartCannotDismiss() {
        GboardSwipeDismissGesture gesture = new GboardSwipeDismissGesture();
        gesture.begin(false, 0, 300f, 300f);

        Assert.assertFalse(gesture.move(0, 300f, 500f, 1f, 8f));
        Assert.assertFalse(gesture.isTracking());
        Assert.assertFalse(gesture.isConsumed());
    }

    @Test
    public void headerSwipeDismissesOncePastDistance() {
        GboardSwipeDismissGesture gesture = new GboardSwipeDismissGesture();
        gesture.begin(true, 0, 120f, 20f);

        Assert.assertFalse(gesture.move(0, 125f, 62f, 1f, 8f));
        Assert.assertTrue(gesture.move(0, 126f, 69f, 1f, 8f));
        Assert.assertTrue(gesture.isConsumed());
        Assert.assertFalse(gesture.move(0, 126f, 90f, 1f, 8f));
    }

    @Test
    public void horizontalCandidateScrollCannotLaterDismiss() {
        GboardSwipeDismissGesture gesture = new GboardSwipeDismissGesture();
        gesture.begin(true, 0, 100f, 20f);

        Assert.assertFalse(gesture.move(0, 140f, 24f, 1f, 8f));
        Assert.assertFalse(gesture.move(0, 140f, 100f, 1f, 8f));
        Assert.assertFalse(gesture.isConsumed());
    }

    @Test
    public void onlyOriginalPointerCanDismissAndResetEndsSession() {
        GboardSwipeDismissGesture gesture = new GboardSwipeDismissGesture();
        gesture.begin(true, 3, 100f, 20f);

        Assert.assertFalse(gesture.move(4, 100f, 100f, 1f, 8f));
        gesture.reset();
        Assert.assertFalse(gesture.move(3, 100f, 100f, 1f, 8f));
        Assert.assertFalse(gesture.isTracking());
    }

    @Test
    public void diagonalGestureNeedsPredominantlyDownwardMotion() {
        GboardSwipeDismissGesture gesture = new GboardSwipeDismissGesture();
        gesture.begin(true, 0, 100f, 20f);

        Assert.assertFalse(gesture.move(0, 150f, 72f, 1f, 8f));
        Assert.assertFalse(gesture.isConsumed());
    }
}
