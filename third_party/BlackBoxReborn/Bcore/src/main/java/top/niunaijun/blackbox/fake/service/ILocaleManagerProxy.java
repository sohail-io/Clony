package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.LocaleList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import top.niunaijun.blackbox.utils.Slog;

/** Keeps system app-locale lookups from treating a virtual package as host-owned. */
public class ILocaleManagerProxy extends BinderInvocationStub {
    private static final String TAG = "ILocaleManagerProxy";
    private static final String LOCALE_INTERFACE = "android.app.ILocaleManager";
    private Object mLocaleManager;

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(Context.LOCALE_SERVICE));
    }

    @Override
    protected Object getWho() {
        Context context = BlackBoxCore.getContext();
        if (context == null) return null;

        mLocaleManager = context.getSystemService(Context.LOCALE_SERVICE);
        if (mLocaleManager == null) {
            Slog.w(TAG, "LocaleManager service is unavailable");
            return null;
        }

        try {
            return FrameworkServiceCache.get(mLocaleManager, LOCALE_INTERFACE);
        } catch (Exception error) {
            Slog.e(TAG, "Could not read LocaleManager's cached service", error);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LOCALE_SERVICE);
        if (mLocaleManager == null) return;
        try {
            FrameworkServiceCache.set(mLocaleManager, LOCALE_INTERFACE, proxyInvocation);
            Slog.i(TAG, "LocaleManager service proxy installed in framework cache");
        } catch (Exception error) {
            Slog.w(TAG, "Could not update the cached LocaleManager instance", error);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getApplicationLocales")
    public static class GetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String virtualPackage = BActivityThread.getAppPackageName();
            if (args != null && args.length > 0 && args[0] instanceof String
                    && virtualPackage != null && virtualPackage.equals(args[0])) {
                // A clone has no system-owned per-app locale record. Empty means use the
                // device locale and avoids querying Android with Clony's mismatched UID.
                Slog.d(TAG, "Returning system-default locale for virtual package="
                        + virtualPackage);
                return LocaleList.getEmptyLocaleList();
            }

            try {
                return method.invoke(who, args);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof SecurityException) {
                    // No cloned app is allowed to read another package's system-owned locale.
                    Slog.w(TAG, "App-specific locale query denied; returning system default", cause);
                    return LocaleList.getEmptyLocaleList();
                }
                throw error;
            }
        }
    }
}
