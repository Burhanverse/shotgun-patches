package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardFlowModeRuntimeTest {
    @Test
    public void selectsKeyboardBodyOverEmojiFastAccessBar() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);

        FrameLayout emojiBar = new FrameLayout(context);
        emojiBar.setTag(".keyboard-body-area");
        LinearLayout emojiDivider = new LinearLayout(context);
        emojiDivider.setTag(".divider.horizontal.for-fast-access-bar");
        emojiBar.addView(emojiDivider);
        emojiBar.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        );
        emojiBar.layout(0, 0, 1080, 120);

        FrameLayout keyboardBody = new FrameLayout(context);
        keyboardBody.setTag(".keyboard-body-area.keyboard-outline-bottom.clip-to-outline-for-old-android");
        keyboardBody.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        );
        keyboardBody.layout(0, 120, 1080, 920);

        root.addView(emojiBar);
        root.addView(keyboardBody);

        View selected = GboardFlowModeRuntime.findBody(root);
        Assert.assertSame(keyboardBody, selected);
    }

    @Test
    public void selectsTallerBodyWhenBothShareGenericTag() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);

        FrameLayout topStrip = new FrameLayout(context);
        topStrip.setTag(".keyboard-body-area");
        topStrip.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        );
        topStrip.layout(0, 0, 1080, 100);

        FrameLayout mainKeyboard = new FrameLayout(context);
        mainKeyboard.setTag(".keyboard-body-area");
        mainKeyboard.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(750, View.MeasureSpec.EXACTLY)
        );
        mainKeyboard.layout(0, 100, 1080, 850);

        root.addView(topStrip);
        root.addView(mainKeyboard);

        View selected = GboardFlowModeRuntime.findBody(root);
        Assert.assertSame(mainKeyboard, selected);
    }

    @Test
    public void ignoresAccessPointEducationTag() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);

        FrameLayout accessPointEducation = new FrameLayout(context);
        accessPointEducation.setTag(".access-point-education.keyboard-body-area");

        FrameLayout keyboardBody = new FrameLayout(context);
        keyboardBody.setTag(".keyboard-body-area.keyboard-outline-bottom.clip-to-outline-for-old-android");

        root.addView(accessPointEducation);
        root.addView(keyboardBody);

        View selected = GboardFlowModeRuntime.findBody(root);
        Assert.assertSame(keyboardBody, selected);
    }
}
