package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.telephony.BRTelephonyManager;
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
            return null;
        }
    }
}
