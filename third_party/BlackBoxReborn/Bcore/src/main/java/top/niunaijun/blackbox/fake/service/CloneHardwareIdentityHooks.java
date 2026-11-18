package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.content.Intent;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.CloneHardwareIdentityStore;

/** Applies clone-scoped hardware details to framework values exposed to guest apps. */
public final class CloneHardwareIdentityHooks {
    private CloneHardwareIdentityHooks() {
    }

    public static boolean shouldRandomizeBattery() {
        String packageName = BActivityThread.getAppPackageName();
        if (packageName == null) return false;
        return CloneHardwareIdentityStore.read(
                BlackBoxCore.getContext(), packageName, BActivityThread.getUserId()).randomizeBatteryLevel;
    }

    public static int batteryLevel() {
        Context context = BlackBoxCore.getContext();
        return VirtualDeviceIdentity.getBatteryLevel(
                context, BActivityThread.getAppPackageName(), BActivityThread.getUserId());
    }

    public static int batteryTemperature() {
        Context context = BlackBoxCore.getContext();
        return VirtualDeviceIdentity.getBatteryTemperature(
                context, BActivityThread.getAppPackageName(), BActivityThread.getUserId());
    }

    public static long batteryChargeCounter() {
        Context context = BlackBoxCore.getContext();
        return VirtualDeviceIdentity.getBatteryChargeCounter(
                context, BActivityThread.getAppPackageName(), BActivityThread.getUserId());
    }

    public static Intent virtualizeBatteryIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())
                || !shouldRandomizeBattery()) {
            return intent;
        }
        Intent virtualIntent = new Intent(intent);
        int scale = Math.max(1, virtualIntent.getIntExtra("scale", 100));
        virtualIntent.putExtra("level", batteryLevel() * scale / 100);
        virtualIntent.putExtra("temperature", batteryTemperature());
        return virtualIntent;
    }
}
