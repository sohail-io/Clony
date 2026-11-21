package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores per-copy network identity controls and defaults for future copies. */
public final class CloneNetworkIdentityStore {
    public static final String SIM_PROFILE_TEST_PACKAGE = "dubai.blinds.curtains";
    private static final String PREFERENCES = "clone_network_identity_v1";
    private static final String KEY_PREFIX = "network_identity:";

    private CloneNetworkIdentityStore() {
    }

    public static Options defaults() {
        return new Options(true, false, true, false, false, false, "", "", "", "", false, "");
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
                preferences.getBoolean(prefix + "hideDnsServers", defaults.hideDnsServers),
                preferences.getBoolean(prefix + "simulateSimProfile", defaults.simulateSimProfile),
                preferences.getString(prefix + "simPhoneNumber", defaults.simPhoneNumber),
                preferences.getString(prefix + "simCarrierName", defaults.simCarrierName),
                preferences.getString(prefix + "simCountryIso", defaults.simCountryIso),
                preferences.getString(prefix + "simOperatorCode", defaults.simOperatorCode),
                preferences.getBoolean(prefix + "matchProxyRegion", defaults.matchProxyRegion),
                preferences.getString(prefix + "proxyCountryIso", defaults.proxyCountryIso)
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
                preferences.getBoolean(prefix + "hideDnsServers", template.hideDnsServers),
                preferences.getBoolean(prefix + "simulateSimProfile", template.simulateSimProfile),
                preferences.getString(prefix + "simPhoneNumber", template.simPhoneNumber),
                preferences.getString(prefix + "simCarrierName", template.simCarrierName),
                preferences.getString(prefix + "simCountryIso", template.simCountryIso),
                preferences.getString(prefix + "simOperatorCode", template.simOperatorCode),
                preferences.getBoolean(prefix + "matchProxyRegion", template.matchProxyRegion),
                preferences.getString(prefix + "proxyCountryIso", template.proxyCountryIso)
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

    /** Saves or clears the country detected through this clone's configured proxy. */
    public static boolean updateProxyRegion(Context context, String packageName, int userId, String countryIso) {
        if (!isValid(context, packageName) || userId < 0) return false;
        String normalized = countryIso == null ? "" : countryIso.trim().toLowerCase(java.util.Locale.ROOT);
        boolean enabled = normalized.matches("[a-z]{2}");
        if (!enabled) normalized = "";

        Options current = read(context, packageName, userId);
        Options updated = new Options(
                current.changeTelephonyIds,
                current.hideSimOperatorInfo,
                current.changeWifiMac,
                current.hideWifiInfo,
                current.hideDnsServers,
                current.simulateSimProfile,
                current.simPhoneNumber,
                current.simCarrierName,
                current.simCountryIso,
                current.simOperatorCode,
                enabled,
                normalized
        );
        return write(context, packageName, userId, updated);
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
                .remove(prefix + "simulateSimProfile")
                .remove(prefix + "simPhoneNumber")
                .remove(prefix + "simCarrierName")
                .remove(prefix + "simCountryIso")
                .remove(prefix + "simOperatorCode")
                .remove(prefix + "matchProxyRegion")
                .remove(prefix + "proxyCountryIso")
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
                .putBoolean(prefix + "hideDnsServers", options.hideDnsServers)
                .putBoolean(prefix + "simulateSimProfile", options.simulateSimProfile)
                .putString(prefix + "simPhoneNumber", options.simPhoneNumber)
                .putString(prefix + "simCarrierName", options.simCarrierName)
                .putString(prefix + "simCountryIso", options.simCountryIso)
                .putString(prefix + "simOperatorCode", options.simOperatorCode)
                .putBoolean(prefix + "matchProxyRegion", options.matchProxyRegion)
                .putString(prefix + "proxyCountryIso", options.proxyCountryIso);
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
        public final boolean simulateSimProfile;
        public final String simPhoneNumber;
        public final String simCarrierName;
        public final String simCountryIso;
        public final String simOperatorCode;
        public final boolean matchProxyRegion;
        public final String proxyCountryIso;

        public Options(
                boolean changeTelephonyIds,
                boolean hideSimOperatorInfo,
                boolean changeWifiMac,
                boolean hideWifiInfo,
                boolean hideDnsServers,
                boolean simulateSimProfile,
                String simPhoneNumber,
                String simCarrierName,
                String simCountryIso,
                String simOperatorCode,
                boolean matchProxyRegion,
                String proxyCountryIso
        ) {
            this.changeTelephonyIds = changeTelephonyIds;
            this.hideSimOperatorInfo = hideSimOperatorInfo;
            this.changeWifiMac = changeWifiMac;
            this.hideWifiInfo = hideWifiInfo;
            this.hideDnsServers = hideDnsServers;
            this.simulateSimProfile = simulateSimProfile;
            this.simPhoneNumber = simPhoneNumber == null ? "" : simPhoneNumber;
            this.simCarrierName = simCarrierName == null ? "" : simCarrierName;
            this.simCountryIso = simCountryIso == null ? "" : simCountryIso;
            this.simOperatorCode = simOperatorCode == null ? "" : simOperatorCode;
            this.matchProxyRegion = matchProxyRegion && proxyCountryIso != null
                    && proxyCountryIso.trim().matches("(?i)[a-z]{2}");
            this.proxyCountryIso = this.matchProxyRegion
                    ? proxyCountryIso.trim().toLowerCase(java.util.Locale.ROOT)
                    : "";
        }
    }
}
