package dev.hyperosfix.desktop;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

/**
 * Modern libxposed entry point (API 101/102).
 *
 * The module is registered through META-INF/xposed/java_init.list; there is no
 * assets/xposed_init and no xposedmodule meta-data anymore.
 */
public final class ModernEntry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        SystemHooks.sSystemServer = param.isSystemServer();
        L.i("module loaded: process=" + param.getProcessName()
                + " systemServer=" + param.isSystemServer()
                + " api=" + getApiVersion());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        L.i("onSystemServerStarting");
        SystemHooks.install(this, param.getClassLoader());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (SystemHooks.isSystemServer()) {
            return;
        }
        AppHooks.install(this, param.getPackageName(), param.getDefaultClassLoader());
    }
}
