package dev.jason.gboardpatches.extension.flowmode;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.widget.FrameLayout;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class GboardFlowModeOverlayTest {
    @Test public void radialOverlayUsesBodyBoundsAndReleasesCallbacksOnDetach() throws Exception {
        try(var controller=Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity=controller.get();FrameLayout root=new FrameLayout(activity);
            FrameLayout body=new FrameLayout(activity);root.addView(body,new FrameLayout.LayoutParams(400,180));
            activity.setContentView(root);root.layout(0,0,600,600);body.layout(80,260,480,440);
            GboardFlowModeOverlay overlay=new GboardFlowModeOverlay(activity,body,
                    Color.BLUE,.5,.33);root.addView(overlay,new FrameLayout.LayoutParams(600,600));
            overlay.layout(0,0,600,600);
            Bitmap bitmap=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);
            overlay.draw(new Canvas(bitmap));
            Field clipField=GboardFlowModeOverlay.class.getDeclaredField("clip");clipField.setAccessible(true);
            RectF clip=(RectF)clipField.get(overlay);
            Assert.assertEquals(400f,clip.width(),0);Assert.assertEquals(180f,clip.height(),0);
            Assert.assertFalse(overlay.isClickable());Assert.assertFalse(overlay.isFocusable());
            Field rays=GboardFlowModeOverlay.class.getDeclaredField("rays");rays.setAccessible(true);
            Assert.assertEquals(75,((Object[])rays.get(overlay)).length);
            Assert.assertTrue(overlay.isForBody(body));root.removeView(overlay);
            Assert.assertFalse(overlay.isForBody(body));overlay.close();bitmap.recycle();
        }
    }
}
