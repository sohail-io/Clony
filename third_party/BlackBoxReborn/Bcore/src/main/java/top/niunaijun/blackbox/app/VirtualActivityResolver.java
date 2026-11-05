package top.niunaijun.blackbox.app;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;

/** Helpers for keeping cloned-app activity resolution inside its virtual package. */
public final class VirtualActivityResolver {
    private VirtualActivityResolver() { }

    public static boolean targetsVirtualPackage(Intent intent, String virtualPackage) {
        if (intent == null || virtualPackage == null) return false;
        ComponentName component = intent.getComponent();
        return component != null && virtualPackage.equals(component.getPackageName());
    }

    public static ActivityInfo attachVirtualApplication(ActivityInfo source,
                                                        ApplicationInfo virtualApplication) {
        if (source == null || virtualApplication == null
                || !virtualApplication.packageName.equals(source.packageName)) return null;
        ActivityInfo virtual = new ActivityInfo(source);
        virtual.applicationInfo = new ApplicationInfo(virtualApplication);
        return virtual;
    }

    public static ActivityNotFoundException missingVirtualActivity(Intent intent, int userId) {
        ComponentName component = intent == null ? null : intent.getComponent();
        String name = component == null ? String.valueOf(intent) : component.flattenToShortString();
        return new ActivityNotFoundException("No virtual activity " + name
                + " is registered for user " + userId);
    }
}
