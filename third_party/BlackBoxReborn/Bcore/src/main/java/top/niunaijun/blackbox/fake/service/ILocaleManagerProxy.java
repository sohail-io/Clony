package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.LocaleList;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import top.niunaijun.blackbox.utils.Slog;

/** Keeps a cloned app's own locale query inside its virtual package identity. */
public final class ILocaleManagerProxy extends BinderInvocationStub {
    private static final String TAG = "LocaleManagerStub";
    private static final String SERVICE_NAME = "locale";
    private static final String SERVICE_INTERFACE = "android.app.ILocaleManager";
    private static volatile Object sLocaleManagerInterface;

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        Object manager = BlackBoxCore.getContext().getSystemService(SERVICE_NAME);
        try {
            return FrameworkServiceCache.get(manager, SERVICE_INTERFACE);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot obtain locale manager service", error);
        }
    }

    @Override
    protected void inject(Object base, Object proxy) {
        sLocaleManagerInterface = proxy;
        replaceSystemService(SERVICE_NAME);
        installForContext(BlackBoxCore.getContext());
    }

    public static void installForContext(Context context) {
        Object service = sLocaleManagerInterface;
        if (context == null || service == null) return;
        Object manager = context.getSystemService(SERVICE_NAME);
        if (manager == null) return;
        try {
            FrameworkServiceCache.set(manager, SERVICE_INTERFACE, service);
        } catch (ReflectiveOperationException error) {
            Slog.w(TAG, "Could not install locale proxy for context package="
                    + context.getPackageName() + ": " + error.getClass().getSimpleName());
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
            String clonePackage = BActivityThread.getAppPackageName();
            if (clonePackage != null && args != null && args.length > 0
                    && clonePackage.equals(args[0])) {
                return LocaleList.getEmptyLocaleList();
            }
            return method.invoke(who, args);
        }
    }
}
