package top.niunaijun.blackbox.utils;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;

/** Runtime permissions backed by the real host UID, never by a synthetic grant. */
public final class HostRuntimePermissions {
    private HostRuntimePermissions() { }

    public static boolean isCaptureOrMediaPermission(String permission) {
        if (permission == null) return false;
        switch (permission) {
            case "android.permission.CAMERA":
            case "android.permission.RECORD_AUDIO":
            case "android.permission.READ_EXTERNAL_STORAGE":
            case "android.permission.WRITE_EXTERNAL_STORAGE":
            case "android.permission.READ_MEDIA_IMAGES":
            case "android.permission.READ_MEDIA_VIDEO":
            case "android.permission.READ_MEDIA_AUDIO":
            case "android.permission.READ_MEDIA_VISUAL_USER_SELECTED":
            case "android.permission.ACCESS_MEDIA_LOCATION":
                return true;
            default:
                return false;
        }
    }

    public static boolean rewriteCurrentClonePackage(Object[] args) {
        return rewritePackage(args, BActivityThread.getAppPackageName(), BlackBoxCore.getHostPkg());
    }

    /** Does not call PackageManager: this is also used from PackageManager's own hooks. */
    public static boolean rewritePackage(Object[] args, String virtualPackage, String hostPackage) {
        if (args == null || virtualPackage == null || hostPackage == null
                || virtualPackage.equals(hostPackage)) return false;
        for (int i = 0; i < args.length; i++) {
            if (virtualPackage.equals(args[i])) {
                args[i] = hostPackage;
                return true;
            }
        }
        return false;
    }
}
