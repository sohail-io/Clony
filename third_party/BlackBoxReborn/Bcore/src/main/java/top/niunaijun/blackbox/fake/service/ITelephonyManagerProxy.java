package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import black.android.os.BRServiceManager;
import black.com.android.internal.telephony.BRITelephonyStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


public class ITelephonyManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ITelephonyManagerProxy";

    public ITelephonyManagerProxy() {
        super(BRServiceManager.get().getService(Context.TELEPHONY_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder telephony = BRServiceManager.get().getService(Context.TELEPHONY_SERVICE);
        return BRITelephonyStub.get().asInterface(telephony);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getDeviceId")
    public static class GetDeviceId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getImeiForSlot")
    public static class getImeiForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return virtualizedIdentifier(who, method, args, "imei");
        }
    }

    @ProxyMethod("getMeidForSlot")
    public static class GetMeidForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return virtualizedIdentifier(who, method, args, "meid");
        }
    }

    @ProxyMethod("isUserDataEnabled")
    public static class IsUserDataEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }


    @ProxyMethod("getLine1NumberForDisplay")
    public static class getLine1NumberForDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ProxyMethod("getSubscriberId")
    public static class GetSubscriberId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return virtualizedIdentifier(who, method, args, "imsi");
        }
    }

    @ProxyMethod("getDeviceIdWithFeature")
    public static class GetDeviceIdWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return virtualizedIdentifier(who, method, args, "imei");
        }
    }

    static Object virtualizedIdentifier(
            Object who,
            Method method,
            Object[] args,
            String identifierKind
    ) throws Throwable {
        // Binder observes Clony's real host UID. Pair that UID with the host
        // package for these identifier reads before Android checks access.
        MethodParameterUtils.replaceFirstAppPkg(args);
        Object platformValue = method.invoke(who, args);
        if (!(platformValue instanceof String) || ((String) platformValue).isEmpty()) {
            return platformValue;
        }

        String packageName = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        if (!identityOptions().changeTelephonyIds) {
            // Do not pass the host hardware identifier through when cloning is disabled.
            return null;
        }
        String virtualValue = VirtualDeviceIdentity.getTelephonyId(
                BlackBoxCore.getContext(), packageName, userId, identifierKind);
        if (virtualValue == null) {
            // Never leak a host hardware identifier if the per-clone identity
            // could not be created.
            Slog.w(TAG, "Could not create a per-clone telephony identifier; returning unavailable value"
                    + " package=" + packageName + " userId=" + userId);
            return null;
        }
        Slog.d(TAG, "virtual_identity hook=telephony_" + identifierKind
                + " package=" + packageName + " userId=" + userId + " source=clone_seed");
        return virtualValue;
    }

    static boolean hideSimOperatorInfo() {
        return identityOptions().hideSimOperatorInfo;
    }

    private static CloneNetworkIdentityStore.Options identityOptions() {
        return CloneNetworkIdentityStore.read(
                BlackBoxCore.getContext(),
                BActivityThread.getAppPackageName(),
                BActivityThread.getUserId()
        );
    }

    static Object hideSimOperatorString(Object who, Method method, Object[] args) throws Throwable {
        CloneNetworkIdentityStore.Options options = identityOptions();
        if (isDubaiCurtainsTestProfileEnabled(options)) {
            String methodName = method.getName().toLowerCase(java.util.Locale.ROOT);
            String configured = null;
            if (methodName.contains("countryiso")) {
                configured = options.simCountryIso;
            } else if (methodName.contains("operatorname")) {
                configured = options.simCarrierName;
            } else if (methodName.contains("operatornumeric")
                    || methodName.contains("simoperator")
                    || methodName.contains("networkoperator")) {
                configured = options.simOperatorCode;
            }
            return configured == null ? "" : configured.trim();
        }
        if (options.hideSimOperatorInfo) return "";
        return method.invoke(who, args);
    }

    static String simulatedPhoneNumber() {
        CloneNetworkIdentityStore.Options options = identityOptions();
        if (!isDubaiCurtainsTestProfileEnabled(options)) return null;
        String number = options.simPhoneNumber == null ? "" : options.simPhoneNumber.trim();
        return number.isEmpty() ? null : number;
    }

    static boolean isDubaiCurtainsTestProfileEnabled() {
        return isDubaiCurtainsTestProfileEnabled(identityOptions());
    }
    private static boolean isDubaiCurtainsTestProfileEnabled(CloneNetworkIdentityStore.Options options) {
        return options != null && options.simulateSimProfile;
    }
//    private static boolean isDubaiCurtainsTestProfileEnabled(CloneNetworkIdentityStore.Options options) {
//        return options != null
//                && options.simulateSimProfile
//                && CloneNetworkIdentityStore.SIM_PROFILE_TEST_PACKAGE.equals(BActivityThread.getAppPackageName()
//        );
//    }

    @ProxyMethod("getCellLocation")
    public static class GetCellLocation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getCellLocation");
            if (hideSimOperatorInfo()) {
                method.invoke(who, args);
                return null;
            }
            if (BLocationManager.isFakeLocationEnable()) {
                BCell cell = BLocationManager.get().getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (cell != null) {
                    
                    return null;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAllCellInfo")
    public static class GetAllCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (hideSimOperatorInfo()) {
                method.invoke(who, args);
                return java.util.Collections.emptyList();
            }
            if (BLocationManager.isFakeLocationEnable()) {
                List<BCell> cell = BLocationManager.get().getAllCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                
                return cell;
            }
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return null;
            }
        }
    }

    @ProxyMethod("getNetworkOperator")
    public static class GetNetworkOperator extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getNetworkOperator");
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkOperatorForPhone")
    public static class GetNetworkOperatorForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkOperatorForSubscriber")
    public static class GetNetworkOperatorForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkOperatorName")
    public static class GetNetworkOperatorName extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkOperatorNameForSubscriber")
    public static class GetNetworkOperatorNameForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkOperatorNameForPhone")
    public static class GetNetworkOperatorNameForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperatorNumericForPhone")
    public static class GetSimOperatorNumericForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperatorForSubscriber")
    public static class GetSimOperatorForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperatorNumericForSubscriber")
    public static class GetSimOperatorNumericForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperatorNameForSubscriber")
    public static class GetSimOperatorNameForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimCountryIsoForSubscriber")
    public static class GetSimCountryIsoForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkCountryIsoForSubscriber")
    public static class GetNetworkCountryIsoForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperatorNameForPhone")
    public static class GetSimOperatorNameForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimOperator")
    public static class GetSimOperator extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getSimCountryIsoForPhone")
    public static class GetSimCountryIsoForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkCountryIsoForPhone")
    public static class GetNetworkCountryIsoForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getNetworkTypeForSubscriber")
    public static class GetNetworkTypeForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return 0;
            }
        }
    }

    @ProxyMethod("getNeighboringCellInfo")
    public static class GetNeighboringCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getNeighboringCellInfo");
            if (hideSimOperatorInfo()) {
                method.invoke(who, args);
                return java.util.Collections.emptyList();
            }
            if (BLocationManager.isFakeLocationEnable()) {
                List<BCell> cell = BLocationManager.get().getNeighboringCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                
                return null;
            }
            return method.invoke(who, args);
        }
    }
}
