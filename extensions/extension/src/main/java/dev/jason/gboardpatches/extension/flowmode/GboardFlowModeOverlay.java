package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import java.util.Random;

public final class GboardFlowModeOverlay extends View implements AutoCloseable {
    private static final int RAY_COUNT = 75;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Ray[] rays = new Ray[RAY_COUNT];
    private final View body;
    private final RectF clip = new RectF();
    private final int[] bodyLocation = new int[2], ownLocation = new int[2];
    private float centerX, centerY, bodyHeight, exclusion = .33f, opacity = 1f;
    private int color;
    private boolean closed;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (closed || !isAttachedToWindow()) return;
            advance(false);
            postDelayed(this, 100L);
        }
    };
    private final View.OnLayoutChangeListener layout = (v, l, t, r, b, ol, ot, or, ob) -> geometry();

    public GboardFlowModeOverlay(Context context, View body, int color,
            double transparency, double exclusion) {
        super(context);
        this.body = body;
        this.color = color;
        this.opacity = finiteUnit(transparency) * Float.intBitsToFloat(0x3fba83a8);
        this.exclusion = finiteUnit(exclusion);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < rays.length; i++) {
            rays[i] = new Ray();
        }
    }

    private static float finiteUnit(double value) {
        return Double.isFinite(value) ? (float) Math.max(0, Math.min(1, value)) : 0f;
    }

    public boolean isForBody(View candidate) {
        return !closed && body == candidate;
    }

    public void pulse() {
        if (!closed) advance(true);
    }

    private void advance(boolean fast) {
        geometry();
        for (Ray ray : rays) {
            ray.radius += ray.speed * (fast ? 2.5f : 1.5f);
            if (ray.radius > ray.edge(getWidth(), getHeight(), centerX, centerY)) reset(ray);
        }
        invalidate();
    }

    private void reset(Ray ray) {
        double angle = (float) (random.nextDouble() * 2 * Math.PI);
        ray.x = (float) Math.cos(angle);
        ray.y = (float) Math.sin(angle);
        ray.length = 150 + random.nextInt(100);
        ray.speed = (20 + random.nextInt(40)) / 5f;
        ray.alpha = 100 + random.nextInt(75);
        ray.width = 2 + random.nextInt(5);
        float brightness = .5f + random.nextFloat();
        ray.color = Color.rgb(Math.min(255, (int) (Color.red(color) * brightness)),
                Math.min(255, (int) (Color.green(color) * brightness)),
                Math.min(255, (int) (Color.blue(color) * brightness)));
        float start = bodyHeight * exclusion - ray.length;
        float end = ray.edge(getWidth(), getHeight(), centerX, centerY);
        ray.radius = end > start ? start + random.nextFloat() * (end - start) : start;
    }

    private void geometry() {
        centerX = getWidth() * .5f;
        centerY = getHeight() * .35f;
        bodyHeight = getHeight();
        clip.set(0, 0, getWidth(), getHeight());
        if (body != null && body.getVisibility() == VISIBLE && isAttachedToWindow()) {
            body.getLocationOnScreen(bodyLocation);
            getLocationOnScreen(ownLocation);
            float left = bodyLocation[0] - ownLocation[0], top = bodyLocation[1] - ownLocation[1];
            clip.set(left, top, left + body.getWidth(), top + body.getHeight());
            centerX = left + body.getWidth() * .5f;
            centerY = top + body.getHeight() * .35f;
            bodyHeight = body.getHeight();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        closed = false;
        if (body != null) body.addOnLayoutChangeListener(layout);
        geometry();
        for (Ray ray : rays) reset(ray);
        post(frame);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        geometry();
        for (Ray ray : rays) reset(ray);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        geometry();
        int save = canvas.save();
        canvas.clipRect(clip);
        float minimum = bodyHeight * exclusion;
        for (Ray ray : rays) {
            if (ray.radius + ray.length < minimum) continue;
            float start = Math.max(ray.radius, minimum);
            float ramp = Math.max(0, Math.min(1, (ray.radius - minimum) / 100f));
            paint.setColor(ray.color);
            paint.setAlpha(Math.max(0, Math.min(255, (int) (ray.alpha * ramp * opacity))));
            paint.setStrokeWidth(ray.width);
            canvas.drawLine(centerX + ray.x * start, centerY + ray.y * start,
                    centerX + ray.x * (ray.radius + ray.length), centerY + ray.y * (ray.radius + ray.length), paint);
        }
        canvas.restoreToCount(save);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        removeCallbacks(frame);
        if (body != null) body.removeOnLayoutChangeListener(layout);
        animate().cancel();
        animate().alpha(0).setDuration(400).withEndAction(() -> {
            if (getParent() instanceof ViewGroup parent) parent.removeView(this);
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        closed = true;
        removeCallbacks(frame);
        if (body != null) body.removeOnLayoutChangeListener(layout);
        super.onDetachedFromWindow();
    }

    static final class Ray {
        float x, y, length, speed, radius, width;
        int alpha, color;

        float edge(int width, int height, float cx, float cy) {
            float horizontal = x > .001f ? (width - cx) / x : x < -.001f ? -cx / x : Float.MAX_VALUE;
            float vertical = y > .001f ? (height - cy) / y : y < -.001f ? -cy / y : Float.MAX_VALUE;
            return Math.min(horizontal, vertical);
        }
    }
}
