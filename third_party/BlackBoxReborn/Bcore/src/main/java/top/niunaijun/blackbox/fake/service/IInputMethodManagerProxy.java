package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.view.inputmethod.EditorInfo;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;

/** Pair EditorInfo's caller package with Clony's real Binder UID. */
public final class IInputMethodManagerProxy extends BinderInvocationStub {
    public IInputMethodManagerProxy() {
        super(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    @Override protected Object getWho() {
        try {
            return Class.forName("com.android.internal.view.IInputMethodManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null,
                            BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot obtain input-method service", error);
        }
    }

    @Override protected void inject(Object base, Object proxy) {
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
        Object manager = BlackBoxCore.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        try {
            Field field = manager.getClass().getDeclaredField("mService");
            field.setAccessible(true);
            field.set(manager, proxy);
        } catch (NoSuchFieldException ignored) {
            // Newer releases use only the global invoker cache.
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot update input-method instance", error);
        }
        try {
            Class<?> invoker = Class.forName("android.view.inputmethod.IInputMethodManagerGlobalInvoker");
            boolean cacheReplaced = false;
            for (Field field : invoker.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && "com.android.internal.view.IInputMethodManager".equals(
                        field.getType().getName())) {
                    field.setAccessible(true);
                    field.set(null, proxy);
                    cacheReplaced = true;
                }
            }
            if (!cacheReplaced) {
                top.niunaijun.blackbox.utils.Slog.i("IInputMethodManagerProxy",
                        "No global input-method cache field; ServiceManager cache proxy remains active");
            }
        } catch (ClassNotFoundException ignored) {
            // Older releases use InputMethodManager.mService.
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot update input-method global cache", error);
        }
    }

    @Override public boolean isBadEnv() { return false; }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[] forwarded = args == null ? null : args.clone();
        if (forwarded != null) for (int i = 0; i < forwarded.length; i++) {
            if (forwarded[i] instanceof EditorInfo) {
                forwarded[i] = forHost((EditorInfo) forwarded[i], BlackBoxCore.getHostPkg());
            }
        }
        try { return method.invoke(getBase(), forwarded); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    public static EditorInfo forHost(EditorInfo original, String hostPackage) {
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            EditorInfo copy = EditorInfo.CREATOR.createFromParcel(parcel);
            copy.packageName = hostPackage;
            return copy;
        } finally { parcel.recycle(); }
    }
}
