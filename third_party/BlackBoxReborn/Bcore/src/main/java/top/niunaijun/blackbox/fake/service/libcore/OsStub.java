package top.niunaijun.blackbox.fake.service.libcore;

import android.os.Process;
import android.util.Log;

import java.lang.reflect.Method;

import black.libcore.io.BRLibcore;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.IOCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Reflector;


public class OsStub extends ClassInvocationStub {
    public static final String TAG = "OsStub";
    private Object mBase;

    public OsStub() {
        mBase = BRLibcore.get().os();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRLibcore.get()._set_os(proxyInvocation);
    }

    @Override
    protected void onBindMethod() {
    }

    @Override
    public boolean isBadEnv() {
        return BRLibcore.get().os() != getProxyInvocation();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String cropPath = null;
        String redirectedCropPath = null;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null)
                    continue;
                String original = null;
                String redirected = null;
                if (args[i] instanceof String && ((String) args[i]).startsWith("/")) {
                    original = (String) args[i];
                    redirected = IOCore.get().redirectPath(original);
                    args[i] = redirected;
                } else if (args[i] instanceof String) {
                    original = (String) args[i];
                    redirected = original;
                }
                if (isProfileCropPath(original) || isProfileCropPath(redirected)) {
                    cropPath = original;
                    redirectedCropPath = redirected;
                }
            }
        }
        if (cropPath == null) {
            return super.invoke(proxy, method, args);
        }

        Log.i(TAG, "profile-crop-io start op=" + method.getName()
                + " requested=" + cropPath + " resolved=" + redirectedCropPath);
        try {
            Object result = super.invoke(proxy, method, args);
            String resultSummary = result == null || result instanceof Number || result instanceof Boolean
                    ? String.valueOf(result) : result.getClass().getSimpleName();
            Log.i(TAG, "profile-crop-io success op=" + method.getName()
                    + " requested=" + cropPath + " resolved=" + redirectedCropPath
                    + " result=" + resultSummary);
            return result;
        } catch (Throwable error) {
            Log.e(TAG, "profile-crop-io failure op=" + method.getName()
                    + " requested=" + cropPath + " resolved=" + redirectedCropPath, error);
            throw error;
        }
    }

    private static boolean isProfileCropPath(String path) {
        return path != null && (path.contains("profileHeader") || path.contains("sd_can_write"));
    }

    @ProxyMethod("getuid")
    public static class getuid extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int callUid = (int) method.invoke(who, args);
            return getFakeUid(callUid);
        }
    }

    @ProxyMethod("stat")
    public static class stat extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object invoke = null;
            try {
                invoke = method.invoke(who, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
            Reflector.with(invoke).field("st_uid").set(getFakeUid(-1));
            return invoke;
        }
    }

    private static int getFakeUid(int callUid) {
        if (callUid > 0 && callUid <= Process.FIRST_APPLICATION_UID)
            return callUid;

        if (BActivityThread.isThreadInit() && BActivityThread.currentActivityThread().isInit()) {
            return BActivityThread.getBAppId();
        } else {
            return BlackBoxCore.getHostUid();
        }
    }
}
