package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.UserManager;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardFlowModeRuntimeTest {
    @Test public void wrappedKeyboardContextStartsDetectorWhilePrivacyOffAndOfficialPreferenceStillGateIt()
            throws Exception {
        var controller=Robolectric.buildService(InputMethodService.class).create();
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()
                .getSystemService(UserManager.class)).setUserUnlocked(true);
        try {
            InputMethodService service=controller.get();
            Object extension=new Object(),event=new Object();
            var binding=new Access();
            binding.keyboard=new ContextWrapper(new ContextWrapper(service));
            try {
                GboardFlowModeRuntime.event(extension,event,binding,true);
                Assert.assertNotNull("The active themed keyboard context must reach the detector",
                        GboardFlowModeRuntime.stateToken(extension));
                binding.eligible=false;
                GboardFlowModeRuntime.event(extension,event,binding,true);
                Assert.assertNull(GboardFlowModeRuntime.stateToken(extension));
                binding.eligible=true;
                GboardFlowModeRuntime.event(extension,event,binding,false);
                Assert.assertNull(GboardFlowModeRuntime.stateToken(extension));
                binding.official=false;
                GboardFlowModeRuntime.event(extension,event,binding,true);
                Assert.assertNull(GboardFlowModeRuntime.stateToken(extension));
                binding.official=true;
                binding.keyboard=RuntimeEnvironment.getApplication();
                GboardFlowModeRuntime.event(extension,event,binding,true);
                Assert.assertNull("An application Context must not be treated as an IME",
                        GboardFlowModeRuntime.stateToken(extension));
            } finally {
                GboardFlowModeRuntime.reset(extension);
                binding.keyboard=null;
                binding.official=true;
            }
        } finally { controller.destroy(); }
    }
    @Test public void contextUnwrapRejectsNullApplicationAndCycles() {
        Assert.assertNull(GboardFlowModeRuntime.inputMethodService(null));
        Assert.assertNull(GboardFlowModeRuntime.inputMethodService(RuntimeEnvironment.getApplication()));
        MutableWrapper first=new MutableWrapper(),second=new MutableWrapper();
        first.base=second;second.base=first;
        Assert.assertNull(GboardFlowModeRuntime.inputMethodService(first));
        first.base=first;
        Assert.assertNull(GboardFlowModeRuntime.inputMethodService(first));
    }
    private static final class Access extends GboardFlowModeAccess {
        Context keyboard;boolean eligible=true,official=true;
        @Override public boolean isEligible(Object extension){return eligible;}
        @Override public Context keyboardContext(Object extension){return keyboard;}
        @Override public Context moduleContext(Object extension){return RuntimeEnvironment.getApplication();}
        @Override public boolean officialEnabled(Context context){return official;}
        @Override public Integer firstKey(Object event){return 11;}
        @Override public int color(Object extension){return 0xff0000ff;}
        @Override public long threshold(Context context){return GboardFlowModeDetector.FALLBACK_NANOS;}
        @Override public double transparency(){return .5;}
        @Override public double exclusion(){return .33;}
    }
    private static final class MutableWrapper extends ContextWrapper {
        Context base;MutableWrapper(){super(null);}
        @Override public Context getBaseContext(){return base;}
    }
}
