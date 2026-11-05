package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;

/** Clipboard Binder calls must identify the real Android caller, including paste-menu checks. */
public final class IClipboardManagerProxy extends BinderInvocationStub {
    public IClipboardManagerProxy() {
        super(BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
    }

    @Override protected Object getWho() {
        try {
            Class<?> stub = Class.forName("android.content.IClipboard$Stub");
            return stub.getMethod("asInterface", IBinder.class).invoke(null,
                    BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot obtain clipboard service", error);
        }
    }

    @Override protected void inject(Object base, Object proxy) {
        replaceSystemService(Context.CLIPBOARD_SERVICE);
        Object manager = BlackBoxCore.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        // Cover both pre-O static caches and modern per-context ClipboardManager caches.
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (!IInterface.class.isAssignableFrom(field.getType())) continue;
            if (!"mService".equals(field.getName()) && !"sService".equals(field.getName())) continue;
            try {
                field.setAccessible(true);
                field.set(Modifier.isStatic(field.getModifiers()) ? null : manager, proxy);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot install clipboard caller identity", error);
            }
        }
    }

    @Override public boolean isBadEnv() { return false; }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[] forwarded = args == null ? null : args.clone();
        rewriteCaller(forwarded, BlackBoxCore.getHostPkg(), BlackBoxCore.getBUid(), BlackBoxCore.getHostUid());
        try {
            return method.invoke(getBase(), forwarded);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public static void rewriteCaller(Object[] args, String hostPackage, int virtualUid, int hostUid) {
        if (args == null) return;
        boolean packageReplaced = false;
        for (int i = 0; i < args.length; i++) {
            if (!packageReplaced && args[i] instanceof String) {
                args[i] = hostPackage;
                packageReplaced = true;
            } else if (args[i] instanceof Integer && (Integer) args[i] == virtualUid && virtualUid != hostUid) {
                args[i] = hostUid;
            }
        }
    }
}
