package dev.jason.gboardpatches.extension.zhuyinbottomrow;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardZhuyinBottomRowWeightReuseTest {
    private static final String[] NAMES = {"key_pos_switch_to_symbol", "A02",
            "key_pos_switch_to_next_language", "key_pos_space", "A06",
            "key_pos_del", "key_pos_ime_action"};
    private static final float[] STOCK = {150, 100, 500, 100, 400, 300, 100, 100, 150};
    private static final float[] CUSTOM = {110, 120, 450, 130, 320, 140, 180, 160, 170};

    @Test
    public void sameViewsRestoreAllSevenKeysAndBothClustersWhenDisabled() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.assertWeights(CUSTOM);
        f.bind(false);
        f.assertWeights(STOCK);
        f.bind(false);
        f.assertWeights(STOCK);
    }

    @Test
    public void repeatedEnabledBindsDoNotCaptureModuleWeightsAsStock() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.bind(true);
        f.bind(false);
        f.assertWeights(STOCK);
    }

    @Test
    public void foreignWeightUpdatesBecomeTheNextBaselineWithoutLosingOtherFields()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        LinearLayout.LayoutParams params = f.params(5);
        params.weight = 365;
        params.width = 77;
        params.leftMargin = 9;
        f.bind(true);
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = 365;
        f.assertWeights(expected);
        Assert.assertSame(params, f.views[5].getLayoutParams());
        Assert.assertEquals(77, params.width);
        Assert.assertEquals(9, params.leftMargin);
    }

    @Test
    public void replacementLayoutParamsAndChangedParentAreNeverRestoredFromOldBaseline()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        LinearLayout.LayoutParams replacement = new LinearLayout.LayoutParams(88, 44, 140);
        f.views[5].setLayoutParams(replacement);
        LinearLayout oldParent = (LinearLayout) f.views[7].getParent();
        oldParent.removeView(f.views[7]);
        LinearLayout foreignParent = new LinearLayout(f.context);
        foreignParent.addView(f.views[7]);
        f.bind(false);
        Assert.assertSame(replacement, f.views[5].getLayoutParams());
        Assert.assertEquals(140, replacement.weight, 0);
        Assert.assertEquals(160, f.params(7).weight, 0);
    }

    @Test
    public void reusedViewsWithNonZhuyinMetadataReleaseOnlyModuleWeights() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.makeNonZhuyin();
        f.params(1).weight = 231;
        f.bind(true);
        float[] expected = STOCK.clone();
        expected[1] = 231;
        f.assertWeights(expected);
    }

    @Test
    public void unknownUnownedFooterIsUntouchedEvenWithEnabledSettings() throws Throwable {
        Fixture f = new Fixture();
        f.makeNonZhuyin();
        f.params(0).weight = 432;
        f.bind(true);
        float[] expected = STOCK.clone();
        expected[0] = 432;
        f.assertWeights(expected);
    }

    @Test
    public void stockRebindCanPublishANewBaselineEqualToThePreviousModuleWeight()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        GboardZhuyinBottomRowWeightRuntime.beforeSoftKeyBound(f.keys[0]);
        f.assertWeights(STOCK);
        f.params(5).weight = CUSTOM[5];
        f.params(5).height = 81;
        f.bind(true);
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = CUSTOM[5];
        f.assertWeights(expected);
        Assert.assertEquals(81, f.params(5).height);
    }

    @Test
    public void disablingPreservesForeignWeightWithoutAnIntermediateEnabledApply()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.params(5).weight = 347;
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = 347;
        f.assertWeights(expected);
    }

    @Test
    public void cleanupDoesNotChangeAnotherKeyboardInstance() throws Throwable {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        first.bind(true);
        second.bind(false);
        first.assertWeights(CUSTOM);
        first.bind(false);
        first.assertWeights(STOCK);
    }

    private static final class Fixture {
        final Context context;
        final View[] keys = new View[7];
        final View[] views;
        final Map<View, Metadata> metadata = new IdentityHashMap<>();

        Fixture() {
            Context app = RuntimeEnvironment.getApplication();
            Resources base = app.getResources();
            Resources resources = new Resources(base.getAssets(), base.getDisplayMetrics(),
                    base.getConfiguration()) {
                @Override public String getResourceEntryName(int id) {
                    if (id >= 1 && id <= NAMES.length) return NAMES[id - 1];
                    return super.getResourceEntryName(id);
                }
            };
            context = new ContextWrapper(app) {
                @Override public Resources getResources() { return resources; }
            };
            SoftKeyboardView keyboard = new SoftKeyboardView(context);
            LinearLayout footer = new LinearLayout(context);
            LinearLayout middle = new LinearLayout(context);
            LinearLayout space = new LinearLayout(context);
            keyboard.addView(footer);
            for (int i = 0; i < keys.length; i++) {
                keys[i] = new View(context);
                keys[i].setId(i + 1);
                metadata.put(keys[i], new Metadata(i == 1 ? "，" : i == 3 ? "注音"
                        : i == 4 ? "ㄦ" : "", true));
            }
            footer.addView(keys[0]); footer.addView(keys[1]); footer.addView(middle);
            footer.addView(keys[5]); footer.addView(keys[6]);
            middle.addView(keys[2]); middle.addView(space);
            space.addView(keys[3]); space.addView(keys[4]);
            views = new View[] {keys[0], keys[1], middle, keys[2], space, keys[3], keys[4],
                    keys[5], keys[6]};
            for (int i = 0; i < views.length; i++) {
                views[i].setLayoutParams(new LinearLayout.LayoutParams(0, 40, STOCK[i]));
            }
        }

        void bind(boolean enabled) throws Throwable {
            Field cached = GboardZhuyinBottomRowWeightRuntime.class.getDeclaredField("cachedSettingsSnapshot");
            cached.setAccessible(true);
            GboardZhuyinBottomRowWeightRuntime.SettingsSnapshot settings =
                    new GboardZhuyinBottomRowWeightRuntime.SettingsSnapshot(
                    SystemClock.elapsedRealtime(), enabled, 110, 120, 130, 140, 180, 160, 170);
            cached.set(null, settings);
            if (!enabled || !metadata.get(keys[0]).zhuyin) {
                GboardZhuyinBottomRowWeightRuntime.beforeSoftKeyBound(keys[0]);
                return;
            }
            java.lang.reflect.Method resolve = GboardZhuyinBottomRowWeightRuntime.class
                    .getDeclaredMethod("findFooterCandidate", View.class);
            resolve.setAccessible(true);
            Object footer = resolve.invoke(null, keys[0]);
            java.lang.reflect.Method apply = GboardZhuyinBottomRowWeightRuntime.class
                    .getDeclaredMethod("applyWeights", footer.getClass(), settings.getClass());
            apply.setAccessible(true);
            apply.invoke(null, footer, settings);
        }

        void makeNonZhuyin() {
            for (View key : keys) metadata.put(key, new Metadata("english", false));
        }

        LinearLayout.LayoutParams params(int index) {
            return (LinearLayout.LayoutParams) views[index].getLayoutParams();
        }

        void assertWeights(float[] expected) {
            for (int i = 0; i < views.length; i++) {
                Assert.assertEquals("weight target " + i, expected[i], params(i).weight, 0);
            }
        }

    }

    private static final class SoftKeyboardView extends LinearLayout {
        SoftKeyboardView(Context context) { super(context); }
    }

    private static final class Metadata {
        final String label;
        final boolean zhuyin;
        Metadata(String label, boolean zhuyin) { this.label = label; this.zhuyin = zhuyin; }
    }
}
