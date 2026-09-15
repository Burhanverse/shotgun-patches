package dev.jason.gboardpatches.extension.webclipboard;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public final class WebClipboardMdnsAdvertiser {
    private static final String TAG = "WebClipboardMdns";
    private static final String SERVICE_TYPE = "_webclip._tcp.";
    private static final String PREF_KEY_INSTANCE_ID = "pref_web_clipboard_mdns_instance_id";
    private static final String TXT_VERSION_KEY = "v";
    private static final String TXT_VERSION_VALUE = "1";
    private static final String TXT_PAIRING_KEY = "code";
    private static final String TXT_LOOPBACK_KEY = "token";

    private final Context context;
    private final NsdManager nsdManager;
    private final String instanceName;
    private NsdServiceInfo serviceInfo;
    private boolean registered;

    private WebClipboardMdnsAdvertiser(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.nsdManager = this.context != null
                ? (NsdManager) this.context.getSystemService(Context.NSD_SERVICE)
                : null;
        this.instanceName = resolveInstanceName();
        this.registered = false;
    }

    public static WebClipboardMdnsAdvertiser create(Context context) {
        try {
            return new WebClipboardMdnsAdvertiser(context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveInstanceName() {
        return resolveInstanceName(null);
    }

    public static String resolveInstanceName(Context context) {
        try {
            if (context == null) {
                return "WebClip-" + System.currentTimeMillis();
            }
            java.util.UUID uuid = java.util.UUID.randomUUID();
            return "WebClip-" + uuid.toString().replace("-", "").substring(0, 8);
        } catch (Throwable ignored) {
            return "WebClip-default";
        }
    }

    public void register() {
        if (registered || nsdManager == null || serviceInfo != null) {
            return;
        }
        try {
            int port = WebClipboardPreferences.getPort(
                    WebClipboardPreferences.preferences(context));
            serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(instanceName);
            serviceInfo.setServiceType(SERVICE_TYPE);
            serviceInfo.setPort(port);
            serviceInfo.setAttribute("v", TXT_VERSION_VALUE);
            String pairingCode = WebClipboardPreferences.getPairingCode(
                    WebClipboardPreferences.preferences(context));
            serviceInfo.setAttribute("code", pairingCode);
            String loopbackToken = WebClipboardPreferences.getLoopbackIngressToken(
                    WebClipboardPreferences.preferences(context));
            serviceInfo.setAttribute("token", loopbackToken);
            nsdManager.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    new NsdManager.RegistrationListener() {
                        @Override
                        public void onRegistrationFailed(
                                NsdServiceInfo info, int errorCode) {
                            Log.w(TAG, "mDNS registration failed: "
                                    + info.getServiceName() + " code=" + errorCode);
                        }
                        @Override
                        public void onUnregistrationFailed(
                                NsdServiceInfo info, int errorCode) {
                            Log.w(TAG, "mDNS unregistration failed: "
                                    + info.getServiceName() + " code=" + errorCode);
                        }
                        @Override
                        public void onServiceRegistered(
                                NsdServiceInfo info) {
                            Log.i(TAG, "mDNS registered: " + info.getServiceName());
                        }
                        @Override
                        public void onServiceUnregistered(
                                NsdServiceInfo info) {
                            Log.i(TAG, "mDNS unregistered: " + info.getServiceName());
                        }
                    });
            registered = true;
        } catch (Throwable ignored) {
            Log.w(TAG, "mDNS registration skipped due to exception");
        }
    }

    public void unregister() {
        if (!registered || nsdManager == null || serviceInfo == null) {
            return;
        }
        try {
            nsdManager.unregisterService(
                    new NsdManager.RegistrationListener() {
                        @Override
                        public void onRegistrationFailed(
                                NsdServiceInfo info, int errorCode) {
                        }
                        @Override
                        public void onUnregistrationFailed(
                                NsdServiceInfo info, int errorCode) {
                        }
                        @Override
                        public void onServiceRegistered(
                                NsdServiceInfo info) {
                        }
                        @Override
                        public void onServiceUnregistered(
                                NsdServiceInfo info) {
                        }
                    });
            registered = false;
        } catch (Throwable ignored) {
            Log.w(TAG, "mDNS unregistration skipped due to exception");
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getServiceType() {
        return SERVICE_TYPE;
    }
}
