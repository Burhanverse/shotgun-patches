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
        private volatile Field contextField;
        private volatile Field keyIdField;
        private volatile Field entryKeycodeField;
        private volatile Field entryPayloadField;

        public ReflectionHandles(ClassLoader classLoader) {
            Field ctxField = null;
            // 18.5.4 uses qil, 18.0.3 uses pvf
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

            Field kIdField = null;
            try {
                Class<?> softKeyDefClass = Class.forName(
                        "com.google.android.libraries.inputmethod.metadata.SoftKeyDef", false, classLoader);
                // 18.5.4 uses "f", 18.0.3 uses "d"
                for (String fieldName : new String[]{"f", "d"}) {
                    try {
                        Field candidate = softKeyDefClass.getDeclaredField(fieldName);
                        if (candidate.getType() == int.class) {
                            candidate.setAccessible(true);
                            kIdField = candidate;
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            keyIdField = kIdField;

            Field codeField = null;
            Field payloadField = null;
            // 18.5.4 uses qbc, 18.0.3 uses pnu
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

        public Context extractContext(Object dispatcher) {
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
                        Object val = contextField.get(dispatcher);
                        if (val instanceof Context ctx) {
                            return ctx;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return resolveApplicationContext();
        }

        public int extractKeyId(Object softKeyDef) {
            if (softKeyDef == null) {
                return 0;
            }
            if (keyIdField == null) {
                for (Field field : softKeyDef.getClass().getDeclaredFields()) {
                    if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        keyIdField = field;
                        break;
                    }
                }
            }
            if (keyIdField != null) {
                try {
                    return keyIdField.getInt(softKeyDef);
                } catch (Throwable ignored) {
                }
            }
            return 0;
        }

        public int extractKeycode(Object actionData) {
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

        public String extractPayload(Object actionData) {
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
                    return val instanceof CharSequence charSequence ? charSequence.toString() : (val != null ? val.toString() : null);
                } catch (Throwable ignored) {
                }
            }
            return null;
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

            if (settings.muteOnHeadphones && GboardShotgunAudioEngine.isExternalAudioConnected(context)) {
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
