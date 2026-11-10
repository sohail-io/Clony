package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.fake.service.VirtualDeviceIdentity;
import top.niunaijun.blackbox.utils.compat.SystemPropertiesCompat;

/** Debug-only, value-redacted checks executed inside a virtual app's main process. */
public final class VirtualCloneDiagnostics {
    public static final String TAG = "ClonyCloneProbe";

    private VirtualCloneDiagnostics() {
    }

    public static void start(
            Context cloneContext,
            String packageName,
            String processName,
            int userId,
            String configuredFingerprint,
            boolean fingerprintApplied,
            AppConfig appConfig
    ) {
        Context hostContext = BlackBoxCore.getContext();
        if (cloneContext == null || hostContext == null
                || (hostContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0
                || !Log.isLoggable(TAG, Log.DEBUG)) {
            return;
        }

        Thread probe = new Thread(() -> run(
                cloneContext, hostContext, packageName, processName, userId,
                configuredFingerprint, fingerprintApplied, appConfig), "ClonyCloneProbe");
        probe.setDaemon(true);
        probe.start();
    }

    private static void run(
            Context cloneContext,
            Context hostContext,
            String packageName,
            String processName,
            int userId,
            String configuredFingerprint,
            boolean fingerprintApplied,
            AppConfig appConfig
    ) {
        try {
            String androidId = Settings.Secure.getString(
                    cloneContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            String storedAndroidId = VirtualDeviceIdentity.getAndroidId(
                    hostContext, packageName, userId);
            String propertyFingerprint = SystemPropertiesCompat.get(
                    "ro.build.fingerprint", "");
            String buildFingerprint = Build.FINGERPRINT;
            boolean customFingerprintSet = configuredFingerprint != null
                    && !configuredFingerprint.isEmpty();
            boolean buildPropertyMatch = buildFingerprint != null
                    && buildFingerprint.equals(propertyFingerprint);
            boolean buildMatchesOverride = customFingerprintSet
                    && configuredFingerprint.equals(buildFingerprint);
            boolean propertyMatchesOverride = customFingerprintSet
                    && configuredFingerprint.equals(propertyFingerprint);

            Log.d(TAG, "event=identity_probe"
                    + " package=" + safe(packageName)
                    + " process=" + safe(processName)
                    + " userId=" + userId
                    + " android_id_present=" + (androidId != null && !androidId.isEmpty())
                    + " android_id_format_valid=" + isAndroidId(androidId)
                    + " android_id_matches_clone_store=" + equals(androidId, storedAndroidId)
                    + " android_id_sha256_prefix=" + hashPrefix(androidId)
                    + " fingerprint_apply_succeeded=" + fingerprintApplied
                    + " custom_fingerprint_configured=" + customFingerprintSet
                    + " build_property_match=" + buildPropertyMatch
                    + " build_matches_custom=" + (customFingerprintSet ? buildMatchesOverride : "not_configured")
                    + " property_matches_custom=" + (customFingerprintSet ? propertyMatchesOverride : "not_configured")
                    + " build_fingerprint_sha256_prefix=" + hashPrefix(buildFingerprint)
                    + " property_fingerprint_sha256_prefix=" + hashPrefix(propertyFingerprint));

            logProxyConfiguration(appConfig, packageName, processName, userId);
        } catch (Throwable error) {
            Log.w(TAG, "event=identity_probe_failed"
                    + " package=" + safe(packageName)
                    + " process=" + safe(processName)
                    + " userId=" + userId
                    + " exception=" + error.getClass().getSimpleName());
        }
    }

    private static void logProxyConfiguration(
            AppConfig appConfig, String packageName, String processName, int userId) {
        int type = appConfig == null ? 0 : appConfig.proxyType;
        String host = appConfig == null ? "" : appConfig.proxyHost;
        int port = appConfig == null ? 0 : appConfig.proxyPort;
        boolean configured = type != 0 && host != null && !host.isEmpty()
                && port > 0 && port <= 65535;

        Log.d(TAG, "event=proxy_configuration"
                + " package=" + safe(packageName)
                + " process=" + safe(processName)
                + " userId=" + userId
                + " configured=" + configured
                + " type=" + type
                + " endpoint_or_credentials_logged=false"
                + " tunnel_test=manual_dialog_only");

        if (!configured) {
            Log.d(TAG, "event=proxy_tunnel_probe result=skipped reason=not_configured"
                    + " package=" + safe(packageName)
                    + " userId=" + userId);
        }
    }

    private static boolean equals(String first, String second) {
        return first != null && first.equals(second);
    }

    private static boolean isAndroidId(String value) {
        if (value == null || value.length() != 16) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
    }

    private static String hashPrefix(String value) {
        if (value == null || value.isEmpty()) return "none";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder prefix = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                prefix.append(Character.forDigit((digest[i] >>> 4) & 0x0f, 16));
                prefix.append(Character.forDigit(digest[i] & 0x0f, 16));
            }
            return prefix.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value.replaceAll("[^A-Za-z0-9_.:$-]", "_");
    }
}
