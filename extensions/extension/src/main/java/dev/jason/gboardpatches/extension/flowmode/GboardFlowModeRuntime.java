package dev.jason.gboardpatches.extension.flowmode;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardFlowModeRuntime {
    private static final String TAG = "GboardFlowModeRuntime";
    private static final int TYPING_PULSE_KEY_CODE = -10043;
    private static final long INACTIVITY_TIMEOUT_MS = 1500L;

    private static final Map<ClassLoader, ReflectionHandles> HANDLES_BY_CLASSLOADER =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, State> STATES = new WeakHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile Context applicationContext;

    private GboardFlowModeRuntime() {
    }

    private static final class ReflectionHandles {
        private volatile Field contextField;
        private volatile Field entryKeycodeField;
        private volatile Field entryPayloadField;

        ReflectionHandles(ClassLoader classLoader) {
            Field ctxField = null;
            for (String className : new String[]{"qil", "pvf"}) {
                try {
                    Class<?> dispatcherClass = Class.forName(className, false, classLoader);
                    for (Field field : dispatcherClass.getDeclaredFields()) {
                        if (Context.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            ctxField = field;
                            break;
                        }
                    }
                    if (ctxField != null) break;
                } catch (Throwable ignored) {
                }
            }
            contextField = ctxField;

            Field codeField = null;
            Field payloadField = null;
            for (String className : new String[]{"qbc", "pnu"}) {
                try {
                    Class<?> actionEntryClass = Class.forName(className, false, classLoader);
                    try {
                        Field c = actionEntryClass.getDeclaredField("c");
                        if (c.getType() == int.class) {
                            c.setAccessible(true);
                            codeField = c;
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Field e = actionEntryClass.getDeclaredField("e");
                        e.setAccessible(true);
                        payloadField = e;
                    } catch (Throwable ignored) {
                    }
                    if (codeField != null && payloadField != null) break;
                } catch (Throwable ignored) {
                }
            }
            entryKeycodeField = codeField;
            entryPayloadField = payloadField;
        }

        Context extractContext(Object dispatcher) {
            if (dispatcher != null) {
                if (contextField == null) {
                    for (Field field : dispatcher.getClass().getDeclaredFields()) {
                        if (Context.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            contextField = field;
                            break;
                        }
                    }
                }
                if (contextField != null) {
                    try {
                        Object obj = contextField.get(dispatcher);
                        if (obj instanceof Context ctx) {
                            return ctx;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return null;
        }

        int extractKeycode(Object actionData) {
            if (actionData == null) {
                return 0;
            }
            if (entryKeycodeField == null) {
                try {
                    Field c = actionData.getClass().getDeclaredField("c");
                    if (c.getType() == int.class) {
                        c.setAccessible(true);
                        entryKeycodeField = c;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (entryKeycodeField != null) {
                try {
                    return entryKeycodeField.getInt(actionData);
                } catch (Throwable ignored) {
                }
            }
            return 0;
        }

        String extractPayload(Object actionData) {
            if (actionData == null) {
                return null;
            }
            if (entryPayloadField == null) {
                try {
                    Field e = actionData.getClass().getDeclaredField("e");
                    e.setAccessible(true);
                    entryPayloadField = e;
                } catch (Throwable ignored) {
                }
            }
            if (entryPayloadField != null) {
                try {
                    Object val = entryPayloadField.get(actionData);
                    return val instanceof CharSequence cs ? cs.toString() : (val != null ? val.toString() : null);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }

    private static ReflectionHandles handles(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        synchronized (HANDLES_BY_CLASSLOADER) {
            ReflectionHandles existing = HANDLES_BY_CLASSLOADER.get(classLoader);
            if (existing != null) {
                return existing;
            }
            ReflectionHandles created = new ReflectionHandles(classLoader);
            HANDLES_BY_CLASSLOADER.put(classLoader, created);
            return created;
        }
    }

    private static Context resolveApplicationContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context reflected = reflectedContext("android.app.ActivityThread", "currentApplication");
        if (reflected == null) {
            reflected = reflectedContext("android.app.AppGlobals", "getInitialApplication");
        }
        if (reflected != null) {
            applicationContext = reflected;
        }
        return reflected;
    }

    private static Context reflectedContext(String className, String methodName) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object application = method.invoke(null);
            if (application instanceof Context ctx) {
                Context app = ctx.getApplicationContext();
                return app != null ? app : ctx;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean onGestureDispatch(
            Object gestureDispatcher,
            Object actionType,
            Object actionData,
            Object softKeyDef) {

        try {
            if (actionType == null) {
                return false;
            }
            String actionName = actionType.toString();
            if (!"PRESS".equals(actionName) && !"DOWN".equals(actionName) && !"TAP".equals(actionName)) {
                return false;
            }

            ClassLoader classLoader = gestureDispatcher != null
                    ? gestureDispatcher.getClass().getClassLoader()
                    : (softKeyDef != null ? softKeyDef.getClass().getClassLoader() : null);

            ReflectionHandles handles = classLoader != null ? handles(classLoader) : null;
            Context context = handles != null ? handles.extractContext(gestureDispatcher) : resolveApplicationContext();

            if (context == null || !GboardPatchesSettings.isUserUnlocked(context)) {
                return false;
            }

            if (!GboardFlowModeSettings.readEnabled(context)) {
                return false;
            }

            InputMethodService service = inputMethodService(context);
            if (service == null) {
                return false;
            }

            int rawKeycode = handles != null ? handles.extractKeycode(actionData) : 0;
            String text = handles != null ? handles.extractPayload(actionData) : null;

            int effectiveCode = mapKey(rawKeycode, text);

            Object keyToken = gestureDispatcher != null ? gestureDispatcher : service;
            State state;
            synchronized (STATES) {
                state = STATES.get(keyToken);
                if (state == null) {
                    state = new State();
                    state.color = resolveThemeColor(context);
                    state.transparency = 0.65;
                    state.exclusion = 0.33;
                    STATES.put(keyToken, state);
                }
            }

            final State currentState = state;
            boolean wasFlowing = state.detector.isFlowing();
            boolean flowing = state.detector.onKey(effectiveCode, SystemClock.elapsedRealtimeNanos());

            if (flowing) {
                MAIN_HANDLER.removeCallbacks(state.inactivityReset);
                MAIN_HANDLER.post(() -> {
                    try {
                        show(service, currentState);
                    } catch (Throwable t) {
                        currentState.detector.reset();
                        hide(currentState);
                    }
                });
                MAIN_HANDLER.postDelayed(state.inactivityReset, INACTIVITY_TIMEOUT_MS);
            } else if (wasFlowing) {
                hide(currentState);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Error in onGestureDispatch", throwable);
        }

        return false;
    }

    private static int mapKey(int keyCode, String text) {
        if (" ".equals(text)) {
            return 62;
        }
        if ("\n".equals(text)) {
            return 66;
        }
        if (keyCode > 0) {
            switch (keyCode) {
                case 55:
                case 56:
                case 59:
                case 60:
                case 62:
                case 66:
                case 67:
                    return keyCode;
                default:
                    return TYPING_PULSE_KEY_CODE;
            }
        }
        if (text != null && !text.isEmpty()) {
            return TYPING_PULSE_KEY_CODE;
        }
        return keyCode;
    }

    private static int resolveThemeColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
            try {
                return context.getColor(android.R.color.system_accent1_200);
            } catch (Throwable ignored) {
            }
        }
        return Color.parseColor("#4285F4");
    }

    private static InputMethodService inputMethodService(Context context) {
        java.util.IdentityHashMap<Context, Boolean> visited = new java.util.IdentityHashMap<>();
        while (context != null && visited.put(context, Boolean.TRUE) == null) {
            if (context instanceof InputMethodService service) {
                return service;
            }
            if (!(context instanceof ContextWrapper wrapper)) {
                return null;
            }
            context = wrapper.getBaseContext();
        }
        return null;
    }

    private static void hide(State state) {
        if (state == null) return;
        MAIN_HANDLER.removeCallbacks(state.inactivityReset);
        GboardFlowModeOverlay overlay = state.overlay.get();
        state.overlay = new WeakReference<>(null);
        if (overlay != null) {
            overlay.post(() -> {
                try {
                    overlay.close();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private static void show(InputMethodService service, State state) {
        if (!state.detector.isFlowing() || service.getWindow() == null
                || service.getWindow().getWindow() == null) {
            return;
        }
        View decor = service.getWindow().getWindow().getDecorView();
        View body = findBody(decor);
        if (body == null || !body.isAttachedToWindow() || !service.isInputViewShown()) {
            return;
        }
        GboardFlowModeOverlay overlay = state.overlay.get();
        if (overlay == null || !overlay.isForBody(body)) {
            if (overlay != null) {
                overlay.close();
            }
            overlay = new GboardFlowModeOverlay(service, body, state.color, state.transparency, state.exclusion);
            service.getWindow().addContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(350).start();
            state.overlay = new WeakReference<>(overlay);
        }
        overlay.pulse();
    }

    private static View findBody(View view) {
        if (view == null) return null;
        Object tag = view.getTag();
        if (tag instanceof String str && (str.equals(".keyboard-body-area")
                || str.startsWith(".keyboard-body-area.keyboard-outline-bottom"))) {
            return view;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBody(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class State {
        final GboardFlowModeDetector detector = new GboardFlowModeDetector();
        WeakReference<GboardFlowModeOverlay> overlay = new WeakReference<>(null);
        final Runnable inactivityReset = () -> {
            detector.reset();
            hide(this);
        };
        double transparency = 0.65;
        double exclusion = 0.33;
        int color = Color.parseColor("#4285F4");
    }
}
