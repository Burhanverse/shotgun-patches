package dev.jason.gboardpatches.extension.shotgunkeyboard;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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

    private GboardShotgunAudioEngine() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        if (soundPool != null && BLAST_LOADED.get() && PUMP_LOADED.get()) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (soundPool == null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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

            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;

            if (blastSoundId == 0) {
                blastSoundId = loadSound(appContext, "gboard_shotgun_blast", "shotgun/blast.mp3");
            }
            if (pumpSoundId == 0) {
                pumpSoundId = loadSound(appContext, "gboard_shotgun_pump", "shotgun/pump.mp3");
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
            if (soundPool == null || (isBlast && !BLAST_LOADED.get()) || (!isBlast && !PUMP_LOADED.get())) {
                init(context);
            }
            if (soundPool == null) {
                return;
            }
            int soundId = isBlast ? blastSoundId : pumpSoundId;
            if (soundId == 0) {
                return;
            }
            float vol = Math.max(0.0f, Math.min(1.0f, volumeMultiplier));
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to play sound: " + (isBlast ? "blast" : "pump"), throwable);
        }
    }

    private static int loadSound(Context context, String soundName, String resourcePath) {
        try {
            File cacheFile = new File(context.getCacheDir(), soundName + ".mp3");
            if (!cacheFile.exists() || cacheFile.length() == 0) {
                InputStream is = GboardShotgunAudioEngine.class.getResourceAsStream("/" + resourcePath);
                if (is != null) {
                    try (is; FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return soundPool.load(cacheFile.getAbsolutePath(), 1);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to load audio resource: " + resourcePath, throwable);
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
