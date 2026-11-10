package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists an optional Android build fingerprint for each virtual app copy. */
public final class CloneFingerprintStore {
    private static final String PREFERENCES = "clone_fingerprints";
    private static final String KEY_PREFIX = "build_fingerprint:";
    public static final int MAX_LENGTH = 91;

    private CloneFingerprintStore() {
    }

    public static String read(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return null;
        }
        String fingerprint = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(key(packageName, userId), null);
        return fingerprint == null || fingerprint.isEmpty() ? null : fingerprint;
    }

    /** Empty values remove the override and restore Clony's default fingerprint. */
    public static boolean write(Context context, String packageName, int userId, String fingerprint) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        if (fingerprint != null && !fingerprint.isEmpty() && !isValid(fingerprint)) {
            return false;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit();
        if (fingerprint == null || fingerprint.isEmpty()) {
            editor.remove(key(packageName, userId));
        } else {
            editor.putString(key(packageName, userId), fingerprint);
        }
        return editor.commit();
    }

    public static boolean isValid(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty() || fingerprint.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < fingerprint.length(); i++) {
            char character = fingerprint.charAt(i);
            if (character < 0x21 || character > 0x7e) return false;
        }
        return true;
    }

    private static String key(String packageName, int userId) {
        return KEY_PREFIX + packageName + ":" + userId;
    }
}
