package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class GboardShotgunKeyboardRuntime {
    private static final String TAG = "GboardShotgunRuntime";

    private static final Map<ClassLoader, ReflectionHandles> HANDLES_BY_CLASSLOADER =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Context applicationContext;

    private GboardShotgunKeyboardRuntime() {
    }

    public static final class ReflectionHandles {
        private final Field contextField;
        private final Field keyIdField;
        private final Field entryKeycodeField;
        private final Field entryPayloadField;

        public ReflectionHandles(ClassLoader classLoader) {
            Field ctxField = null;
            try {
                Class<?> dispatcherClass = Class.forName("pvf", false, classLoader);
                try {
                    ctxField = dispatcherClass.getDeclaredField("b");
                    ctxField.setAccessible(true);
                } catch (Throwable ignored) {
                }
                if (ctxField == null) {
                    for (Field field : dispatcherClass.getDeclaredFields()) {
                        if (Context.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            ctxField = field;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            contextField = ctxField;

            Field kIdField = null;
            try {
                Class<?> softKeyDefClass = Class.forName(
                        "com.google.android.libraries.inputmethod.metadata.SoftKeyDef", false, classLoader);
                kIdField = softKeyDefClass.getDeclaredField("d");
                kIdField.setAccessible(true);
            } catch (Throwable ignored) {
            }
            keyIdField = kIdField;

            Field codeField = null;
            Field payloadField = null;
            try {
                Class<?> actionEntryClass = Class.forName("pnu", false, classLoader);
                codeField = actionEntryClass.getDeclaredField("c");
                codeField.setAccessible(true);
                payloadField = actionEntryClass.getDeclaredField("e");
                payloadField.setAccessible(true);
            } catch (Throwable ignored) {
            }
            entryKeycodeField = codeField;
            entryPayloadField = payloadField;
        }

        public Context extractContext(Object dispatcher) {
            if (dispatcher != null && contextField != null) {
                try {
                    Object val = contextField.get(dispatcher);
                    if (val instanceof Context ctx) {
                        return ctx;
                    }
                } catch (Throwable ignored) {
                }
            }
            return resolveApplicationContext();
        }

        public int extractKeyId(Object softKeyDef) {
            if (softKeyDef == null || keyIdField == null) {
                return 0;
            }
            try {
                return keyIdField.getInt(softKeyDef);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        public int extractKeycode(Object actionData) {
            if (actionData == null || entryKeycodeField == null) {
                return 0;
            }
            try {
                return entryKeycodeField.getInt(actionData);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        public String extractPayload(Object actionData) {
            if (actionData == null || entryPayloadField == null) {
                return null;
            }
            try {
                Object val = entryPayloadField.get(actionData);
                return val instanceof CharSequence charSequence ? charSequence.toString() : (val != null ? val.toString() : null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static Context resolveApplicationContext() {
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

    public static boolean maybePlayShotgunSound(
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

            GboardShotgunKeyboardSettings.SettingsSnapshot settings =
                    GboardShotgunKeyboardSettings.snapshot(context);

            if (!settings.enabled || settings.volumeMultiplier <= 0f) {
                return false;
            }

            int keyId = handles != null ? handles.extractKeyId(softKeyDef) : 0;
            int keycode = handles != null ? handles.extractKeycode(actionData) : 0;
            String pressText = handles != null ? handles.extractPayload(actionData) : null;
            String keyName = null;
            if (context != null && keyId != 0) {
                try {
                    keyName = context.getResources().getResourceEntryName(keyId);
                } catch (Throwable ignored) {
                }
            }

            GboardShotgunKeyboardPolicy.SoundType soundType =
                    GboardShotgunKeyboardPolicy.evaluateSoundType(
                            keyId, keycode, pressText, keyName, settings);

            if (soundType == GboardShotgunKeyboardPolicy.SoundType.PUMP) {
                GboardShotgunAudioEngine.playPump(context, settings.volumeMultiplier);
            } else if (soundType == GboardShotgunKeyboardPolicy.SoundType.BLAST) {
                GboardShotgunAudioEngine.playBlast(context, settings.volumeMultiplier);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Error in maybePlayShotgunSound", throwable);
        }

        // Always return false so standard keyboard typing dispatch is never suppressed.
        return false;
    }
}
