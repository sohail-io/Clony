package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import black.android.telephony.BRTelephonyManager;
import black.android.os.BRServiceManager;
import black.com.android.internal.telephony.BRISubStub;
import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;

/** Simulates the selected profile through SubscriptionManager and its telephony service. */
public class ISubProxy extends BinderInvocationStub {
    private static final String SERVICE_NAME = "isub";

    public ISubProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        return BRISubStub.get().asInterface(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
        if (BRTelephonyManager.get()._check_sISub() != null) {
            BRTelephonyManager.get()._set_sISub((IInterface) proxyInvocation);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = super.invoke(proxy, method, args);
        CloneNetworkIdentityStore.Options options = ITelephonyManagerProxy.identityOptions();
        if (!ITelephonyManagerProxy.isSimProfileActive(options)) return result;
        if ("getActiveSubIdList".equals(method.getName()) && result instanceof int[]) {
            return new int[]{syntheticSubscriptionId(null, method, args)};
        }
        String methodName = method.getName().toLowerCase(java.util.Locale.ROOT);
        if (method.getReturnType() == String.class
                && (methodName.contains("subscriberid") || methodName.contains("imsi"))) {
            return VirtualDeviceIdentity.getProfileImsi(
                    BlackBoxCore.getContext(), BActivityThread.getAppPackageName(),
                    BActivityThread.getUserId(), options.simOperatorCode);
        }
        if (method.getReturnType() == String.class
                && (methodName.contains("iccserial") || methodName.contains("icc_id")
                || methodName.contains("iccid") || methodName.contains("simserialnumber"))) {
            return VirtualDeviceIdentity.getProfileIccid(
                    BlackBoxCore.getContext(), BActivityThread.getAppPackageName(),
                    BActivityThread.getUserId(), options.simOperatorCode);
        }
        if (result instanceof SubscriptionInfo) {
            return applyProfile((SubscriptionInfo) result, options, method, args);
        }
        if (result == null && SubscriptionInfo.class.isAssignableFrom(method.getReturnType())) {
            return applyProfile(null, options, method, args);
        }
        if (result instanceof List<?>) {
            List<Object> rewritten = new ArrayList<>(((List<?>) result).size());
            for (Object item : (List<?>) result) {
                rewritten.add(item instanceof SubscriptionInfo
                        ? applyProfile((SubscriptionInfo) item, options, method, args)
                        : item);
            }
            if (rewritten.isEmpty() && isSubscriptionInfoList(method)) {
                SubscriptionInfo synthetic = applyProfile(null, options, method, args);
                if (synthetic != null) rewritten.add(synthetic);
            }
            return rewritten;
        }
        return result;
    }

    private static SubscriptionInfo applyProfile(
            SubscriptionInfo info,
            CloneNetworkIdentityStore.Options options,
            Method sourceMethod,
            Object[] args
    ) {
        String iccid = VirtualDeviceIdentity.getProfileIccid(
                BlackBoxCore.getContext(), BActivityThread.getAppPackageName(),
                BActivityThread.getUserId(), options.simOperatorCode);
        if (iccid == null) return null;
        String mcc = options.simOperatorCode.substring(0, 3);
        String mnc = options.simOperatorCode.substring(3);
        try {
            Class<?> builderClass = Class.forName(SubscriptionInfo.class.getName() + "$Builder");
            Object builder = info == null
                    ? builderClass.getDeclaredConstructor().newInstance()
                    : newBuilderFromInfo(builderClass, info);
            invokeBuilder(builderClass, builder, "setId", int.class,
                    syntheticSubscriptionId(info, sourceMethod, args));
            invokeBuilder(builderClass, builder, "setIccId", String.class, iccid);
            invokeBuilder(builderClass, builder, "setSimSlotIndex", int.class,
                    syntheticSlotIndex(info, sourceMethod, args));
            invokeBuilder(builderClass, builder, "setDisplayName", CharSequence.class,
                    options.simCarrierName.trim());
            invokeBuilder(builderClass, builder, "setCarrierName", CharSequence.class,
                    options.simCarrierName.trim());
            invokeBuilder(builderClass, builder, "setNumber", String.class,
                    ITelephonyManagerProxy.simulatedPhoneNumber(options));
            invokeBuilder(builderClass, builder, "setMcc", String.class, mcc);
            invokeBuilder(builderClass, builder, "setMnc", String.class, mnc);
            invokeBuilder(builderClass, builder, "setCountryIso", String.class,
                    options.simCountryIso.trim());
            Method build = builderClass.getDeclaredMethod("build");
            build.setAccessible(true);
            return (SubscriptionInfo) build.invoke(builder);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("ISubProxy", "Could not build the selected SIM subscription view",
                    error);
            return info == null ? null : applyProfileWithLegacyFields(info, options, iccid, mcc, mnc);
        }
    }

    private static Object newBuilderFromInfo(Class<?> builderClass, SubscriptionInfo info)
            throws ReflectiveOperationException {
        try {
            Constructor<?> copyConstructor = builderClass.getDeclaredConstructor(SubscriptionInfo.class);
            copyConstructor.setAccessible(true);
            return copyConstructor.newInstance(info);
        } catch (NoSuchMethodException unavailable) {
            return builderClass.getDeclaredConstructor().newInstance();
        }
    }

    private static void invokeBuilder(
            Class<?> builderClass, Object builder, String methodName, Class<?> parameterType,
            Object value) throws ReflectiveOperationException {
        Method setter = builderClass.getDeclaredMethod(methodName, parameterType);
        setter.setAccessible(true);
        setter.invoke(builder, value);
    }

    private static SubscriptionInfo applyProfileWithLegacyFields(
            SubscriptionInfo info,
            CloneNetworkIdentityStore.Options options,
            String iccid,
            String mcc,
            String mnc
    ) {
        try {
            setLegacyField(info, "mIccId", iccid);
            setLegacyField(info, "mCarrierName", options.simCarrierName.trim());
            setLegacyField(info, "mDisplayName", options.simCarrierName.trim());
            setLegacyField(info, "mNumber", ITelephonyManagerProxy.simulatedPhoneNumber(options));
            setLegacyField(info, "mCountryIso", options.simCountryIso.trim());
            setLegacyField(info, "mMcc", mcc);
            setLegacyField(info, "mMnc", mnc);
            return info;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("ISubProxy", "Could not update legacy SIM subscription view", error);
            return null;
        }
    }

    private static void setLegacyField(Object target, String name, String value)
            throws ReflectiveOperationException {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                if (field.getType() == int.class || field.getType() == Integer.class) {
                    field.set(target, Integer.parseInt(value));
                } else {
                    field.set(target, value);
                }
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static int syntheticSubscriptionId(
            SubscriptionInfo info, Method sourceMethod, Object[] args) {
        if ("getActiveSubIdList".equals(sourceMethod.getName())) return 1;
        if (info != null && info.getSubscriptionId() >= 0) return info.getSubscriptionId();
        if ("getActiveSubscriptionInfo".equals(sourceMethod.getName())
                && args != null && args.length > 0 && args[0] instanceof Integer
                && (Integer) args[0] >= 0) {
            return (Integer) args[0];
        }
        return 1;
    }

    private static int syntheticSlotIndex(
            SubscriptionInfo info, Method sourceMethod, Object[] args) {
        if (info != null && info.getSimSlotIndex() >= 0) return info.getSimSlotIndex();
        if (sourceMethod.getName().contains("ForSimSlotIndex")
                && args != null && args.length > 0 && args[0] instanceof Integer
                && (Integer) args[0] >= 0) {
            return (Integer) args[0];
        }
        return 0;
    }

    private static boolean isSubscriptionInfoList(Method method) {
        String methodName = method.getName().toLowerCase(java.util.Locale.ROOT);
        return methodName.contains("subscriptioninfo") || methodName.contains("subinfolist");
    }

    private static Object hidePhoneNumber(Object who, Method method, Object[] args) throws Throwable {
        String override = phoneNumberOverride(ITelephonyManagerProxy.identityOptions());
        if (override != null) return override;
        return method.invoke(who, args);
    }

    static String phoneNumberOverride(
            top.niunaijun.blackbox.core.CloneNetworkIdentityStore.Options options
    ) {
        String simulated = ITelephonyManagerProxy.simulatedPhoneNumber(options);
        if (simulated != null) return simulated;
        return ITelephonyManagerProxy.shouldSuppressPhoneNumber(options) ? "" : null;
    }

    @ProxyMethod("getPhoneNumber")
    public static class GetPhoneNumber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hidePhoneNumber(who, method, args);
        }
    }

    @ProxyMethod("getPhoneNumberFromFirstAvailableSource")
    public static class GetPhoneNumberFromFirstAvailableSource extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hidePhoneNumber(who, method, args);
        }
    }
}
