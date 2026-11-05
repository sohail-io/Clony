package top.niunaijun.blackbox.entity.pm;

/** Encodes a request to clone an app that is already installed on the host device. */
public final class InstalledPackageRequest {
    private static final String PREFIX = "clony-installed-package:";

    private InstalledPackageRequest() {
    }

    public static String encode(String packageName) {
        return PREFIX + packageName;
    }

    public static String decode(String request) {
        if (request == null || !request.startsWith(PREFIX)) return null;
        String packageName = request.substring(PREFIX.length());
        return packageName.isEmpty() ? null : packageName;
    }
}
