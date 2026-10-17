package dev.hyperosfix.desktop;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;

/**
 * system_server side, modern libxposed API.
 *
 * CachedAppOptimizer compacts (pages out) and freezes cached processes.  If the
 * HyperOS Runtime desktop is misclassified as cached, its pages are pushed out
 * and every touch afterwards faults them back in.
 *
 * Hooks are installed immediately; target resolution is deferred because the
 * PackageManager is not ready when onSystemServerStarting() fires.
 */
final class SystemHooks {

    /** Set by ModernEntry.onModuleLoaded(). */
    static volatile boolean sSystemServer;

    private static volatile boolean sInstalled;
    private static volatile boolean sInitStarted;
    private static volatile boolean sInitialized;

    private SystemHooks() {
    }

    static boolean isSystemServer() {
        return sSystemServer;
    }

    static void install(XposedModule module, ClassLoader cl) {
        if (sInstalled) {
            return;
        }
        sInstalled = true;

        L.i("system_server hook install: Android " + Build.VERSION.RELEASE
                + " / SDK " + Build.VERSION.SDK_INT + " / " + Build.DISPLAY);

        hookSystemReady(module, cl);
        hookCachedAppOptimizer(module, cl);
        hookAmsTrim(module, cl);
    }

    private static void ensureInitializedAsync() {
        if (sInitialized || sInitStarted) {
            return;
        }
        synchronized (SystemHooks.class) {
            if (sInitialized || sInitStarted) {
                return;
            }
            sInitStarted = true;
        }

        Thread thread = new Thread(() -> {
            try {
                Context ctx = systemContext();
                if (ctx == null) {
                    sInitStarted = false;
                    L.w("target init deferred: no system context yet");
                    return;
                }
                Targets.init(ctx);
                sInitialized = true;
                L.i("initialized; mode=" + (Targets.isEnforce()
                        ? "ENFORCE"
                        : "DRY-RUN (no " + Targets.ENFORCE_FILE + ")"));
            } catch (Throwable t) {
                sInitStarted = false;
                L.e("target init failed", t);
            }
        }, "hyperosfix-init");
        thread.setDaemon(true);
        thread.start();
    }

    private static Context systemContext() {
        try {
            Class<?> at = Reflect.findClass("android.app.ActivityThread", null);
            Object thread = Reflect.callStatic(at, "currentActivityThread");
            if (thread != null) {
                Object ctx = Reflect.call(thread, "getSystemContext");
                if (ctx instanceof Context) {
                    return (Context) ctx;
                }
            }
        } catch (Throwable t) {
            L.e("systemContext via ActivityThread", t);
        }

        try {
            Class<?> ag = Reflect.findClass("android.app.AppGlobals", null);
            Object app = Reflect.callStatic(ag, "getInitialApplication");
            if (app instanceof Context) {
                return (Context) app;
            }
        } catch (Throwable t) {
            L.e("systemContext via AppGlobals", t);
        }
        return null;
    }

    private static void hookSystemReady(XposedModule module, ClassLoader cl) {
        try {
            Class<?> ams = Reflect.findClass(
                    "com.android.server.am.ActivityManagerService", cl);
            int count = 0;
            for (Method m : ams.getDeclaredMethods()) {
                if (!"systemReady".equals(m.getName())) {
                    continue;
                }
                module.hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    ensureInitializedAsync();
                    return result;
                });
                count++;
                L.i("hooked AMS#systemReady" + signature(m));
            }
            L.i("AMS#systemReady hooks: " + count);
        } catch (Throwable t) {
            L.w("AMS#systemReady hook unavailable: " + t);
        }
    }

    private static void hookCachedAppOptimizer(XposedModule module, ClassLoader cl) {
        final Class<?> optimizer;
        try {
            optimizer = Reflect.findClass("com.android.server.am.CachedAppOptimizer", cl);
        } catch (Throwable t) {
            L.e("CachedAppOptimizer not found", t);
            return;
        }

        StringBuilder declared = new StringBuilder();
        int installed = 0;

        for (Method m : optimizer.getDeclaredMethods()) {
            if (m.isSynthetic()
                    || Modifier.isAbstract(m.getModifiers())
                    || Modifier.isNative(m.getModifiers())) {
                continue;
            }
            declared.append(m.getName()).append(' ');

            String lower = m.getName().toLowerCase(Locale.ROOT);
            boolean compaction = lower.contains("compact");
            // Do not hook freezeProcessNoLock: the outer freezeProcess owns the
            // bookkeeping and skipping only the inner call would desync AMS.
            boolean freezing = lower.startsWith("freeze")
                    && !lower.contains("unfreeze")
                    && !lower.contains("nolock")
                    && !lower.contains("nofreeze");
            if (!compaction && !freezing) {
                continue;
            }

            try {
                module.hook(m).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    String target;
                    try {
                        target = Targets.findTargetInArgs(args);
                    } catch (Throwable t) {
                        target = null;
                    }
                    if (target != null) {
                        String what = chain.getExecutable().getName() + "(" + target + ")";
                        if (Targets.isEnforce()) {
                            L.throttled("BLOCKED " + what);
                            return defaultValue(m.getReturnType());
                        }
                        L.throttled("would block " + what);
                    }
                    return chain.proceed();
                });
                installed++;
                L.i("hooked CachedAppOptimizer#" + m.getName() + signature(m));
            } catch (Throwable t) {
                L.e("hook " + m.getName(), t);
            }
        }

        L.i("CachedAppOptimizer declared methods: " + declared);
        L.i("CachedAppOptimizer hooks installed: " + installed);
        if (installed == 0) {
            L.w("no compaction/freezing methods matched; class layout may differ");
        }
    }

    private static void hookAmsTrim(XposedModule module, ClassLoader cl) {
        try {
            Class<?> ams = Reflect.findClass(
                    "com.android.server.am.ActivityManagerService", cl);
            int count = 0;
            for (Method m : ams.getDeclaredMethods()) {
                if (!"setProcessMemoryTrimLevel".equals(m.getName())) {
                    continue;
                }
                module.hook(m).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0 && args[0] instanceof String
                            && Targets.isTargetProcess((String) args[0])) {
                        String processName = (String) args[0];
                        if (Targets.isEnforce()) {
                            L.throttled("BLOCKED setProcessMemoryTrimLevel(" + processName + ")");
                            return defaultValue(m.getReturnType());
                        }
                        L.throttled("would block setProcessMemoryTrimLevel(" + processName + ")");
                    }
                    return chain.proceed();
                });
                count++;
            }
            L.i("AMS#setProcessMemoryTrimLevel hooks: " + count);
        } catch (Throwable t) {
            L.e("AMS trim hook", t);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || type == Void.class) {
            return null;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        return null;
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder("(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getSimpleName());
        }
        return sb.append(')').toString();
    }
}
