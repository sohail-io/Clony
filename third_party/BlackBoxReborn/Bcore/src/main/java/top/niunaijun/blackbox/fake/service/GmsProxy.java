package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;

/** Google broker identity belongs to the selected service route, not install state. */
public final class GmsProxy {
    private GmsProxy() {}

    public static IBinder wrapServiceBinder(ComponentName component, IBinder service,
                                            boolean virtualRoute) throws RemoteException {
        if (service == null || component == null || !"com.google.android.gms".equals(component.getPackageName())) {
            return service;
        }
        String descriptor = service.getInterfaceDescriptor();
        Slog.i("GmsProxy", "service_connected route=" + (virtualRoute ? "virtual" : "host")
                + " userId=" + BActivityThread.getUserId() + " component=" + component.flattenToShortString()
                + " descriptor=" + descriptor);
        if (!virtualRoute && descriptor != null && descriptor.contains("IGmsServiceBroker")) {
            // A host broker validates Clony's UID against the guest's package.
            // Do not send guest authentication requests through that connection.
            Slog.w("GmsProxy", "Rejecting host Google broker for a virtual caller");
            return null;
        }
        return service;
    }
}
