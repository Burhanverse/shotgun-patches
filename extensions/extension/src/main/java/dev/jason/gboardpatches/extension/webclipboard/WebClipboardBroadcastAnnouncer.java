package dev.jason.gboardpatches.extension.webclipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;

public final class WebClipboardBroadcastAnnouncer {
    private static final String TAG = "WebClipBcast";
    private static final long ANNOUNCE_INTERVAL_MS = 5_000L;
    private static final int UDP_VERSION = 1;
    private static final String FALLBACK_BROADCAST_ADDRESS = "255.255.255.255";
    private static final byte[] FALLBACK_BROADCAST_BYTES =
            {(byte) 255, (byte) 255, (byte) 255, (byte) 255};

    public static final int BROADCAST_PORT = 1717;

    private static final WebClipboardBroadcastAnnouncer SHARED =
            new WebClipboardBroadcastAnnouncer();

    private final Object lock = new Object();
    private final AtomicInteger startCount = new AtomicInteger();
    private Handler handler;
    private Context appContext;
    private boolean running;

    private final Runnable periodicAnnounce = new Runnable() {
        @Override
        public void run() {
            announceNow();
            synchronized (lock) {
                if (running) {
                    handler.postDelayed(this, ANNOUNCE_INTERVAL_MS);
                }
            }
        }
    };

    private WebClipboardBroadcastAnnouncer() {
    }

    public static WebClipboardBroadcastAnnouncer announcer() {
        return SHARED;
    }

    public void start(Context context) {
        Context safeContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (safeContext == null) {
            return;
        }
        boolean startLoop;
        synchronized (lock) {
            if (appContext == null) {
                appContext = safeContext;
            }
            startLoop = startCount.getAndIncrement() == 0;
            if (startLoop) {
                running = true;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                }
                handler.removeCallbacks(periodicAnnounce);
                handler.post(periodicAnnounce);
            }
        }
    }

    public void stop() {
        boolean shutdown;
        synchronized (lock) {
            int remaining = startCount.decrementAndGet();
            if (remaining < 0) {
                startCount.set(0);
            }
            shutdown = remaining <= 0;
            if (shutdown) {
                running = false;
                if (handler != null) {
                    handler.removeCallbacks(periodicAnnounce);
                }
                appContext = null;
            }
        }
    }

    public void announceNow() {
        Context context;
        synchronized (lock) {
            context = appContext;
        }
        if (context == null) {
            return;
        }
        try {
            AnnouncementConfig config = readConfig(context);
            String payload = buildPayloadJson(config);
            byte[] data = payload.getBytes("UTF-8");
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, resolveBroadcastAddress(context), BROADCAST_PORT);
                socket.send(packet);
                Log.i(TAG, "broadcast announced to " + packet.getAddress().getHostAddress()
                        + ":" + BROADCAST_PORT);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "broadcast skipped due to exception");
        }
    }

    static AnnouncementConfig readConfig(Context context) {
        SharedPreferences webPreferences = WebClipboardPreferences.preferences(context);
        String deviceId = WebClipboardPreferences.getMdnsInstanceId(webPreferences);
        int webclipPort = WebClipboardPreferences.getPort(webPreferences);
        String pairingCode = WebClipboardPreferences.getPairingCode(webPreferences);
        String loopbackIngressToken =
                WebClipboardPreferences.getLoopbackIngressToken(webPreferences);
        LanFtpPreferences.Snapshot lanFtpSnapshot =
                LanFtpPreferences.read(LanFtpPreferences.preferences(context));
        return new AnnouncementConfig(
                deviceId,
                webclipPort,
                pairingCode,
                loopbackIngressToken,
                lanFtpSnapshot.enabled,
                lanFtpSnapshot.controlPort);
    }

    static String buildPayloadJson(AnnouncementConfig config) {
        StringBuilder payload = new StringBuilder(256);
        payload.append('{');
        payload.append("\"deviceId\":").append(quote(config.deviceId));
        payload.append(',');
        payload.append("\"v\":").append(UDP_VERSION);
        payload.append(',');
        payload.append("\"webclip\":{");
        payload.append("\"port\":").append(config.webclipPort);
        payload.append(',');
        payload.append("\"code\":").append(quote(config.pairingCode));
        payload.append(',');
        payload.append("\"token\":").append(quote(config.loopbackIngressToken));
        payload.append('}');
        if (config.lanFtpEnabled) {
            payload.append(',');
            payload.append("\"lanftp\":{");
            payload.append("\"port\":").append(config.lanFtpPort);
            payload.append(',');
            payload.append("\"enabled\":true");
            payload.append('}');
        }
        payload.append('}');
        return payload.toString();
    }

    private static InetAddress resolveBroadcastAddress(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager != null) {
                LinkProperties properties = manager.getLinkProperties(manager.getActiveNetwork());
                if (properties != null) {
                    List<LinkAddress> addresses = properties.getLinkAddresses();
                    for (LinkAddress address : addresses) {
                        InetAddress ip = address.getAddress();
                        int prefix = address.getPrefixLength();
                        if (ip instanceof Inet4Address
                                && prefix >= 0
                                && prefix < 32
                                && !ip.isLoopbackAddress()
                                && !ip.isLinkLocalAddress()) {
                            return ipv4Broadcast((Inet4Address) ip, prefix);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the global broadcast address.
        }
        return globalBroadcastAddress();
    }

    private static InetAddress ipv4Broadcast(Inet4Address address, int prefix) {
        byte[] broadcast = address.getAddress().clone();
        for (int bit = prefix; bit < 32; bit++) {
            broadcast[bit / 8] |= (byte) (0x80 >>> (bit % 8));
        }
        try {
            return InetAddress.getByAddress(broadcast);
        } catch (Throwable ignored) {
            return globalBroadcastAddress();
        }
    }

    private static InetAddress globalBroadcastAddress() {
        try {
            return InetAddress.getByAddress(FALLBACK_BROADCAST_BYTES);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder((value.length() + 2) * 2);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (current <= 0x1F) {
                        escaped.append(String.format(Locale.US, "\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    public static final class AnnouncementConfig {
        public final String deviceId;
        public final int webclipPort;
        public final String pairingCode;
        public final String loopbackIngressToken;
        public final boolean lanFtpEnabled;
        public final int lanFtpPort;

        public AnnouncementConfig(String deviceId, int webclipPort, String pairingCode,
                String loopbackIngressToken, boolean lanFtpEnabled, int lanFtpPort) {
            this.deviceId = deviceId == null ? "" : deviceId;
            this.webclipPort = webclipPort;
            this.pairingCode = pairingCode == null ? "" : pairingCode;
            this.loopbackIngressToken =
                    loopbackIngressToken == null ? "" : loopbackIngressToken;
            this.lanFtpEnabled = lanFtpEnabled;
            this.lanFtpPort = lanFtpPort;
        }
    }
}