package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GboardShotgunAudioEngine {
    private static final String TAG = "GboardShotgunAudio";
    private static final int MAX_STREAMS = 10;

    private static volatile SoundPool soundPool;
    private static volatile int blastSoundId = 0;
    private static volatile int pumpSoundId = 0;
    private static final AtomicBoolean BLAST_LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean PUMP_LOADED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();
    private static volatile Context cachedContext;

    private GboardShotgunAudioEngine() {
    }

    public static Context resolveContext(Context context) {
        if (context != null) {
            Context app = context.getApplicationContext();
            return app != null ? app : context;
        }
        if (cachedContext != null) {
            return cachedContext;
        }
        Context resolved = reflectedApplicationContext("android.app.ActivityThread", "currentApplication");
        if (resolved == null) {
            resolved = reflectedApplicationContext("android.app.AppGlobals", "getInitialApplication");
        }
        if (resolved != null) {
            cachedContext = resolved;
        }
        return resolved;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
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

    public static void init(Context context) {
        Context ctx = resolveContext(context);
        if (soundPool != null && BLAST_LOADED.get() && PUMP_LOADED.get()) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (soundPool == null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                soundPool = new SoundPool.Builder()
                        .setMaxStreams(MAX_STREAMS)
                        .setAudioAttributes(attributes)
                        .build();

                soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                    if (status == 0) {
                        if (sampleId == blastSoundId) {
                            BLAST_LOADED.set(true);
                        } else if (sampleId == pumpSoundId) {
                            PUMP_LOADED.set(true);
                        }
                    } else {
                        Log.w(TAG, "SoundPool sample load failed: id=" + sampleId + ", status=" + status);
                    }
                });
            }

            if (blastSoundId == 0) {
                blastSoundId = loadSound(ctx, "blast", GboardShotgunAudioPayload.getBlastAudioBytes());
            }
            if (pumpSoundId == 0) {
                pumpSoundId = loadSound(ctx, "pump", GboardShotgunAudioPayload.getPumpAudioBytes());
            }
        }
    }

    public static void playBlast(Context context, float volumeMultiplier) {
        playSound(context, true, volumeMultiplier);
    }

    public static void playPump(Context context, float volumeMultiplier) {
        playSound(context, false, volumeMultiplier);
    }

    private static void playSound(Context context, boolean isBlast, float volumeMultiplier) {
        if (volumeMultiplier <= 0f) {
            return;
        }
        try {
            init(context);
            if (soundPool == null) {
                return;
            }
            int soundId = isBlast ? blastSoundId : pumpSoundId;
            if (soundId == 0) {
                return;
            }
            float vol = Math.max(0.0f, Math.min(1.0f, volumeMultiplier));
            int streamId = soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
            if (streamId == 0) {
                soundPool.play(soundId, vol, vol, 2, 0, 1.0f);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to play sound: " + (isBlast ? "blast" : "pump"), throwable);
        }
    }

    private static int loadSound(Context context, String soundName, byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            return 0;
        }
        try {
            File cacheDir = context != null ? context.getCacheDir() : null;
            if (cacheDir == null) {
                cacheDir = new File("/data/local/tmp");
            }
            File audioDir = new File(cacheDir, "gboard_shotgun");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            File cacheFile = new File(audioDir, soundName + ".mp3");
            if (!cacheFile.exists() || cacheFile.length() != audioBytes.length) {
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(audioBytes);
                    fos.flush();
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return soundPool.load(cacheFile.getAbsolutePath(), 1);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to load audio resource: " + soundName, throwable);
        }

        return 0;
    }

    public static void release() {
        synchronized (INIT_LOCK) {
            if (soundPool != null) {
                try {
                    soundPool.release();
                } catch (Throwable ignored) {
                }
                soundPool = null;
                blastSoundId = 0;
                pumpSoundId = 0;
                BLAST_LOADED.set(false);
                PUMP_LOADED.set(false);
            }
        }
    }
}
