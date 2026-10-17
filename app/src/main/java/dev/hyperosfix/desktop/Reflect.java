package dev.hyperosfix.desktop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small reflection helper. The modern libxposed API does not ship XposedHelpers,
 * so the few accesses this module needs are implemented here.
 */
final class Reflect {

    private Reflect() {
    }

    static Class<?> findClass(String name, ClassLoader cl) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }

    static Object getObjectField(Object obj, String name) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        throw new NoSuchFieldError(name);
    }

    static Object callStatic(Class<?> clazz, String name) {
        try {
            Method m = clazz.getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static Object call(Object obj, String name) {
        try {
            Method m = obj.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
