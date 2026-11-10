package top.niunaijun.blackbox.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in, payload-free diagnostics for a virtual app process.
 * Enable with: adb shell setprop log.tag.ClonyVirtualRuntime DEBUG
 */
public final class VirtualRuntimeTrace {
    public static final String TAG = "ClonyVirtualRuntime";
    private static final Set<String> SEEN_EVENTS = ConcurrentHashMap.newKeySet();

    private VirtualRuntimeTrace() {
    }

    public static void event(String event, String packageName, String processName, int userId) {
        if (!isEnabled()) return;
        write(Log.DEBUG, event, packageName, processName, userId, null, null);
    }

    public static void eventOnce(String event, String packageName, String processName, int userId) {
        if (!isEnabled()) return;
        String key = safe(event) + "|" + safe(packageName) + "|" + safe(processName) + "|" + userId;
        if (SEEN_EVENTS.add(key)) {
            write(Log.DEBUG, event, packageName, processName, userId, null, null);
        }
    }

    public static void failure(
            String event,
            String packageName,
            String processName,
            int userId,
            Throwable error
    ) {
        if (!isEnabled()) return;
        String exception = error == null ? "unknown" : error.getClass().getSimpleName();
        write(Log.WARN, event, packageName, processName, userId, "failure", exception);
    }

    public static void failureOnce(
            String event,
            String packageName,
            String processName,
            int userId,
            Throwable error
    ) {
        if (!isEnabled()) return;
        String key = safe(event) + "|" + safe(packageName) + "|" + safe(processName) + "|" + userId;
        if (SEEN_EVENTS.add(key)) {
            failure(event, packageName, processName, userId, error);
        }
    }

    public static void activity(String event, Activity activity, int userId) {
        if (!isEnabled() || activity == null) return;
        String packageName = activity.getPackageName();
        String processName = activity.getClass().getName();
        Log.d(TAG, "event=" + safe(event)
                + " package=" + safe(packageName)
                + " component=" + safe(processName)
                + " userId=" + userId
                + " taskId=" + activity.getTaskId()
                + " pid=" + Process.myPid());
    }

    public static void activityDispatch(String event, String packageName, String componentName,
                                        int userId, boolean accepted) {
        if (!isEnabled()) return;
        Log.d(TAG, "event=" + safe(event)
                + " package=" + safe(packageName)
                + " component=" + safe(componentName)
                + " userId=" + userId
                + " outcome=" + (accepted ? "accepted" : "rejected")
                + " pid=" + Process.myPid());
    }

    public static void registerActivityCallbacks(Application application, int userId) {
        if (!isEnabled() || application == null) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
                VirtualRuntimeTrace.activity("activity_created", activity, userId);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                VirtualRuntimeTrace.activity("activity_started", activity, userId);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                VirtualRuntimeTrace.activity("activity_resumed", activity, userId);
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                VirtualRuntimeTrace.activity("activity_destroyed", activity, userId);
            }
        });
    }

    private static boolean isEnabled() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private static void write(
            int priority,
            String event,
            String packageName,
            String processName,
            int userId,
            String outcome,
            String exceptionType
    ) {
        String line = "event=" + safe(event)
                + " package=" + safe(packageName)
                + " process=" + safe(processName)
                + " userId=" + userId
                + " pid=" + Process.myPid();
        if (outcome != null) line += " outcome=" + safe(outcome);
        if (exceptionType != null) line += " exception=" + safe(exceptionType);
        Log.println(priority, TAG, line);
    }

    private static String safe(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == ':' || c == '$' || c == '-') {
                result.append(c);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }
}
