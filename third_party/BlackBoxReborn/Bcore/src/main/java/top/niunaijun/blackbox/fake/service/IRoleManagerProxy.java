package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.app.role.BRIRoleManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Keeps virtual package role checks inside the virtual package namespace.
 * A virtual app shares Clony's real Android UID, so forwarding its package name
 * to RoleManager fails Android's UID/package ownership check.
 */
public class IRoleManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IRoleManagerProxy";
    private static final String ROLE_SERVICE = "role";

    public IRoleManagerProxy() {
        super(BRServiceManager.get().getService(ROLE_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRoleManagerStub.get().asInterface(BRServiceManager.get().getService(ROLE_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(ROLE_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("isRoleHeldAsUser")
    public static class IsRoleHeld extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            String roleName = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : "unknown";
            String packageName = args != null && args.length > 1 && args[1] instanceof String
                    ? (String) args[1] : BActivityThread.getAppPackageName();
            Slog.d(TAG, "virtual_role_query package=" + packageName + " role=" + roleName + " result=false");
            // Roles are assigned to installed host packages. The virtual package has no
            // independent Android identity or role assignments on the device.
            return false;
        }
    }
}
