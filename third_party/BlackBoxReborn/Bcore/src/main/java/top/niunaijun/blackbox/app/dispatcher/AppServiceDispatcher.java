package top.niunaijun.blackbox.app.dispatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.ServiceRecord;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.entity.UnbindRecord;
import top.niunaijun.blackbox.proxy.record.ProxyServiceRecord;

import static android.app.Service.START_NOT_STICKY;



public class AppServiceDispatcher {
    public static final String TAG = "AppServiceDispatcher";

    private static final AppServiceDispatcher sServiceDispatcher = new AppServiceDispatcher();

    private Map<Intent.FilterComparison, ServiceRecord> mService = new HashMap<>();

    public static AppServiceDispatcher get() {
        return sServiceDispatcher;
    }

    private final Handler mHandler = BlackBoxCore.get().getHandler();

    public IBinder onBind(Intent proxyIntent) {
        ProxyServiceRecord serviceRecord = ProxyServiceRecord.create(proxyIntent);
        Intent intent = serviceRecord.mServiceIntent;
        ServiceInfo serviceInfo = serviceRecord.mServiceInfo;

        if (intent == null || serviceInfo == null)
            return null;



        Service service = getOrCreateService(serviceRecord);
        if (service == null)
            return null;
        intent.setExtrasClassLoader(service.getClassLoader());

        ServiceRecord record = findRecord(intent);
        record.incrementAndGetBindCount(intent);
        if (record.hasBinder(intent)) {
            if (record.isRebind()) {
                service.onRebind(intent);
                record.setRebind(false);
            }
            return record.getBinder(intent);
        }

        try {
            IBinder iBinder = service.onBind(intent);
            record.addBinder(intent, iBinder);
            return iBinder;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public int onStartCommand(Intent proxyIntent, int flags, int startId) {
        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return START_NOT_STICKY;
        }


        Service service = getOrCreateService(stubRecord);
        if (service == null)
            return START_NOT_STICKY;
        stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());
        ServiceRecord record = findRecord(stubRecord.mServiceIntent);
        record.setStartId(stubRecord.mStartId);
        try {
            int i = service.onStartCommand(stubRecord.mServiceIntent, flags, stubRecord.mStartId);
            BlackBoxCore.getBActivityManager().onStartCommand(proxyIntent, stubRecord.mUserId);
            return i;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onDestroy();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        mService.clear();

    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onConfigurationChanged(newConfig);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void onLowMemory() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onLowMemory();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void onTrimMemory(int level) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onTrimMemory(level);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        
    }

    public boolean onUnbind(Intent proxyIntent) {
        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return false;
        }
        Intent intent = stubRecord.mServiceIntent;

        try {
            UnbindRecord unbindRecord = BlackBoxCore.getBActivityManager().onServiceUnbind(proxyIntent, BlackBoxCore.getUserId());
            if (unbindRecord == null)
                return false;

            Service service = getOrCreateService(stubRecord);
            if (service == null)
                return false;

            stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());

            ServiceRecord record = findRecord(intent);

            boolean destroy = unbindRecord.getStartId() == 0;
            if (destroy || record.decreaseConnectionCount(intent)) {
                boolean b = service.onUnbind(intent);
                if (destroy) {
                    service.onDestroy();
                    BlackBoxCore.getBActivityManager().onServiceDestroy(proxyIntent, BlackBoxCore.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
                record.setRebind(true);

            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public IBinder peekService(Intent intent) {
        ServiceRecord record = findRecord(intent);
        if (record == null) {
            return null;
        }
        return record.getBinder(intent);
    }

    public void stopService(Intent intent) {
        if (intent == null)
            return;
        ServiceRecord record = findRecord(intent);
        if (record == null)
            return;
        if (record.getService() != null) {
            boolean destroy = record.getStartId() > 0;
            try {
                if (destroy) {
                    mHandler.post(() -> record.getService().onDestroy());
                    BlackBoxCore.getBActivityManager().onServiceDestroy(intent, BlackBoxCore.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private ServiceRecord findRecord(Intent intent) {
        return mService.get(new Intent.FilterComparison(intent));
    }

    private Service getOrCreateService(ProxyServiceRecord proxyServiceRecord) {
        Intent intent = proxyServiceRecord.mServiceIntent;
        ServiceInfo serviceInfo = proxyServiceRecord.mServiceInfo;
        IBinder token = proxyServiceRecord.mToken;

        ServiceRecord record = findRecord(intent);
        if (record != null && record.getService() != null) {
            return record.getService();
        }
        Service service;
        try {
            BActivityThread thread = BlackBoxCore.currentActivityThread();
            AppConfig config = BActivityThread.getAppConfig();
            if (config == null) {
                AppConfig expected = proxyServiceRecord.mAppConfig;
                if (expected != null) validateServiceConfig(expected, serviceInfo, proxyServiceRecord.mUserId);
                // Android may recreate a bound service without first invoking our
                // provider. Restore through the server so its PID/slot record is
                // refreshed too; never attach a stale owner token locally.
                BlackBoxCore.getBActivityManager().restartProcess(serviceInfo.packageName,
                        serviceInfo.processName, proxyServiceRecord.mUserId);
                config = BActivityThread.getAppConfig();
            }
            validateServiceConfig(config, serviceInfo, proxyServiceRecord.mUserId);
            service = thread.createService(serviceInfo, token);
            if (service == null) throw new IllegalStateException("Virtual service creation returned null");
        } catch (Throwable error) {
            Slog.e(TAG, "service_start_failed package=" + serviceInfo.packageName
                    + " process=" + serviceInfo.processName + " userId=" + proxyServiceRecord.mUserId, error);
            mService.remove(new Intent.FilterComparison(intent));
            BlackBoxCore.getBActivityManager().onServiceDestroy(makeProxyIntent(proxyServiceRecord), proxyServiceRecord.mUserId);
            return null; // Android delivers onNullBinding, rather than leaving a pending bind.
        }
        record = new ServiceRecord();
        record.setService(service);
        mService.put(new Intent.FilterComparison(intent), record);
        return service;
    }
    private static Intent makeProxyIntent(ProxyServiceRecord record) {
        Intent intent = new Intent();
        ProxyServiceRecord.saveStub(intent, record.mServiceIntent, record.mServiceInfo,
                record.mToken, record.mUserId, record.mStartId);
        return intent;
    }

    public static void validateServiceConfig(AppConfig config, ServiceInfo service, int userId) {
        if (config == null || service == null || config.userId != userId
                || !java.util.Objects.equals(config.packageName, service.packageName)
                || !java.util.Objects.equals(config.processName, service.processName)) {
            throw new IllegalStateException("Service process configuration does not match its virtual package/user");
        }
    }

}
