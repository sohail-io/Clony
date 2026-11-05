package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Convert unsupported privileged provider filters into the normal no-credential result.
 * This keeps the platform's provider restriction intact and lets virtual apps fall back to
 * their regular sign-in UI instead of crashing on CredentialManager's permission check.
 */
public final class ICredentialManagerProxy extends BinderInvocationStub {
    private static final String TAG = "CredentialManagerStub";
    private static final String SERVICE_NAME = "credential";
    private static final String ALLOWED_PROVIDERS_PERMISSION =
            "android.permission.CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS";
    private static final String NO_CREDENTIAL_TYPE =
            "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL";
    private static volatile Object sCredentialManagerInterface;

    public ICredentialManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        try {
            Class<?> stub = Class.forName("android.credentials.ICredentialManager$Stub");
            return stub.getMethod("asInterface", IBinder.class).invoke(null,
                    BRServiceManager.get().getService(SERVICE_NAME));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot obtain credential manager service", error);
        }
    }

    @Override
    protected void inject(Object base, Object proxy) {
        sCredentialManagerInterface = proxy;
        replaceSystemService(SERVICE_NAME);
        if (!installForContext(BlackBoxCore.getContext())) {
            throw new IllegalStateException("Could not route host CredentialManager through proxy");
        }
    }

    /** Installs the binder proxy into the specific process/context manager cache. */
    public static boolean installForContext(Context context) {
        if (context == null || sCredentialManagerInterface == null) return false;
        Object manager = context.getSystemService(SERVICE_NAME);
        if (manager == null) {
            Slog.w(TAG, "CredentialManager unavailable for context package="
                    + context.getPackageName());
            return false;
        }

        AtomicBoolean serviceReplaced = new AtomicBoolean();
        for (Class<?> type = manager.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!IInterface.class.isAssignableFrom(field.getType())) continue;
                if (!"mService".equals(field.getName()) && !"sService".equals(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    Object target = Modifier.isStatic(field.getModifiers()) ? null : manager;
                    field.set(target, sCredentialManagerInterface);
                    if (field.get(target) == sCredentialManagerInterface) serviceReplaced.set(true);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Slog.w(TAG, "Could not patch CredentialManager context cache: "
                            + error.getClass().getSimpleName());
                }
            }
        }
        if (!serviceReplaced.get()) {
            Slog.w(TAG, "CredentialManager context cache did not expose its binder package="
                    + context.getPackageName());
            return false;
        }
        Slog.i(TAG, "CredentialManager proxy installed for context package="
                + context.getPackageName() + " user=" + BActivityThread.getUserId());
        return true;
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("executeGetCredential")
    public static class ExecuteGetCredential extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = BActivityThread.getAppPackageName();
            if (packageName == null || args == null || args.length < 2) {
                return method.invoke(who, args);
            }

            Slog.d(TAG, "CredentialManager route method=" + method.getName()
                    + " package=" + packageName + " user=" + BActivityThread.getUserId());
            if (completeNoCredentialFallback(args[0], args[1], true,
                    hasAllowedProvidersPermission())) {
                Slog.i(TAG, "CredentialManager fallback=NO_CREDENTIAL package=" + packageName
                        + " user=" + BActivityThread.getUserId());
                return null;
            }
            return method.invoke(who, args);
        }

        /** Returns true when a clone request was completed locally with the normal empty result. */
        public static boolean completeNoCredentialFallback(Object request, Object callback,
                                                            boolean virtualCaller,
                                                            boolean hasPermission) throws Throwable {
            if (!virtualCaller || hasPermission) return false;
            Boolean filtered = hasAllowedProviders(request);
            // An unreadable new request shape must not be sent to Android with unknown privileged
            // provider fields. Treat it as unavailable and let the app use its regular login UI.
            if (Boolean.FALSE.equals(filtered)) return false;
            deliverNoCredential(callback);
            return true;
        }

        private static Boolean hasAllowedProviders(Object request) {
            if (request == null) return null;
            try {
                Object options = request.getClass().getMethod("getCredentialOptions").invoke(request);
                if (!(options instanceof Iterable)) return null;
                for (Object option : (Iterable<?>) options) {
                    if (option == null) continue;
                    Object providers = option.getClass().getMethod("getAllowedProviders").invoke(option);
                    if (providers instanceof Collection && !((Collection<?>) providers).isEmpty()) {
                        return true;
                    }
                    if (providers instanceof Iterable && ((Iterable<?>) providers).iterator().hasNext()) {
                        return true;
                    }
                }
                return false;
            } catch (ReflectiveOperationException error) {
                Slog.w(TAG, "Could not inspect credential provider filter: " + error.getClass().getSimpleName());
                return null;
            }
        }

        private static boolean hasAllowedProvidersPermission() {
            return BlackBoxCore.getContext().checkSelfPermission(ALLOWED_PROVIDERS_PERMISSION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        private static void deliverNoCredential(Object callback) throws Throwable {
            try {
                Method onError = callback.getClass().getMethod("onError", String.class, String.class);
                onError.invoke(callback, NO_CREDENTIAL_TYPE,
                        "No credential providers are available for this request.");
            } catch (InvocationTargetException error) {
                throw error.getCause();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot return credential fallback to clone", error);
            }
        }
    }
}
