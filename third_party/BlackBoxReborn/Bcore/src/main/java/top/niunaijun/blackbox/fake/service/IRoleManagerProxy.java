package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.Context;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import top.niunaijun.blackbox.utils.HostRuntimePermissions;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Adapts RoleManager queries to the real host identity used by virtual apps.
 * A virtual app shares Clony's Android UID, so Android rejects its virtual package name.
 */
public class IRoleManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IRoleManagerProxy";
    private static final String ROLE_SERVICE = "role";
    private static final String ROLE_INTERFACE = "android.app.role.IRoleManager";

    private Object mRoleManager;

    public IRoleManagerProxy() {
        super(BRServiceManager.get().getService(ROLE_SERVICE));
    }

    @Override
    protected Object getWho() {
        // Resolve the framework-owned RoleManager through the public service API, then read its
        // cached IRoleManager on the native worker path. Calling Stub.asInterface directly is
        // blocked by hidden-API enforcement on some Android 14 builds (including the crash device).
        Context context = BlackBoxCore.getContext();
        if (context == null) return null;

        mRoleManager = context.getSystemService(ROLE_SERVICE);
        if (mRoleManager == null) {
            Slog.w(TAG, "RoleManager service is unavailable");
            return null;
        }

        try {
            return FrameworkServiceCache.get(mRoleManager, ROLE_INTERFACE);
        } catch (Exception error) {
            Slog.e(TAG, "Could not read RoleManager's cached service", error);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(ROLE_SERVICE);
        // The RoleManager instance used to obtain the base service is already cached by this
        // Context. Replace its field as well as ServiceManager's binder cache so both this
        // context and subsequently created app contexts receive the virtual role handler.
        if (mRoleManager != null) {
            try {
                FrameworkServiceCache.set(mRoleManager, ROLE_INTERFACE, proxyInvocation);
                Slog.i(TAG, "RoleManager service proxy installed in framework cache");
            } catch (Exception error) {
                Slog.w(TAG, "Could not update the cached RoleManager instance", error);
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("isRoleHeldAsUser")
    public static class IsRoleHeld extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String roleName = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : "unknown";
            String virtualPackage = args != null && args.length > 1 && args[1] instanceof String
                    ? (String) args[1] : BActivityThread.getAppPackageName();
            // A virtual Chrome installation cannot be assigned Android's real browser role:
            // the OS only knows the host package and UID. Treat Chrome as the browser inside
            // its clone so its first-run flow does not offer an impossible system role promo.
            if ("android.app.role.BROWSER".equals(roleName)
                    && "com.android.chrome".equals(BActivityThread.getAppPackageName())
                    && "com.android.chrome".equals(virtualPackage)) {
                Slog.d(TAG, "virtual_role_query role=" + roleName
                        + " package=" + virtualPackage + " result=true reason=virtual_chrome");
                return true;
            }
            // Binder sees Clony's real UID, so Android rejects the virtual package name as
            // belonging to that UID. Forward the query as Clony and return the real role state;
            // a hard-coded false can leave Chrome's first-run decision logic on the wrong path.
            boolean rewritten = HostRuntimePermissions.rewritePackage(
                    args, BActivityThread.getAppPackageName(), BlackBoxCore.getHostPkg());
            String forwardedPackage = args != null && args.length > 1 && args[1] instanceof String
                    ? (String) args[1] : virtualPackage;
            try {
                Object result = method.invoke(who, args);
                Slog.d(TAG, "virtual_role_query role=" + roleName
                        + " package=" + virtualPackage
                        + " forwarded=" + forwardedPackage
                        + " rewritten=" + rewritten
                        + " result=" + result);
                return result;
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof SecurityException) {
                    Slog.w(TAG, "Role query rejected role=" + roleName
                            + " package=" + forwardedPackage, cause);
                    return false;
                }
                throw error;
            }
        }
    }
}
