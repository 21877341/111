package dev.hyperosfix.desktop;

import android.os.SystemClock;
import android.util.Log;

/**
 * Logs to logcat. libxposed modules can also use XposedModule.log(), but a
 * static logger keeps the hook classes free of a module reference.
 */
final class L {

    static final String TAG = "HyperOSDesktopFix";
    private static final long THROTTLE_MS = 5_000L;

    private static long sLastThrottled;

    private L() {
    }

    static void i(String msg) {
        Log.i(TAG, msg);
    }

    static void w(String msg) {
        Log.w(TAG, msg);
    }

    static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }

    static void throttled(String msg) {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastThrottled < THROTTLE_MS) {
            return;
        }
        sLastThrottled = now;
        Log.i(TAG, msg);
    }
}
