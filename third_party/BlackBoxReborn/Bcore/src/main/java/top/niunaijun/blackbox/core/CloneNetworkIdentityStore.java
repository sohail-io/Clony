package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores per-copy network identity controls and defaults for future copies. */
public final class CloneNetworkIdentityStore {
    private static final String PREFERENCES = "clone_network_identity_v1";
    private static final String KEY_PREFIX = "network_identity:";

    private CloneNetworkIdentityStore() {
    }

    public static Options defaults() {
        return new Options(true, false, true, false, false);
    }

    public static Options readTemplate(Context context, String packageName) {
        if (!isValid(context, packageName)) return defaults();
        SharedPreferences preferences = preferences(context);
        String prefix = templateKey(packageName);
        Options defaults = defaults();
        return new Options(
                preferences.getBoolean(prefix + "changeTelephonyIds", defaults.changeTelephonyIds),
                preferences.getBoolean(prefix + "hideSimOperatorInfo", defaults.hideSimOperatorInfo),
                preferences.getBoolean(prefix + "changeWifiMac", defaults.changeWifiMac),
                preferences.getBoolean(prefix + "hideWifiInfo", defaults.hideWifiInfo),
                preferences.getBoolean(prefix + "hideDnsServers", defaults.hideDnsServers)
        );
    }

    public static Options read(Context context, String packageName, int userId) {
        if (!isValid(context, packageName) || userId < 0) return defaults();
        SharedPreferences preferences = preferences(context);
        String prefix = cloneKey(packageName, userId);
        Options template = readTemplate(context, packageName);
        return new Options(
                preferences.getBoolean(prefix + "changeTelephonyIds", template.changeTelephonyIds),
                preferences.getBoolean(prefix + "hideSimOperatorInfo", template.hideSimOperatorInfo),
                preferences.getBoolean(prefix + "changeWifiMac", template.changeWifiMac),
                preferences.getBoolean(prefix + "hideWifiInfo", template.hideWifiInfo),
                preferences.getBoolean(prefix + "hideDnsServers", template.hideDnsServers)
        );
    }

    public static boolean writeTemplate(Context context, String packageName, Options options) {
        if (!isValid(context, packageName) || options == null) return false;
        return writeOptions(preferences(context).edit(), templateKey(packageName), options).commit();
    }

    public static boolean write(Context context, String packageName, int userId, Options options) {
        if (!isValid(context, packageName) || userId < 0 || options == null) return false;
        return writeOptions(preferences(context).edit(), cloneKey(packageName, userId), options).commit();
    }

    public static boolean delete(Context context, String packageName, int userId) {
        if (!isValid(context, packageName) || userId < 0) return false;
        String prefix = cloneKey(packageName, userId);
        return preferences(context).edit()
                .remove(prefix + "changeTelephonyIds")
                .remove(prefix + "hideSimOperatorInfo")
                .remove(prefix + "changeWifiMac")
                .remove(prefix + "hideWifiInfo")
                .remove(prefix + "hideDnsServers")
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor writeOptions(
            SharedPreferences.Editor editor,
            String prefix,
            Options options
    ) {
        return editor
                .putBoolean(prefix + "changeTelephonyIds", options.changeTelephonyIds)
                .putBoolean(prefix + "hideSimOperatorInfo", options.hideSimOperatorInfo)
                .putBoolean(prefix + "changeWifiMac", options.changeWifiMac)
                .putBoolean(prefix + "hideWifiInfo", options.hideWifiInfo)
                .putBoolean(prefix + "hideDnsServers", options.hideDnsServers);
    }

    private static String cloneKey(String packageName, int userId) {
        return KEY_PREFIX + "clone:" + packageName + ":" + userId + ":";
    }

    private static String templateKey(String packageName) {
        return KEY_PREFIX + "template:" + packageName + ":";
    }

    private static boolean isValid(Context context, String packageName) {
        return context != null && packageName != null && packageName.matches("[A-Za-z0-9_.]+");
    }

    public static final class Options {
        public final boolean changeTelephonyIds;
        public final boolean hideSimOperatorInfo;
        public final boolean changeWifiMac;
        public final boolean hideWifiInfo;
        public final boolean hideDnsServers;

        public Options(
                boolean changeTelephonyIds,
                boolean hideSimOperatorInfo,
                boolean changeWifiMac,
                boolean hideWifiInfo,
                boolean hideDnsServers
        ) {
            this.changeTelephonyIds = changeTelephonyIds;
            this.hideSimOperatorInfo = hideSimOperatorInfo;
            this.changeWifiMac = changeWifiMac;
            this.hideWifiInfo = hideWifiInfo;
            this.hideDnsServers = hideDnsServers;
        }
    }
}
