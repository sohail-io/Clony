package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import top.niunaijun.blackbox.fake.service.GmsProxy;
import top.niunaijun.blackbox.utils.SimpleCrashFix;
import top.niunaijun.blackbox.utils.Slog;

public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new ConcurrentHashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;
    private final boolean mVirtualRoute;
    private final boolean mGoogleSignIn;

    private ServiceConnectionDelegate(IServiceConnection connection, Intent intent, boolean virtualRoute) {
        mConn = connection;
        mComponentName = intent.getComponent();
        mVirtualRoute = virtualRoute;
        mGoogleSignIn = "com.google.android.gms.auth.api.signin.service.START".equals(intent.getAction());
    }

    public static ServiceConnectionDelegate getDelegate(IBinder binder) {
        return sServiceConnectDelegate.get(binder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent, boolean virtualRoute) {
        IBinder binder = base.asBinder();
        ServiceConnectionDelegate delegate = new ServiceConnectionDelegate(base, intent, virtualRoute);
        sServiceConnectDelegate.put(binder, delegate);
        try {
            binder.linkToDeath(() -> sServiceConnectDelegate.remove(binder, delegate), 0);
        } catch (RemoteException error) {
            sServiceConnectDelegate.remove(binder, delegate);
        }
        return delegate;
    }

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        deliver(name, service, null, false, usesSessionLayout());
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        ComponentName name = data.readInt() == 0 ? null : ComponentName.CREATOR.createFromParcel(data);
        IBinder service = data.readStrongBinder();
        // Preserve the phone's actual wire layout, including IBinderSession on
        // newer builds. API level alone doesn't distinguish all Android 16 builds.
        boolean sessionLayout = data.dataAvail() > 4;
        IBinder session = sessionLayout ? data.readStrongBinder() : null;
        boolean dead = data.dataAvail() >= 4 && data.readInt() != 0;
        deliver(name, service, session, dead, sessionLayout);
        if (reply != null) reply.writeNoException();
        return true;
    }

    public static boolean usesSessionLayout() throws RemoteException {
        for (java.lang.reflect.Method method : IServiceConnection.class.getDeclaredMethods()) {
            if ("connected".equals(method.getName())) return method.getParameterTypes().length == 4;
        }
        throw new RemoteException("Cannot determine IServiceConnection callback layout");
    }

    private void deliver(ComponentName name, IBinder service, IBinder session, boolean dead,
                         boolean sessionLayout) throws RemoteException {
        ComponentName target = mComponentName == null ? name : mComponentName;
        service = GmsProxy.wrapServiceBinder(target, service, mVirtualRoute);
        if (mGoogleSignIn && (service == null || dead)) {
            SimpleCrashFix.cancelGoogleSignIn("Google service connection failed");
        }
        forwardConnection(mConn.asBinder(), target, service, session, dead, sessionLayout);
        Slog.i("ServiceConnectionDelegate", "callback_delivered route=" + (mVirtualRoute ? "virtual" : "host")
                + " component=" + target + " connected=" + (service != null && !dead));
    }

    /** Forward the framework callback without a reflection bridge that can silently fail. */
    public static void forwardConnection(IBinder connection, ComponentName name, IBinder service,
                                         IBinder session, boolean dead, boolean sessionLayout) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(name == null ? 0 : 1);
            if (name != null) name.writeToParcel(data, 0);
            data.writeStrongBinder(service);
            if (sessionLayout) data.writeStrongBinder(session);
            if (Build.VERSION.SDK_INT >= 26) data.writeInt(dead ? 1 : 0);
            if (!connection.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)) {
                throw new RemoteException("Service connection callback was rejected");
            }
        } finally {
            data.recycle();
        }
    }
}
