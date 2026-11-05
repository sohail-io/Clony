package top.niunaijun.blackbox.fake.service;

import android.app.ActivityManager;

import java.lang.reflect.Method;

import black.android.app.BRActivityTaskManager;
import black.android.os.BRServiceManager;
import black.android.util.BRSingleton;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.TaskDescriptionCompat;
import java.lang.reflect.Method;


@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityTaskManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ActivityTaskManager";

    public IActivityTaskManagerProxy() {
        super(BRServiceManager.get().getService("activity_task"));
    }

    @Override
    protected Object getWho() {
        try {
            // Read the framework singleton directly. Calling ActivityTaskManager.getService()
            // is blocked on Android 17, while the singleton getter still resolves the existing
            // IActivityTaskManager instance without calling Stub.asInterface() from app code.
            Object singleton = BRActivityTaskManager.get().IActivityTaskManagerSingleton();
            Object service = BRSingleton.get(singleton).get();
            if (service == null) {
                Slog.e(TAG, "activity_task interface is unavailable; virtual activity routing cannot be hooked");
            } else {
                Slog.i(TAG, "activity_task interface acquired from framework singleton: " +
                        service.getClass().getName());
            }
            return service;
        } catch (Throwable error) {
            Slog.e(TAG, "Unable to read the ActivityTaskManager singleton", error);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("activity_task");
        Object o = BRActivityTaskManager.get().IActivityTaskManagerSingleton();
        // proxyInvocation already implements IActivityTaskManager. Converting the Binder
        // stub back through Stub.asInterface() is denied by Android 17's hidden API policy.
        BRSingleton.get(o)._set_mInstance(proxyInvocation);
        Slog.i(TAG, "activity_task proxy installed");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getActivityClientController")
    public static class GetActivityClientController extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object controller = method.invoke(who, args);
            return IActivityClientProxy.wrapController(controller);
        }
    }

    
    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = TaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }
}
