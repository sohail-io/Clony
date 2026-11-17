package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Stores build-property overrides for a clone and the next copies of an app. */
public final class CloneBuildPropsStore {
    private static final String PREFERENCES = "clone_build_props";
    private static final String KEY_PREFIX = "build_props:";
    private static final int MAX_VALUE_LENGTH = 91;

    private static final String[] PROPERTY_NAMES = {
            "deviceName",
            "manufacturer",
            "brand",
            "model",
            "product",
            "device",
            "board",
            "radio",
            "bootloader",
            "fingerprint",
            "build",
            "host",
            "time",
            "osVersion",
            "release",
            "sdkInt"
    };

    private CloneBuildPropsStore() {
    }

    public static String[] propertyNames() {
        return PROPERTY_NAMES.clone();
    }

    public static String[] read(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return emptyValues();
        }
        return readValues(context, cloneKey(packageName, userId));
    }

    public static String[] readTemplate(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return emptyValues();
        }
        return readValues(context, templateKey(packageName));
    }

    public static boolean write(Context context, String packageName, int userId, String[] values) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0
                || !isValid(values)) {
            return false;
        }
        return writeValues(context, cloneKey(packageName, userId), values);
    }

    public static boolean writeTemplate(Context context, String packageName, String[] values) {
        if (context == null || packageName == null || packageName.isEmpty() || !isValid(values)) {
            return false;
        }
        return writeValues(context, templateKey(packageName), values);
    }

    public static boolean delete(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit();
        String keyPrefix = cloneKey(packageName, userId) + ":";
        for (String propertyName : PROPERTY_NAMES) {
            editor.remove(keyPrefix + propertyName);
        }
        return editor.commit();
    }

    public static boolean isValid(String[] values) {
        if (values == null || values.length != PROPERTY_NAMES.length) return false;
        for (int index = 0; index < values.length; index++) {
            String value = values[index] == null ? "" : values[index];
            if (value.length() > MAX_VALUE_LENGTH) return false;
            for (int characterIndex = 0; characterIndex < value.length(); characterIndex++) {
                char character = value.charAt(characterIndex);
                if (character < 0x20 || character > 0x7e) return false;
                if (index == 9 && Character.isWhitespace(character)) return false;
            }
            if (index == 12 && !value.isEmpty()) {
                try {
                    if (Long.parseLong(value) < 0) return false;
                } catch (NumberFormatException invalidTime) {
                    return false;
                }
            }
            if (index == 15 && !value.isEmpty()) {
                try {
                    int sdkInt = Integer.parseInt(value);
                    if (sdkInt < 1) return false;
                } catch (NumberFormatException invalidSdkInt) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String[] emptyValues() {
        return new String[PROPERTY_NAMES.length];
    }

    private static String[] readValues(Context context, String keyPrefix) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String[] values = new String[PROPERTY_NAMES.length];
        for (int index = 0; index < PROPERTY_NAMES.length; index++) {
            values[index] = preferences.getString(keyPrefix + ":" + PROPERTY_NAMES[index], "");
        }
        // Replace test-profile placeholders saved by older app versions with
        // values reported by the actual device before applying the overrides.
        if (values[7].startsWith("TEST-RADIO-")) {
            String radioVersion = Build.getRadioVersion();
            values[7] = radioVersion == null ? "" : radioVersion;
        }
        if (values[8].startsWith("TEST-BOOTLOADER-")) {
            values[8] = Build.BOOTLOADER == null ? "" : Build.BOOTLOADER;
        }
        if ("clony-test-build-host".equals(values[11]) || "nubia-build-host".equals(values[11])) {
            values[11] = Build.HOST == null ? "" : Build.HOST;
        }
        if (values[13].endsWith("-test") || "4.4".equals(values[13])) {
            String osVersion = System.getProperty("os.version");
            values[13] = osVersion == null ? "" : osVersion;
        }
        return values;
    }

    private static boolean writeValues(Context context, String keyPrefix, String[] values) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit();
        boolean hasValues = false;
        for (int index = 0; index < PROPERTY_NAMES.length; index++) {
            String value = values[index] == null ? "" : values[index].trim();
            String key = keyPrefix + ":" + PROPERTY_NAMES[index];
            if (value.isEmpty()) {
                editor.remove(key);
            } else {
                editor.putString(key, value);
                hasValues = true;
            }
        }
        if (!hasValues) {
            for (String propertyName : PROPERTY_NAMES) {
                editor.remove(keyPrefix + ":" + propertyName);
            }
        }
        return editor.commit();
    }

    private static String cloneKey(String packageName, int userId) {
        return KEY_PREFIX + "clone:" + packageName + ":" + userId;
    }

    private static String templateKey(String packageName) {
        return KEY_PREFIX + "template:" + packageName;
    }
}
