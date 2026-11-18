package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;

/** Virtualizes BatteryManager properties while preserving framework permission checks. */
public class BatteryPropertiesProxy extends BinderInvocationStub {
    private static final String SERVICE_NAME = "batteryproperties";
    private static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    private static final int BATTERY_PROPERTY_CAPACITY = 4;

    public BatteryPropertiesProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(SERVICE_NAME);
        if (binder == null) return null;
        try {
            Class<?> stub = Class.forName("android.os.IBatteryPropertiesRegistrar$Stub");
            Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            return asInterface.invoke(null, binder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getProperty")
    public static class GetProperty extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(who, args);
            if (!(result instanceof Integer) || ((Integer) result) != 0 || args == null
                    || args.length < 2 || !(args[0] instanceof Integer)
                    || !CloneHardwareIdentityHooks.shouldRandomizeBattery()) {
                return result;
            }

            int property = (Integer) args[0];
            long value;
            if (property == BATTERY_PROPERTY_CAPACITY) {
                value = CloneHardwareIdentityHooks.batteryLevel();
            } else if (property == BATTERY_PROPERTY_CHARGE_COUNTER) {
                value = CloneHardwareIdentityHooks.batteryChargeCounter();
            } else {
                return result;
            }
            try {
                setBatteryPropertyLong(args[1], value);
            } catch (Throwable ignored) {
                // If this Android release blocks the hidden BatteryProperty mutator,
                // keep the framework's real value instead of breaking the app query.
            }
            return result;
        }

        private void setBatteryPropertyLong(Object property, long value) throws Throwable {
            if (property == null) return;
            Method setLong = property.getClass().getDeclaredMethod("setLong", long.class);
            setLong.setAccessible(true);
            setLong.invoke(property, value);
        }
    }
}
