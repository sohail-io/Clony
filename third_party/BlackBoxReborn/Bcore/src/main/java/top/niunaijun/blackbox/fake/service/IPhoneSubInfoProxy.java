package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.telephony.BRTelephonyManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class IPhoneSubInfoProxy extends ClassInvocationStub {
    public static final String TAG = "IPhoneSubInfoProxy";

    public IPhoneSubInfoProxy() {
        if (BRTelephonyManager.get()._check_sServiceHandleCacheEnabled() != null) {
            BRTelephonyManager.get()._set_sServiceHandleCacheEnabled(true);
        }
        if (BRTelephonyManager.get()._check_getSubscriberInfoService() != null) {
            BRTelephonyManager.get().getSubscriberInfoService();
        }
    }

    @Override
    protected Object getWho() {
        return BRTelephonyManager.get().sIPhoneSubInfo();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRTelephonyManager.get()._set_sIPhoneSubInfo(proxyInvocation);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static Object lineNumber(Object who, Method method, Object[] args) throws Throwable {
        CloneNetworkIdentityStore.Options options = ITelephonyManagerProxy.identityOptions();
        if (ITelephonyManagerProxy.isSimProfileActive(options)) {
            return ITelephonyManagerProxy.simulatedPhoneNumber(options);
        }
        if (ITelephonyManagerProxy.shouldSuppressPhoneNumber(options)) return null;
        return method.invoke(who, args);
    }

    private static Object simSerialNumber(Object who, Method method, Object[] args) throws Throwable {
        CloneNetworkIdentityStore.Options options = ITelephonyManagerProxy.identityOptions();
        if (ITelephonyManagerProxy.isSimProfileActive(options)) {
            return VirtualDeviceIdentity.getProfileIccid(
                    BlackBoxCore.getContext(),
                    BActivityThread.getAppPackageName(),
                    BActivityThread.getUserId(),
                    options.simOperatorCode
            );
        }
        if (options.hideSimOperatorInfo || options.matchProxyRegion) return null;
        return method.invoke(who, args);
    }

    @ProxyMethod("getDeviceId")
    public static class GetDeviceId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getDeviceIdWithFeature")
    public static class GetDeviceIdWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getDeviceIdForPhone")
    public static class GetDeviceIdForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getImeiForSubscriber")
    public static class GetImeiForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getMeidForSubscriber")
    public static class GetMeidForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "meid");
        }
    }

    @ProxyMethod("getSubscriberId")
    public static class GetSubscriberId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imsi");
        }
    }

    @ProxyMethod("getSubscriberIdWithFeature")
    public static class GetSubscriberIdWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imsi");
        }
    }

    @ProxyMethod("getSubscriberIdForSubscriber")
    public static class GetSubscriberIdForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ITelephonyManagerProxy.virtualizedIdentifier(who, method, args, "imsi");
        }
    }

    @ProxyMethod("getLine1NumberForSubscriber")
    public static class getLine1NumberForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return lineNumber(who, method, args);
        }
    }

    @ProxyMethod("getIccSerialNumber")
    public static class GetIccSerialNumber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return simSerialNumber(who, method, args);
        }
    }

    @ProxyMethod("getIccSerialNumberForSubscriber")
    public static class GetIccSerialNumberForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return simSerialNumber(who, method, args);
        }
    }

    @ProxyMethod("getIccSerialNumberWithFeature")
    public static class GetIccSerialNumberWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return simSerialNumber(who, method, args);
        }
    }

    @ProxyMethod("getLine1Number")
    public static class GetLine1Number extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return lineNumber(who, method, args);
        }
    }

    @ProxyMethod("getLine1NumberWithFeature")
    public static class GetLine1NumberWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return lineNumber(who, method, args);
        }
    }

    @ProxyMethod("getMsisdn")
    public static class GetMsisdn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return lineNumber(who, method, args);
        }
    }

    @ProxyMethod("getMsisdnForSubscriber")
    public static class GetMsisdnForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return lineNumber(who, method, args);
        }
    }

    @ProxyMethod("getSimSerialNumber")
    public static class GetSimSerialNumber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return simSerialNumber(who, method, args);
        }
    }

    @ProxyMethod("getSimSerialNumberForSubscriber")
    public static class GetSimSerialNumberForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return simSerialNumber(who, method, args);
        }
    }
}
