package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.os.BRIVibratorManagerServiceStub;
import black.android.os.BRServiceManager;
import black.com.android.internal.os.BRIVibratorServiceStub;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IVibratorServiceProxy extends BinderInvocationStub {
    private Object vibratorManager;
    private static String NAME;
    static {
        if (BuildCompat.isS()) {
            NAME = "vibrator_manager";
        } else {
            NAME = Context.VIBRATOR_SERVICE;
        }
    }

    public IVibratorServiceProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        if (BuildCompat.isS()) {
            try {
                vibratorManager = BlackBoxCore.getContext().getSystemService(NAME);
                Object service = FrameworkServiceCache.get(vibratorManager, "android.os.IVibratorManagerService");
                if (service != null) return service;
            } catch (Exception error) {
                Slog.w(TAG, "Unable to read cached vibrator service", error);
            }
        }
        IBinder service = BRServiceManager.get().getService(NAME);
        if (BuildCompat.isS()) {
            return BRIVibratorManagerServiceStub.get().asInterface(service);
        }
        return BRIVibratorServiceStub.get().asInterface(service);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(NAME);
        if (vibratorManager != null) {
            try {
                // Existing managers cache the interface before the Binder cache is replaced.
                FrameworkServiceCache.set(vibratorManager, "android.os.IVibratorManagerService", proxyInvocation);
                Slog.i(TAG, "vibrator manager proxy installed");
            } catch (Exception error) {
                Slog.e(TAG, "Unable to update cached vibrator service", error);
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // A framework Vibrator can retain the installed source app's UID as well
        // as the virtual UID. Both must be attributed to the real caller here.
        rewriteCallerUid(method.getName(), args, BlackBoxCore.getHostUid());
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    public static void rewriteCallerUid(String methodName, Object[] args, int hostUid) {
        if (args == null || args.length == 0 || !(args[0] instanceof Integer)) return;
        if ("vibrate".equals(methodName) || "setAlwaysOnEffect".equals(methodName)
                || "performHapticFeedback".equals(methodName)
                || "performHapticFeedbackForInputDevice".equals(methodName)) {
            args[0] = hostUid;
        }
    }
}
