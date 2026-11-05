package top.niunaijun.blackbox.fake.service;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.HostRuntimePermissions;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import java.lang.reflect.Method;


public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";
    private static final String SERVICE_INTERFACE = "android.permission.IPermissionManager";
    private Object frameworkPermissionManager;

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        // Let framework code create its own Binder interface. Calling the hidden
        // IPermissionManager.Stub.asInterface() directly is blocked on Android 17.
        try {
            frameworkPermissionManager = BlackBoxCore.getContext().getSystemService("permission");
            Object service = FrameworkServiceCache.get(frameworkPermissionManager, SERVICE_INTERFACE);
            Slog.i(TAG, "Permission manager interface acquired from framework cache: "
                    + service.getClass().getName());
            return service;
        } catch (Exception error) {
            Slog.e(TAG, "Framework permission manager interface is unavailable", error);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        // PermissionManager also retains an instance field; updating only the
        // ActivityThread static cache misses rationale and permission flag calls.
        try {
            FrameworkServiceCache.set(frameworkPermissionManager, SERVICE_INTERFACE, proxyInvocation);
            FrameworkServiceCache.set(Class.forName("android.app.ActivityThread"),
                    SERVICE_INTERFACE, proxyInvocation);
            Slog.i(TAG, "Permission manager proxy installed in framework caches");
        } catch (Exception error) {
            throw new IllegalStateException("Unable to update framework permission caches", error);
        }
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        // On modern Android these queries moved out of IPackageManager. Query
        // the package owning the real UID so a clone cannot inherit the source
        // app's grants or denial flags, and permission requests remain requestable.
        addMethodHook(new HostPackageQuery("checkPermission"));
        addMethodHook(new HostPackageQuery("getPermissionFlags"));
        addMethodHook(new HostPackageQuery("getPermissionRequestState"));
        addMethodHook(new HostPackageQuery("shouldShowRequestPermissionRationale"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    public static class HostPackageQuery extends MethodHook {
        private final String methodName;

        public HostPackageQuery(String methodName) {
            this.methodName = methodName;
        }

        @Override
        protected String getMethodName() {
            return methodName;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            HostRuntimePermissions.rewriteCurrentClonePackage(args);
            return method.invoke(who, args);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

}
