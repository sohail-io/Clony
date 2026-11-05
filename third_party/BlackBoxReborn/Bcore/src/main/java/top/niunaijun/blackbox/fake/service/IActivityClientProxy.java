package top.niunaijun.blackbox.fake.service;

import android.app.ActivityManager;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import black.android.app.BRActivityClient;
import black.android.util.BRSingleton;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.TaskDescriptionCompat;


@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityClientProxy extends ClassInvocationStub {
    public static final String TAG = "IActivityClientProxy";
    private final Object who;
    private static final Map<Object, Object> sControllerProxies =
            Collections.synchronizedMap(new WeakHashMap<>());

    public IActivityClientProxy(Object who) {
        this.who = who;
    }

    /** Wrap a controller received in a launch transaction without consulting ActivityClient's
     * hidden singleton fields, which Android 17 blocks for ordinary applications. */
    public static Object wrapController(Object controller) {
        if (controller == null) return null;
        synchronized (sControllerProxies) {
            Object existing = sControllerProxies.get(controller);
            if (existing != null) return existing;
            IActivityClientProxy stub = new IActivityClientProxy(controller);
            stub.onlyProxy(true);
            stub.injectHook();
            Object proxy = stub.getProxyInvocation();
            if (proxy == null) return controller;
            sControllerProxies.put(controller, proxy);
            return proxy;
        }
    }

    @Override
    protected Object getWho() {
        if (who != null) {
            return who;
        }
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        return BRSingleton.get(singleton).get();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        BRSingleton.get(singleton)._set_mInstance(proxyInvocation);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @Override
    public void onlyProxy(boolean o) {
        super.onlyProxy(o);
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onFinishActivity(token);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onActivityResumed(token);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onActivityDestroyed(token);
            return method.invoke(who, args);
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
