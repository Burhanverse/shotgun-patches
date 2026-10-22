package dev.jason.gboardpatches.extension.flowmode;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;
import java.util.ArrayDeque;

public final class GboardFlowModeCallbacks {
    private static final GboardFlowModeAccess ACCESS=new GboardFlowModeAccess();
    private static final GboardFlowModeRequestBridge BRIDGE=new GboardFlowModeRequestBridge(ACCESS);
    private static final ThreadLocal<ArrayDeque<Boolean>> CONTINUATIONS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Boolean>> TASKS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Boolean>> NATIVES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Boolean>> SETTERS=ThreadLocal.withInitial(ArrayDeque::new);
    private GboardFlowModeCallbacks() {}
    private static boolean enabled() {
        try {
            android.content.SharedPreferences prefs=GboardFlagRuntimeContext.preferencesOrNull();
            return prefs!=null && GboardFlowModeSettings.readEnabled(prefs);
        } catch(Throwable unavailable) { return false; }
    }
    public static boolean beforeEvent(Object extension,Object event) {
        boolean enabled=enabled() && BRIDGE.isHealthy();
        try {
            GboardFlowModeRuntime.event(extension,event,ACCESS,enabled);
        } catch(Throwable failure) { reset(extension);enabled=false; }
        try { return BRIDGE.enterEvent(extension,event,enabled); }
        catch(Throwable failure) { reset(extension);return false; }
    }
    public static void afterEvent() { try { BRIDGE.exitEvent(); } catch(Throwable ignored) {} }
    public static void reset(Object extension) {
        try { GboardFlowModeRuntime.reset(extension); } catch(Throwable ignored) {}
    }
    public static void availability(Object extension,boolean available) { if(!available) reset(extension); }
    public static boolean moduleReset(Object module) {
        if(!enabled()) return false;
        try { return BRIDGE.resetModule(module); } catch(Throwable unavailable) { return false; }
    }
    public static void moduleRequest(Object module,Object request) {
        if(!enabled()) return;
        try { BRIDGE.tagRequest(module,request); } catch(Throwable ignored) {}
    }
    public static Object prefilter(Object continuation,Object status) {
        if(!enabled()) return null;
        try { return BRIDGE.bypassPreFilter(continuation,status); } catch(Throwable unavailable) { return null; }
    }
    public static void enterContinuation(Object continuation) {
        boolean active=enabled();CONTINUATIONS.get().push(active);
        if(active) try { BRIDGE.enterContinuation(continuation); } catch(Throwable ignored) {}
    }
    public static void exitContinuation() {
        if(pop(CONTINUATIONS)) try { BRIDGE.exitContinuation(); } catch(Throwable ignored) {}
    }
    public static void tagTask(Object task,Object processor) {
        if(!enabled()) return;
        try { BRIDGE.tagPostprocessTask(task,processor); } catch(Throwable ignored) {}
    }
    public static void enterPostprocess(Object task) {
        boolean active=enabled();TASKS.get().push(active);
        if(active) try { BRIDGE.enterPostprocess(task); } catch(Throwable ignored) {}
    }
    public static void exitPostprocess() {
        if(pop(TASKS)) try { BRIDGE.exitPostprocess(); } catch(Throwable ignored) {}
    }
    public static void enterNative(Object callback,long handle) {
        boolean active=enabled();NATIVES.get().push(active);
        if(active) try { BRIDGE.enterNativeResponse(callback,handle); } catch(Throwable ignored) {}
    }
    public static void exitNative() {
        if(pop(NATIVES)) try { BRIDGE.exitNativeResponse(); } catch(Throwable ignored) {}
    }
    public static void enterSetter(Object callback) {
        boolean active=enabled();SETTERS.get().push(active);
        if(active) try { BRIDGE.enterStockSetter(callback); } catch(Throwable ignored) {}
    }
    public static void exitSetter() {
        if(pop(SETTERS)) try { BRIDGE.exitStockSetter(); } catch(Throwable ignored) {}
    }
    public static boolean excludeOov(Object provider) {
        if(!enabled()) return false;
        try { return BRIDGE.excludeIntendedOov(provider); } catch(Throwable unavailable) { return false; }
    }
    public static boolean skipOov() {
        return enabled() && BRIDGE.skipIntendedOovLookup();
    }
    private static boolean pop(ThreadLocal<ArrayDeque<Boolean>> values) {
        ArrayDeque<Boolean> stack=values.get();Boolean active=stack.poll();
        if(stack.isEmpty()) values.remove();return Boolean.TRUE.equals(active);
    }
}
