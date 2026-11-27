package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.telephony.TelephonyManager;
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
            String simulated = simulatedPhoneNumber(identityOptions());
            if (simulated != null) return simulated;
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
        String packageName = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        CloneNetworkIdentityStore.Options options = identityOptions();
        if (isSimProfileActive(options)
                && ("imei".equals(identifierKind) || "device_id".equals(identifierKind))) {
            return VirtualDeviceIdentity.getTelephonyId(
                    BlackBoxCore.getContext(), packageName, userId, "imei");
        }
        if (isSimProfileActive(options) && "imsi".equals(identifierKind)) {
            return VirtualDeviceIdentity.getProfileImsi(
                    BlackBoxCore.getContext(), packageName, userId, options.simOperatorCode);
        }
        if (!(platformValue instanceof String) || ((String) platformValue).isEmpty()) {
            return platformValue;
        }

        if (!options.changeTelephonyIds) {
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
        return shouldSuppressCellDetails(identityOptions());
    }

    static boolean shouldSuppressCellDetails(CloneNetworkIdentityStore.Options options) {
        return options != null && (options.hideSimOperatorInfo
                || options.matchProxyRegion
                || isSimProfileActive(options));
    }

    static boolean shouldSuppressPhoneNumber() {
        return shouldSuppressPhoneNumber(identityOptions());
    }

    static boolean shouldSuppressPhoneNumber(CloneNetworkIdentityStore.Options options) {
        return !isSimProfileActive(options) && options != null
                && (options.hideSimOperatorInfo || options.matchProxyRegion);
    }

    static boolean isSimProfileActive(CloneNetworkIdentityStore.Options options) {
        return options != null && options.simulateSimProfile
                && options.simCountryIso.matches("(?i)[a-z]{2}")
                && !options.simCarrierName.trim().isEmpty()
                && options.simOperatorCode.matches("[0-9]{5,6}");
    }

    static int simStateOverride(CloneNetworkIdentityStore.Options options, int platformState) {
        return isSimProfileActive(options) ? TelephonyManager.SIM_STATE_READY : platformState;
    }

    static String simulatedPhoneNumber(CloneNetworkIdentityStore.Options options) {
        if (!isSimProfileActive(options)) return null;
        String generatedNumber = VirtualDeviceIdentity.getProfilePhoneNumber(
                BlackBoxCore.getContext(), BActivityThread.getAppPackageName(),
                BActivityThread.getUserId(), options.simCountryIso);
        return generatedNumber == null ? "" : generatedNumber;
    }

    static CloneNetworkIdentityStore.Options identityOptions() {
        return CloneNetworkIdentityStore.read(
                BlackBoxCore.getContext(),
                BActivityThread.getAppPackageName(),
                BActivityThread.getUserId()
        );
    }

    static Object hideSimOperatorString(Object who, Method method, Object[] args) throws Throwable {
        CloneNetworkIdentityStore.Options options = identityOptions();
        String override = operatorStringOverride(options, method.getName());
        if (override != null) return override;
        return method.invoke(who, args);
    }

    static String operatorStringOverride(
            CloneNetworkIdentityStore.Options options,
            String methodName
    ) {
        if (options == null || methodName == null) return null;
        String normalizedMethod = methodName.toLowerCase(java.util.Locale.ROOT);
        // A user-selected SIM profile is explicit and takes precedence over
        // proxy-country matching and the old redaction option for these values.
        if (isSimProfileActive(options)) {
            if (normalizedMethod.contains("countryiso")) return options.simCountryIso.trim();
            if (normalizedMethod.contains("operatorname")
                    || normalizedMethod.contains("serviceprovidername")) {
                return options.simCarrierName.trim();
            }
            if (normalizedMethod.contains("operatornumeric")
                    || normalizedMethod.contains("simoperator")
                    || normalizedMethod.contains("networkoperator")) {
                return options.simOperatorCode.trim();
            }
            return "";
        }

        if (options.hideSimOperatorInfo) return "";

        if (options.matchProxyRegion) {
            if (normalizedMethod.contains("countryiso")) return options.proxyCountryIso;
            // The proxy reveals a country, not a mobile carrier or cell.
            return "";
        }
        return null;
    }

    @ProxyMethod("getSimState")
    public static class GetSimState extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            CloneNetworkIdentityStore.Options options = identityOptions();
            if (isSimProfileActive(options)) return TelephonyManager.SIM_STATE_READY;
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getSimStateForSlotIndex")
    public static class GetSimStateForSlotIndex extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            CloneNetworkIdentityStore.Options options = identityOptions();
            if (isSimProfileActive(options)) return TelephonyManager.SIM_STATE_READY;
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getSimStateForPhoneId")
    public static class GetSimStateForPhoneId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            CloneNetworkIdentityStore.Options options = identityOptions();
            if (isSimProfileActive(options)) return TelephonyManager.SIM_STATE_READY;
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCellLocation")
    public static class GetCellLocation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getCellLocation");
            if (hideSimOperatorInfo()) {
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

    @ProxyMethod("getServiceProviderNameForPhone")
    public static class GetServiceProviderNameForPhone extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return hideSimOperatorString(who, method, args);
        }
    }

    @ProxyMethod("getServiceProviderNameForSubscriber")
    public static class GetServiceProviderNameForSubscriber extends MethodHook {
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

    @ProxyMethod("getSimOperatorNumeric")
    public static class GetSimOperatorNumeric extends MethodHook {
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

    @ProxyMethod("getSimOperatorName")
    public static class GetSimOperatorName extends MethodHook {
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
