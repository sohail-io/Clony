package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores an optional model name override for each virtual app copy. */
public final class CloneDeviceModelStore {
    private static final String PREFERENCES = "clone_device_models";
    private static final String KEY_PREFIX = "device_model:";
    public static final int MAX_LENGTH = 64;

    private CloneDeviceModelStore() {
    }

    public static String read(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return null;
        }
        String model = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(key(packageName, userId), null);
        return model == null || model.isEmpty() ? null : model;
    }

    public static boolean write(Context context, String packageName, int userId, String model) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        if (model != null && !model.isEmpty() && !isValid(model)) return false;

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit();
        if (model == null || model.isEmpty()) {
            editor.remove(key(packageName, userId));
        } else {
            editor.putString(key(packageName, userId), model);
        }
        return editor.commit();
    }

    public static boolean isValid(String model) {
        if (model == null || model.isEmpty() || model.length() > MAX_LENGTH) return false;
        for (int i = 0; i < model.length(); i++) {
            char character = model.charAt(i);
            if (character < 0x20 || character > 0x7e) return false;
        }
        return !model.trim().isEmpty();
    }

    private static String key(String packageName, int userId) {
        return KEY_PREFIX + packageName + ":" + userId;
    }
}
