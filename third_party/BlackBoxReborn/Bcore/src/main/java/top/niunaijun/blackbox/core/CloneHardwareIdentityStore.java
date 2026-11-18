package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores clone-scoped CPU/GPU visibility and battery randomization controls. */
public final class CloneHardwareIdentityStore {
    private static final String PREFERENCES = "clone_hardware_identity_v1";
    private static final String KEY_PREFIX = "hardware_identity:";

    private CloneHardwareIdentityStore() {
    }

    public static Options defaults() {
        return new Options(false, false);
    }

    public static Options readTemplate(Context context, String packageName) {
        if (!isValid(context, packageName)) return defaults();
        return readOptions(preferences(context), templateKey(packageName), defaults());
    }

    public static Options read(Context context, String packageName, int userId) {
        if (!isValid(context, packageName) || userId < 0) return defaults();
        return readOptions(preferences(context), cloneKey(packageName, userId), defaults());
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
                .remove(prefix + "hideCpuGpuInfo")
                .remove(prefix + "randomizeBatteryLevel")
                .commit();
    }

    private static Options readOptions(SharedPreferences preferences, String prefix, Options defaults) {
        return new Options(
                preferences.getBoolean(prefix + "hideCpuGpuInfo", defaults.hideCpuGpuInfo),
                preferences.getBoolean(prefix + "randomizeBatteryLevel", defaults.randomizeBatteryLevel)
        );
    }

    private static SharedPreferences.Editor writeOptions(
            SharedPreferences.Editor editor, String prefix, Options options) {
        return editor
                .putBoolean(prefix + "hideCpuGpuInfo", options.hideCpuGpuInfo)
                .putBoolean(prefix + "randomizeBatteryLevel", options.randomizeBatteryLevel);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static boolean isValid(Context context, String packageName) {
        return context != null && packageName != null && packageName.matches("[A-Za-z0-9_.]+");
    }

    private static String templateKey(String packageName) {
        return KEY_PREFIX + "template:" + packageName + ":";
    }

    private static String cloneKey(String packageName, int userId) {
        return KEY_PREFIX + "clone:" + packageName + ":" + userId + ":";
    }

    public static final class Options {
        public final boolean hideCpuGpuInfo;
        public final boolean randomizeBatteryLevel;

        public Options(boolean hideCpuGpuInfo, boolean randomizeBatteryLevel) {
            this.hideCpuGpuInfo = hideCpuGpuInfo;
            this.randomizeBatteryLevel = randomizeBatteryLevel;
        }
    }
}
