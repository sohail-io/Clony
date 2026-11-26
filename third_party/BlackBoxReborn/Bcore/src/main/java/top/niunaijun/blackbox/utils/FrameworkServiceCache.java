package top.niunaijun.blackbox.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import top.niunaijun.blackbox.core.NativeCore;

/** Access an explicitly requested framework cache by its declared type. */
public final class FrameworkServiceCache {
    private static final String ROLE_MANAGER_INTERFACE = "android.app.role.IRoleManager";
    private static final String LOCALE_MANAGER_INTERFACE = "android.app.ILocaleManager";
    private static final String CONNECTIVITY_MANAGER_INTERFACE = "android.net.IConnectivityManager";

    private FrameworkServiceCache() { }

    public static Object get(Object manager, String interfaceName) throws ReflectiveOperationException {
        if (usesNativeCache(interfaceName)) {
            Object service = NativeCore.getFrameworkService(manager, interfaceName);
            if (service == null) throw new NoSuchFieldException(
                    "Framework service cache is unavailable for " + interfaceName);
            return service;
        }
        try {
            return field(manager, interfaceName).get(manager instanceof Class ? null : manager);
        } catch (NoSuchFieldException error) {
            Object service = NativeCore.getFrameworkService(manager, interfaceName);
            if (service == null) throw error;
            return service;
        }
    }

    public static void set(Object manager, String interfaceName, Object service)
            throws ReflectiveOperationException {
        if (usesNativeCache(interfaceName)) {
            if (!NativeCore.setFrameworkService(manager, interfaceName, service)) {
                throw new NoSuchFieldException(
                        "Framework service cache is unavailable for " + interfaceName);
            }
            return;
        }
        try {
            field(manager, interfaceName).set(manager instanceof Class ? null : manager, service);
        } catch (NoSuchFieldException error) {
            if (!NativeCore.setFrameworkService(manager, interfaceName, service)) throw error;
        }
    }

    private static boolean usesNativeCache(String interfaceName) {
        return ROLE_MANAGER_INTERFACE.equals(interfaceName)
                || LOCALE_MANAGER_INTERFACE.equals(interfaceName)
                || CONNECTIVITY_MANAGER_INTERFACE.equals(interfaceName);
    }

    private static Field field(Object manager, String interfaceName) throws NoSuchFieldException {
        if (manager == null) throw new NoSuchFieldException("Framework manager is unavailable");
        boolean staticField = manager instanceof Class;
        Class<?> owner = staticField ? (Class<?>) manager : manager.getClass();
        Field match = null;
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            // Android 17 filters some named field lookups. Enumerating and matching
            // the declared Binder type also avoids depending on private field names.
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) != staticField
                        || !interfaceName.equals(field.getType().getName())) continue;
                if (match != null) throw new NoSuchFieldException("Ambiguous cache for " + interfaceName);
                match = field;
            }
        }
        if (match == null) throw new NoSuchFieldException("No cache for " + interfaceName);
        match.setAccessible(true);
        return match;
    }
}
