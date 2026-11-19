package top.niunaijun.blackbox.fake.service;

import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.lang.reflect.Method;

import black.android.app.usage.BRIUsageStatsManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

public class IUsageStatsManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IUsageStatsManagerProxy";

    public IUsageStatsManagerProxy() {
        super(BRServiceManager.get().getService(Context.USAGE_STATS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIUsageStatsManagerStub.get().asInterface(
                BRServiceManager.get().getService(Context.USAGE_STATS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.USAGE_STATS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getAppStandbyBucket")
    public static class GetAppStandbyBucket extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            if (packageName != null && packageName.equals(BActivityThread.getAppPackageName())) {
                // A virtual app shares Clony's UID and cannot query this protected usage
                // statistic as if it had its own host UID. Its active virtual session should
                // be treated as active without forwarding the privileged query.
                return UsageStatsManager.STANDBY_BUCKET_ACTIVE;
            }
            return method.invoke(who, args);
        }
    }
}
