package dev.jason.gboardpatches.extension.swipedismiss;

public final class GboardSwipeDismissGesture {
    private static final float DISTANCE_DP = 48f;
    private static final float DIRECTION_RATIO = 1.2f;

    private int pointerId = -1;
    private float downX;
    private float downY;
    private boolean consumed;

    public static boolean isHeaderStart(float x, float y, int left, int top,
            int right, int bottom) {
        return right > left && bottom > top
                && x >= left && x < right && y >= top && y < bottom;
    }

    public void begin(boolean admitted, int id, float x, float y) {
        reset();
        if (admitted && id >= 0) {
            pointerId = id;
            downX = x;
            downY = y;
        }
    }

    public boolean move(int id, float x, float y, float density, float touchSlop) {
        if (id != pointerId || consumed) {
            return false;
        }
        float dx = Math.abs(x - downX);
        float dy = y - downY;
        if (dx > Math.max(touchSlop, 0f) && dx > dy) {
            reset();
            return false;
        }
        float safeDensity = density > 0f ? density : 1f;
        if (dy >= DISTANCE_DP * safeDensity && dy >= dx * DIRECTION_RATIO) {
            consumed = true;
            return true;
        }
        return false;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public boolean isTracking() {
        return pointerId >= 0;
    }

    public void reset() {
        pointerId = -1;
        consumed = false;
    }
}
