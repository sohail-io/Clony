package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.util.Log;

import java.lang.reflect.Method;

import black.android.net.wifi.BRIWifiManagerStub;
import black.android.net.wifi.BRWifiInfo;
import black.android.net.wifi.BRWifiSsid;
import black.android.net.wifi.WifiInfoContext;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;


public class IWifiManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IWifiManagerProxy";

    public IWifiManagerProxy() {
        super(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIWifiManagerStub.get().asInterface(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getConnectionInfo")
    public static class GetConnectionInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            WifiInfo wifiInfo = (WifiInfo) method.invoke(who, args);
            if (wifiInfo == null) return null;

            String packageName = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            CloneNetworkIdentityStore.Options options = options(packageName, userId);
            String redactedMac = "02:00:00:00:00:00";
            String mac = options.changeWifiMac
                    ? VirtualDeviceIdentity.getNetworkMacAddress(
                            BlackBoxCore.getContext(), packageName, userId, "wifi_mac")
                    : redactedMac;
            String bssid = options.changeWifiMac
                    ? VirtualDeviceIdentity.getNetworkMacAddress(
                            BlackBoxCore.getContext(), packageName, userId, "wifi_bssid")
                    : redactedMac;
            String ssid = options.hideWifiInfo ? "<unknown ssid>" : "BlackBox_Wifi";

            WifiInfoContext bridge = BRWifiInfo.get(wifiInfo);
            bridge._set_mBSSID(options.hideWifiInfo ? null : bssid);
            bridge._set_mMacAddress(options.hideWifiInfo ? redactedMac : mac);
            try {
                bridge._set_mSSID(ssid);
            } catch (Throwable ignored) {
                // Newer Android releases source the SSID from WifiSsid instead.
            }
            bridge._set_mWifiSsid(BRWifiSsid.get().createFromAsciiEncoded(ssid));
            return wifiInfo;
        }

        public static String intIP2StringIP(int ip) {
            return (ip & 0xFF) + "." +
                    ((ip >> 8) & 0xFF) + "." +
                    ((ip >> 16) & 0xFF) + "." +
                    (ip >> 24 & 0xFF);
        }

        public static int ip2Int(String ipString) {
            
            String[] ipSlices = ipString.split("\\.");
            int rs = 0;
            for (int i = 0; i < ipSlices.length; i++) {
                
                int intSlice = Integer.parseInt(ipSlices[i]) << 8 * i;
                
                rs = rs | intSlice;
            }
            return rs;
        }
    }

    @ProxyMethod("getScanResults")
    public static class GetScanResults extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object platformValue = method.invoke(who, args);
            if (options(BActivityThread.getAppPackageName(), BActivityThread.getUserId()).hideWifiInfo) {
                return emptyListResult(platformValue, method);
            }
            return platformValue;
        }
    }

    @ProxyMethod("getConfiguredNetworks")
    public static class GetConfiguredNetworks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object platformValue = method.invoke(who, args);
            if (options(BActivityThread.getAppPackageName(), BActivityThread.getUserId()).hideWifiInfo) {
                return emptyListResult(platformValue, method);
            }
            return platformValue;
        }
    }

    private static Object emptyListResult(Object platformValue, Method method) {
        if (platformValue instanceof java.util.List) {
            return java.util.Collections.emptyList();
        }
        if (java.util.List.class.isAssignableFrom(method.getReturnType())) {
            return java.util.Collections.emptyList();
        }
        Class<?> resultClass = platformValue == null ? method.getReturnType() : platformValue.getClass();
        try {
            return resultClass.getConstructor(java.util.List.class)
                    .newInstance(java.util.Collections.emptyList());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CloneNetworkIdentityStore.Options options(String packageName, int userId) {
        return CloneNetworkIdentityStore.read(
                BlackBoxCore.getContext(), packageName, userId);
    }
}
