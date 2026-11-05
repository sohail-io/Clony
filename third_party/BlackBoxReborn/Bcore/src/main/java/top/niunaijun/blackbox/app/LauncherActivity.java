package top.niunaijun.blackbox.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.R;
import top.niunaijun.blackbox.utils.Slog;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;


public class LauncherActivity extends Activity {
    public static final String TAG = "SplashScreen";

    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";
    private boolean isRunning = false;
    private String launchPackageName = "unknown";
    private int launchUserId = -1;

    public static void launch(Intent intent, int userId) {
        try {
            Intent splash = new Intent();
            splash.setClass(BlackBoxCore.getContext(), LauncherActivity.class);
            
            splash.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            splash.putExtra(LauncherActivity.KEY_INTENT, intent);
            splash.putExtra(LauncherActivity.KEY_PKG, intent.getPackage());
            splash.putExtra(LauncherActivity.KEY_USER_ID, userId);
            Log.i(TAG, "clone_launch stage=splash_dispatch package=" + intent.getPackage() +
                " component=" + intent.getComponent() + " userId=" + userId +
                " targetAction=" + intent.getAction() + " targetFlags=0x" + Integer.toHexString(intent.getFlags()));
            BlackBoxCore.getContext().startActivity(splash);
            Log.i(TAG, "clone_launch stage=splash_dispatch_return package=" + intent.getPackage() + " userId=" + userId);
        } catch (Exception e) {
            Log.e(TAG, "clone_launch stage=splash_dispatch_failed userId=" + userId, e);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            
            Intent intent = getIntent();
            if (intent == null) {
                Slog.w(TAG, "Intent is null, finishing activity");
                finish();
                return;
            }
            
            Intent launchIntent = intent.getParcelableExtra(KEY_INTENT);
            String packageName = intent.getStringExtra(KEY_PKG);
            int userId = intent.getIntExtra(KEY_USER_ID, 0);
            launchPackageName = packageName == null ? "unknown" : packageName;
            launchUserId = userId;

            if (launchIntent == null || packageName == null) {
                Slog.w(TAG, "Missing launch intent or package name, finishing activity");
                finish();
                return;
            }

            Log.i(TAG, "clone_launch stage=splash_onCreate package=" + packageName + " userId=" + userId +
                " taskId=" + getTaskId() + " launchIntent=" + launchIntent +
                " elapsedMs=" + android.os.SystemClock.elapsedRealtime());

            
            PackageInfo packageInfo = getPackageInfoWithFallback(packageName, userId);
            
            if (packageInfo == null) {
                Log.w(TAG, "clone_launch stage=package_info_missing package=" + packageName + " userId=" + userId);
                
            } else {
                Log.i(TAG, "clone_launch stage=package_info_ready package=" + packageName + " userId=" + userId);
            }
            
            
            Drawable drawable = null;
            String appName = packageName;
            try {
                if (packageInfo != null && packageInfo.applicationInfo != null) {
                    PackageManager pm = getPackageManager();
                    drawable = pm.getApplicationIcon(packageInfo.applicationInfo);
                    CharSequence label = pm.getApplicationLabel(packageInfo.applicationInfo);
                    if (label != null) appName = label.toString();
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to load app icon or name for " + packageName + ": " + e.getMessage());
            }
            // Resolve presentation from the installed source app; virtual package resources
            // may not be available yet during the first launch.
            try {
                android.content.pm.ApplicationInfo source = getPackageManager().getApplicationInfo(packageName, 0);
                appName = getPackageManager().getApplicationLabel(source).toString();
                drawable = getPackageManager().getApplicationIcon(source);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Keep the virtual package's label and icon when the source was removed.
            }
            setContentView(R.layout.activity_launcher);
            ImageView iconView = findViewById(R.id.iv_icon);
            TextView nameView = findViewById(R.id.tv_app_name);
            if (nameView != null) {
                nameView.setText(appName);
                nameView.setAlpha(0f);
                nameView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(200)
                    .start();
            }
            if (iconView != null && drawable != null) {
                iconView.setImageDrawable(drawable);
                iconView.setScaleX(0.7f);
                iconView.setScaleY(0.7f);
                iconView.setAlpha(0f);
                iconView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(() -> iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start())
                    .start();
            }
            
            
            launchAppAsync(launchIntent, packageName, userId);
            
        } catch (Exception e) {
            Log.e(TAG, "clone_launch stage=splash_onCreate_failed package=" + launchPackageName +
                " userId=" + launchUserId + " taskId=" + getTaskId(), e);
            finish();
        }
    }

    
    private PackageInfo getPackageInfoWithFallback(String packageName, int userId) {
        try {
            
            return BlackBoxCore.getBPackageManager().getPackageInfo(packageName, 0, userId);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to get package info for " + packageName + " (attempt 1): " + e.getMessage());
            
            try {
                
                return BlackBoxCore.getBPackageManager().getPackageInfo(packageName, 
                    android.content.pm.PackageManager.GET_META_DATA, userId);
            } catch (Exception e2) {
                Slog.w(TAG, "Failed to get package info for " + packageName + " (attempt 2): " + e2.getMessage());
                
                try {
                    
                    android.content.pm.ApplicationInfo appInfo = BlackBoxCore.getBPackageManager()
                        .getApplicationInfo(packageName, 0, userId);
                    
                    if (appInfo != null) {
                        
                        PackageInfo fallbackInfo = new PackageInfo();
                        fallbackInfo.packageName = packageName;
                        fallbackInfo.applicationInfo = appInfo;
                        fallbackInfo.versionCode = 1;
                        fallbackInfo.versionName = "1.0";
                        fallbackInfo.firstInstallTime = System.currentTimeMillis();
                        fallbackInfo.lastUpdateTime = System.currentTimeMillis();
                        
                        Slog.d(TAG, "Created fallback PackageInfo for " + packageName);
                        return fallbackInfo;
                    }
                } catch (Exception e3) {
                    Slog.w(TAG, "Failed to get application info for " + packageName + ": " + e3.getMessage());
                }
            }
        }
        
        return null;
    }

    
    private void launchAppAsync(final Intent launchIntent, final String packageName, final int userId) {
        new Thread(() -> {
            try {
                Log.i(TAG, "clone_launch stage=startActivity_thread package=" + packageName + " userId=" + userId +
                    " component=" + launchIntent.getComponent() + " action=" + launchIntent.getAction());
                
                
                Thread.sleep(100);
                
                
                BlackBoxCore.getBActivityManager().startActivity(launchIntent, userId);
                Log.i(TAG, "clone_launch stage=startActivity_return package=" + packageName + " userId=" + userId);
            } catch (Exception e) {
                Log.e(TAG, "clone_launch stage=startActivity_failed package=" + packageName + " userId=" + userId, e);
                
                
                runOnUiThread(() -> {
                    try {
                        
                        Log.e(TAG, "clone_launch stage=startActivity_ui_error package=" + packageName +
                            " userId=" + userId + " message=" + e.getMessage());
                    } catch (Exception uiException) {
                        Slog.e(TAG, "Error showing error message", uiException);
                    }
                });
            }
        }, "AppLaunchThread").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = true;
        logLifecycle("onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logLifecycle("onResume");
        if (isRunning) {
            Log.i(TAG, "clone_launch stage=splash_finish reason=resumed_after_pause package=" + launchPackageName +
                " userId=" + launchUserId + " taskId=" + getTaskId());
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        logLifecycle("onStart");
    }

    @Override
    protected void onStop() {
        logLifecycle("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logLifecycle("onDestroy");
        super.onDestroy();
    }

    private void logLifecycle(String event) {
        Log.i(TAG, "clone_launch stage=splash_" + event + " package=" + launchPackageName +
            " userId=" + launchUserId + " taskId=" + getTaskId() + " isRunning=" + isRunning +
            " finishing=" + isFinishing() + " elapsedMs=" + android.os.SystemClock.elapsedRealtime());
    }
}
