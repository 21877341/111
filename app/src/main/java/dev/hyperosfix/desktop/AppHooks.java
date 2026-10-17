package dev.hyperosfix.desktop;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Desktop-app side, modern libxposed API.
 *
 * Caps the trim level at TRIM_MEMORY_UI_HIDDEN so the desktop is not asked to
 * throw away its working set when it goes to the background.
 */
final class AppHooks {

    /** ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN */
    private static final int TRIM_MEMORY_UI_HIDDEN = 20;

    private static volatile boolean sInstalled;

    private AppHooks() {
    }

    static void install(XposedModule module, String pkg, ClassLoader cl) {
        if (sInstalled || pkg == null || cl == null) {
            return;
        }
        if ("dev.hyperosfix.desktop".equals(pkg)) {
            return;
        }
        sInstalled = true;
        L.i("app process: " + pkg);

        XposedInterface.Hooker clamp = chain -> {
            Object[] args = chain.getArgs().toArray();
            if (args.length > 0 && args[0] instanceof Integer) {
                int level = (Integer) args[0];
                if (level > TRIM_MEMORY_UI_HIDDEN) {
                    args[0] = TRIM_MEMORY_UI_HIDDEN;
                    L.throttled("clamped trim " + level + " -> "
                            + TRIM_MEMORY_UI_HIDDEN + " in " + pkg);
                    return chain.proceed(args);
                }
                if (level == TRIM_MEMORY_UI_HIDDEN) {
                    L.throttled("trim UI_HIDDEN in " + pkg);
                }
            }
            return chain.proceed();
        };

        hookAll(module, cl,
                "android.app.ActivityThread$ApplicationThread",
                "scheduleTrimMemory", clamp);
        hookAll(module, cl,
                "android.app.ActivityThread",
                "handleTrimMemory", clamp);
        hookAll(module, cl,
                "android.app.Application",
                "onTrimMemory", clamp);
    }

    private static void hookAll(XposedModule module, ClassLoader cl, String className,
                                String methodName, XposedInterface.Hooker hooker) {
        try {
            Class<?> c = Reflect.findClass(className, cl);
            int count = 0;
            for (Method m : c.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) {
                    continue;
                }
                module.hook(m).intercept(hooker);
                count++;
            }
            L.i(className + "#" + methodName + " hooks: " + count);
        } catch (Throwable t) {
            L.w(className + "#" + methodName + " unavailable: " + t);
        }
    }
}
