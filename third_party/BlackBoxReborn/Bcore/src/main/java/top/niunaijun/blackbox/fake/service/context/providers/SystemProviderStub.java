package top.niunaijun.blackbox.fake.service.context.providers;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.IInterface;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.service.VirtualDeviceIdentity;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;


public class SystemProviderStub extends ClassInvocationStub implements BContentProvider {
    private IInterface mBase;

    @Override
    public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
        mBase = contentProviderProxy;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        
        String methodName = method.getName();
        // Queries and file opens need the same real Binder identity as call().
        // Hidden AttributionSource state fields are filtered on Android 17.
        fixCallAttribution(args);
        
        
        
        if ("call".equals(methodName)) {
            try {
                Object result = method.invoke(mBase, args);
                return replaceAndroidIdCallResult(args, result);
            } catch (Throwable error) {
                Throwable cause = error;
                while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String message = cause.getMessage();
                if (cause instanceof SecurityException && message != null
                        && message.contains("Calling uid")
                        && message.contains("doesn't match source uid")) {
                    Slog.w("SystemProviderStub", "Settings provider rejected virtual attribution; "
                            + "handling " + describeCall(args) + " through the safe fallback");
                    return replaceAndroidIdCallResult(args, rejectedCallResult(args));
                }
                throw cause;
            }
        }

        boolean androidIdQuery = "query".equals(methodName) && isAndroidIdQuery(args);
        
        
        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg instanceof String) {
                String authority = (String) arg;
                
                if (!isSystemProviderAuthority(authority)) {
                    args[0] = BlackBoxCore.getHostPkg();
                }
            } else if (arg != null) {
                Class<?> attrSourceClass = BRAttributionSource.getRealClass();
                
                if (attrSourceClass != null && arg.getClass().getName().equals(attrSourceClass.getName())) {
                    ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                }
            }
        }
        Object result = method.invoke(mBase, args);
        return androidIdQuery ? replaceAndroidIdCursor(result) : result;
    }

    private Object replaceAndroidIdCallResult(Object[] args, Object result) {
        if (!(result instanceof Bundle) || !isAndroidIdCall(args)) return result;
        Bundle providerResult = (Bundle) result;
        String packageName = getVirtualPackageName();
        String androidId = VirtualDeviceIdentity.getAndroidId(
                getIdentityContext(), packageName, getVirtualUserId());
        if (androidId == null) return result;

        Bundle virtualResult = new Bundle(providerResult);
        virtualResult.putString("value", androidId);
        logIdentityHook("settings_secure_android_id", packageName, getVirtualUserId());
        return virtualResult;
    }

    private Object replaceAndroidIdCursor(Object result) {
        if (!(result instanceof Cursor)) return result;
        String packageName = getVirtualPackageName();
        String androidId = VirtualDeviceIdentity.getAndroidId(
                getIdentityContext(), packageName, getVirtualUserId());
        if (androidId == null) return result;

        Cursor cursor = (Cursor) result;
        int valueColumn = cursor.getColumnIndex("value");
        if (valueColumn < 0) return result;
        int nameColumn = cursor.getColumnIndex("name");
        logIdentityHook("settings_secure_android_id", packageName, getVirtualUserId());
        return new CursorWrapper(cursor) {
            @Override
            public String getString(int columnIndex) {
                if (columnIndex == valueColumn && getPosition() >= 0 && getPosition() < getCount()) {
                    if (nameColumn < 0 || "android_id".equalsIgnoreCase(super.getString(nameColumn))) {
                        return androidId;
                    }
                }
                return super.getString(columnIndex);
            }
        };
    }

    private boolean isAndroidIdCall(Object[] args) {
        if (args == null) return false;
        boolean secureRead = false;
        boolean androidId = false;
        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                secureRead |= "GET_secure".equals(value);
                androidId |= "android_id".equalsIgnoreCase(value);
            } else if (arg instanceof String[]) {
                for (String value : (String[]) arg) {
                    androidId |= value != null && "android_id".equalsIgnoreCase(value);
                }
            }
        }
        return secureRead && androidId;
    }

    private boolean isAndroidIdQuery(Object[] args) {
        if (args == null) return false;
        boolean secureSettings = false;
        boolean androidId = false;
        for (Object arg : args) {
            if (arg instanceof Uri) {
                Uri uri = (Uri) arg;
                if ("settings".equals(uri.getAuthority())) {
                    for (String segment : uri.getPathSegments()) {
                        secureSettings |= "secure".equalsIgnoreCase(segment);
                        androidId |= "android_id".equalsIgnoreCase(segment);
                    }
                }
            } else if (arg instanceof String) {
                androidId |= ((String) arg).toLowerCase(java.util.Locale.ROOT).contains("android_id");
            } else if (arg instanceof String[]) {
                for (String value : (String[]) arg) {
                    androidId |= value != null && "android_id".equalsIgnoreCase(value);
                }
            } else if (arg instanceof Bundle) {
                Bundle queryArgs = (Bundle) arg;
                String[] selectionArgs = queryArgs.getStringArray("android:query-arg-sql-selection-args");
                if (selectionArgs != null) {
                    for (String value : selectionArgs) {
                        androidId |= value != null && "android_id".equalsIgnoreCase(value);
                    }
                }
                String selection = queryArgs.getString("android:query-arg-sql-selection");
                androidId |= selection != null
                        && selection.toLowerCase(java.util.Locale.ROOT).contains("android_id");
            }
        }
        return secureSettings && androidId;
    }

    protected String getVirtualPackageName() {
        String packageName = BActivityThread.getAppPackageName();
        if (packageName != null) return packageName;
        Context context = getIdentityContext();
        return context == null ? null : context.getPackageName();
    }

    protected int getVirtualUserId() {
        return BActivityThread.getUserId();
    }

    protected Context getIdentityContext() {
        Context context = BlackBoxCore.getContext();
        if (context != null) return context;
        return BActivityThread.getApplication();
    }

    private void logIdentityHook(String hook, String packageName, int userId) {
        Slog.d("SystemProviderStub", "virtual_identity hook=" + hook
                + " package=" + packageName + " userId=" + userId + " source=clone_seed");
    }

    static Bundle rejectedCallResult(Object[] args) {
        Bundle result = new Bundle();
        if (args != null) {
            for (Object arg : args) {
                if ("LIST_config".equals(arg)) {
                    // Settings.NameValueCache iterates this map without a null
                    // check. An empty Bundle crashes DeviceConfig/Autofill.
                    result.putSerializable("value", new HashMap<String, String>());
                    break;
                }
            }
        }
        return result;
    }

    private void fixCallAttribution(Object[] args) {
        if (args == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ exposes AttributionSource.Builder as a public API. The
            // runtime's virtual UID cannot be sent over Binder as the caller UID:
            // system providers validate it against the real Clony process UID.
            // Rebuild the direct call argument instead of mutating hidden fields,
            // which Android 16 blocks through hidden-API reflection.
            try {
                Class<?> sourceClass = Class.forName("android.content.AttributionSource");
                Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
                // Process.myUid() is virtualized in a clone process. Binder sees
                // Clony's real host uid, so the source passed to the system
                // provider must use the same host identity.
                Object builder = builderClass.getConstructor(int.class)
                        .newInstance(BlackBoxCore.getHostUid());
                builderClass.getMethod("setPackageName", String.class)
                        .invoke(builder, BlackBoxCore.getHostPkg());
                Object hostSource = builderClass.getMethod("build").invoke(builder);

                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && sourceClass.isInstance(args[i])) {
                        args[i] = hostSource;
                        Slog.d("SystemProviderStub", "Replaced settings AttributionSource at arg " + i);
                    }
                }
                return;
            } catch (Throwable error) {
                // Fall back to the legacy state repair on Android versions or
                // provider signatures that do not expose the public builder.
                Slog.w("SystemProviderStub", "Could not rebuild settings AttributionSource: "
                        + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

        Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && attributionSourceClass != null
                    && arg.getClass().getName().equals(attributionSourceClass.getName())) {
                ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
            }
        }
    }

    private String describeCall(Object[] args) {
        if (args == null) {
            return "unknown method";
        }
        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                if (value.startsWith("GET_") || value.startsWith("PUT_")
                        || value.startsWith("LIST_")) {
                    return value;
                }
            }
        }
        return "provider call";
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        
        return authority.equals("settings") || 
               authority.equals("media") || 
               authority.equals("downloads") || 
               authority.equals("contacts") || 
               authority.equals("call_log") || 
               authority.equals("telephony") || 
               authority.equals("calendar") || 
               authority.equals("browser") || 
               authority.equals("user_dictionary") || 
               authority.equals("applications") ||
               authority.startsWith("com.android.") ||
               authority.startsWith("android.");
    }
}
