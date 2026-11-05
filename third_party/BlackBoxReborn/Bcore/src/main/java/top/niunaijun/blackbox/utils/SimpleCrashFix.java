package top.niunaijun.blackbox.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Map;

import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;


public class SimpleCrashFix {
    private static final String TAG = "SimpleCrashFix";
    private static boolean sIsInstalled = false;
    private static volatile WeakReference<Activity> sLastVirtualActivity = new WeakReference<>(null);

    public static void onVirtualActivityResumed(Activity activity) {
        sLastVirtualActivity = new WeakReference<>(activity);
    }

    public static void onVirtualActivityPaused(Activity activity) {
        // Retain the authorization activity while a Google activity is in front.
    }

    public static void onVirtualActivityDestroyed(Activity activity) {
        if (sLastVirtualActivity.get() == activity) {
            sLastVirtualActivity = new WeakReference<>(null);
        }
    }

    
    public static void installSimpleFix() {
        if (sIsInstalled) {
            Slog.d(TAG, "Simple crash fix already installed");
            return;
        }
        
        try {
            Slog.d(TAG, "Installing essential crash fix...");
            
            
            installGlobalExceptionHandler();
            
            
            installContextWrapperHook();
            
            sIsInstalled = true;
            Slog.d(TAG, "Essential crash fix installed successfully");
        } catch (Exception e) {
            Slog.e(TAG, "Failed to install essential crash fix: " + e.getMessage(), e);
        }
    }
    
    
    private static void installGlobalExceptionHandler() {
        top.niunaijun.blackbox.core.CrashHandler.create();
    }

    /** Called at a failed auth-service boundary while the main looper is still healthy. */
    public static void cancelGoogleSignIn(String reason) {
        Slog.w(TAG, "google_sign_in_failed userId=" + BActivityThread.getUserId() + " reason=" + reason);
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = findAuthorizationActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            activity.setResult(Activity.RESULT_CANCELED);
            Toast.makeText(activity, "Google sign-in couldn't start. Please try again.", Toast.LENGTH_LONG).show();
            activity.finish();
        });
    }

    private static Activity findAuthorizationActivity() {
        Activity tracked = sLastVirtualActivity.get();
        if (tracked != null && tracked.getClass().getName().endsWith("AuthorizeActivity")) {
            return tracked;
        }
        try {
            Map<android.os.IBinder, Object> activities =
                    BRActivityThread.get(BlackBoxCore.mainThread()).mActivities();
            if (activities != null) {
                Activity candidate = null;
                for (Object record : activities.values()) {
                    Activity activity = BRActivityThreadActivityClientRecord.get(record).activity();
                    if (activity == null || activity.isFinishing()
                            || !activity.getClass().getName().endsWith("AuthorizeActivity")) {
                        continue;
                    }
                    candidate = activity;
                    Boolean topResumed = BRActivityThreadActivityClientRecord.get(record)
                            .isTopResumedActivity();
                    if (Boolean.TRUE.equals(topResumed)) {
                        return activity;
                    }
                }
                if (candidate != null) return candidate;
            }
        } catch (Throwable error) {
            Slog.w(TAG, "Could not inspect virtual activity records: " + error.getMessage());
        }
        return null;
    }
    
    
    private static void installContextWrapperHook() {
        try {
            
            ContextWrapperHook.installHook();
            Slog.d(TAG, "Context wrapper hook installed");
        } catch (Exception e) {
            Slog.e(TAG, "Failed to install context wrapper hook: " + e.getMessage(), e);
        }
    }
    
    
}
