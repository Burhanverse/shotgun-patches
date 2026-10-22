package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardFlowModeRuntime {
    private static final int ENABLED_STATE_UNKNOWN = -1;
    private static final int ENABLED_STATE_FALSE = 0;
    private static final int ENABLED_STATE_TRUE = 1;

    private static volatile int enabledState = ENABLED_STATE_UNKNOWN;

    private GboardFlowModeRuntime() {
    }

    public static Object applyFlagValue(String flagName, Object stockResult) {
        try {
            return applyFlagValue(
                    flagName,
                    stockResult,
                    GboardFlagRuntimeContext.preferencesOrNull());
        } catch (Throwable ignored) {
            enabledState = ENABLED_STATE_FALSE;
            return stockResult;
        }
    }

    static Object applyFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        try {
            if (!GboardFlowModePolicy.isTargetFlagName(flagName)) {
                return stockResult;
            }
            if (preferences == null) {
                enabledState = ENABLED_STATE_FALSE;
                return stockResult;
            }
            boolean enabled = GboardFlowModeSettings.readEnabled(preferences);
            enabledState = enabled ? ENABLED_STATE_TRUE : ENABLED_STATE_FALSE;
            return GboardFlowModePolicy.maybeForceFlagValue(
                    flagName,
                    stockResult,
                    enabled);
        } catch (Throwable ignored) {
            enabledState = ENABLED_STATE_FALSE;
            return stockResult;
        }
    }

    public static int mapKeyCode(int keyCode) {
        try {
            return GboardFlowModePolicy.mapKeyCode(
                    keyCode,
                    enabledState == ENABLED_STATE_TRUE);
        } catch (Throwable ignored) {
            return keyCode;
        }
    }

    private static final Map<Object,State> STATES=new WeakHashMap<>();

    public static boolean isFlowingFor(Object extension) {
        synchronized(STATES) {
            State state=STATES.get(extension);return state!=null && state.detector.isFlowing();
        }
    }
    public static void event(Object extension,Object event,GboardFlowModeAccess binding,boolean enabled) {
        if(!enabled || !binding.isEligible(extension)) { reset(extension);return; }
        Context context=binding.keyboardContext(extension);
        InputMethodService service=inputMethodService(context);
        if(service==null) { reset(extension);return; }
        Context settingsContext=binding.moduleContext(extension);
        UserManager users=(UserManager)service.getSystemService(Context.USER_SERVICE);
        if(users==null || !users.isUserUnlocked()) { reset(extension);return; }
        if(!binding.officialEnabled(settingsContext)) { reset(extension);return; }
        Integer key=binding.firstKey(event);if(key==null) return;
        State state;
        synchronized(STATES) {
            state=STATES.get(extension);
            if(state==null) {
                if(!binding.officialEnabled(settingsContext)) return;
                state=new State();state.detector.configureThreshold(binding.threshold(settingsContext));
                state.transparency=binding.transparency();state.exclusion=binding.exclusion();
                state.color=binding.color(extension);STATES.put(extension,state);
            }
        }
        int code=GboardFlowModePolicy.mapKeyCode(key,true);
        boolean was=state.detector.isFlowing();
        if(GboardFlowModeDetector.isSessionReset(key)) {
            reset(extension);return;
        }
        boolean flowing=state.detector.onKey(code,SystemClock.elapsedRealtimeNanos());
        State current=state;
        if(!flowing && was) hide(current);
        if(flowing) new Handler(service.getMainLooper()).post(()->{
            try { show(service,current); } catch(Throwable unavailable) { current.detector.reset();hide(current); }
        });
    }
    static InputMethodService inputMethodService(Context context) {
        java.util.IdentityHashMap<Context,Boolean> visited=new java.util.IdentityHashMap<>();
        while(context!=null && visited.put(context,Boolean.TRUE)==null) {
            if(context instanceof InputMethodService service) return service;
            if(!(context instanceof ContextWrapper wrapper)) return null;
            context=wrapper.getBaseContext();
        }
        return null;
    }
    public static boolean isCurrent(Object extension, Object stateToken) {
        synchronized(STATES) { return STATES.get(extension)==stateToken; }
    }
    public static Object stateToken(Object extension) {
        synchronized(STATES) { return STATES.get(extension); }
    }
    public static void reset(Object extension) {
        State state;synchronized(STATES){state=STATES.remove(extension);}
        if(state!=null) { state.detector.reset();hide(state); }
    }
    private static void hide(State state) {
        GboardFlowModeOverlay overlay=state.overlay.get();
        state.overlay=new WeakReference<>(null);
        if(overlay!=null) overlay.post(()->{try { overlay.close(); } catch(Throwable ignored) {}});
    }
    private static void show(InputMethodService service,State state) {
        if(!state.detector.isFlowing() || service.getWindow()==null
                || service.getWindow().getWindow()==null) return;
        View decor=service.getWindow().getWindow().getDecorView();
        View body=findBody(decor);
        if(body==null || !body.isAttachedToWindow() || !service.isInputViewShown()) return;
        GboardFlowModeOverlay overlay=state.overlay.get();
        if(overlay==null || !overlay.isForBody(body)) {
            if(overlay!=null) overlay.close();
            overlay=new GboardFlowModeOverlay(service,body,state.color,state.transparency,state.exclusion);
            service.getWindow().addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
            overlay.setAlpha(0);overlay.animate().alpha(1).setDuration(500).start();
            state.overlay=new WeakReference<>(overlay);
        }
        overlay.pulse();
    }
    private static View findBody(View view) {
        if(view==null) return null;
        Object tag=view.getTag();
        if(tag instanceof String && (((String)tag).equals(".keyboard-body-area")
                || ((String)tag).startsWith(".keyboard-body-area.keyboard-outline-bottom"))) return view;
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {
            View found=findBody(group.getChildAt(i));if(found!=null) return found;
        }
        return null;
    }
    private static final class State {
        final GboardFlowModeDetector detector=new GboardFlowModeDetector();
        WeakReference<GboardFlowModeOverlay> overlay=new WeakReference<>(null);
        double transparency,exclusion;int color;
    }
}
