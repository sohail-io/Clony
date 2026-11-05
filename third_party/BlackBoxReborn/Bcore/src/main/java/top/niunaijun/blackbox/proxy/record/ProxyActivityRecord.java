package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.IBinder;

import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.Slog;


public class ProxyActivityRecord {
    private static final String TAG = "ProxyActivityRecord";
    public static final String EXTRA_ACTIVITY_RECORD = "_B_|_activity_record_v_";

    public int mUserId;
    public ActivityInfo mActivityInfo;
    public Intent mTarget;
    public IBinder mActivityRecord;

    public ProxyActivityRecord(int userId, ActivityInfo activityInfo, Intent target, IBinder activityRecord) {
        mUserId = userId;
        mActivityInfo = activityInfo;
        mTarget = target;
        mActivityRecord = activityRecord;
    }

    public static void saveStub(Intent shadow, Intent target, ActivityInfo activityInfo, IBinder activityRecord, int userId) {
        shadow.putExtra("_B_|_user_id_", userId);
        shadow.putExtra("_B_|_activity_info_", activityInfo);
        // The target may carry guest Parcelable extras whose classes do not exist in
        // system_server. Keep it in the runtime ActivityRecord and recover it by token.
        BundleCompat.putBinder(shadow, EXTRA_ACTIVITY_RECORD, activityRecord);
    }

    public static ProxyActivityRecord create(Intent intent) {
        int userId = intent.getIntExtra("_B_|_user_id_", 0);
        ActivityInfo activityInfo = intent.getParcelableExtra("_B_|_activity_info_");
        Intent target = intent.getParcelableExtra("_B_|_target_");
        IBinder activityRecord = BundleCompat.getBinder(intent, EXTRA_ACTIVITY_RECORD);
        if (target == null && activityRecord != null) {
            target = top.niunaijun.blackbox.fake.frameworks.BActivityManager.get()
                    .getActivityIntent(activityRecord);
        }
        Slog.i(TAG, "activity_target_recovered userId=" + userId + " available=" + (target != null));
        return new ProxyActivityRecord(userId, activityInfo, target, activityRecord);
    }

}
