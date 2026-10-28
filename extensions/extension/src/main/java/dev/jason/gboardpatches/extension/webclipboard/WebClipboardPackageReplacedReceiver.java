package dev.jason.gboardpatches.extension.webclipboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class WebClipboardPackageReplacedReceiver extends BroadcastReceiver {
    private static final String TAG = "GboardWebClipboard";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (context == null || intent == null) {
                return;
            }
            String action = intent.getAction();
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                return;
            }
            WebClipboardTileController.reconcileRuntime(context);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to reconcile Web Clipboard after package replace or boot", throwable);
        }
    }
}

