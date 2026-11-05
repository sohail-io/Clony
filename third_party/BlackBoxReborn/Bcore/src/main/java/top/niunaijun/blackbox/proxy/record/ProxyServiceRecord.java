package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.entity.AppConfig;


public class ProxyServiceRecord {
    public Intent mServiceIntent;
    public ServiceInfo mServiceInfo;
    public IBinder mToken;
    public int mUserId;
    public int mStartId;
    public AppConfig mAppConfig;

    public ProxyServiceRecord(Intent serviceIntent, ServiceInfo serviceInfo, IBinder token, int userId, int startId) {
        mServiceIntent = serviceIntent;
        mServiceInfo = serviceInfo;
        mUserId = userId;
        mStartId = startId;
        mToken = token;
    }

    public static void saveStub(Intent shadow, Intent target, ServiceInfo serviceInfo, IBinder token, int userId, int startId) {
        shadow.putExtra("_B_|_target_", target);
        shadow.putExtra("_B_|_service_info_", serviceInfo);
        shadow.putExtra("_B_|_user_id_", userId);
        shadow.putExtra("_B_|_start_id_", startId);
        BundleCompat.putBinder(shadow, "_B_|_token_", token);
    }

    public static ProxyServiceRecord create(Intent intent) {
        intent.setExtrasClassLoader(AppConfig.class.getClassLoader());
        Intent target = intent.getParcelableExtra("_B_|_target_");
        ServiceInfo serviceInfo = intent.getParcelableExtra("_B_|_service_info_");
        int userId = intent.getIntExtra("_B_|_user_id_", 0);
        int startId = intent.getIntExtra("_B_|_start_id_", 0);
        IBinder token = BundleCompat.getBinder(intent, "_B_|_token_");
        ProxyServiceRecord record = new ProxyServiceRecord(target, serviceInfo, token, userId, startId);
        Object config = intent.getExtras().get(AppConfig.KEY);
        if (config instanceof android.os.Bundle) record.mAppConfig = AppConfig.fromBundle((android.os.Bundle) config);
        else if (config instanceof AppConfig) record.mAppConfig = (AppConfig) config; // Previously issued records.
        return record;
    }
}
