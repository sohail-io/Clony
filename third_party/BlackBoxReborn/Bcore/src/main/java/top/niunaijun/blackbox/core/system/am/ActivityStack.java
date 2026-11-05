package top.niunaijun.blackbox.core.system.am;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import black.android.app.BRActivityManagerNative;
import black.android.app.BRIActivityManager;
import black.com.android.internal.BRRstyleable;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.PackageManagerCompat;
import top.niunaijun.blackbox.proxy.ProxyActivity;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;

import static android.content.pm.PackageManager.GET_ACTIVITIES;


@SuppressWarnings({"deprecation", "unchecked"})
public class ActivityStack {
    public static final String TAG = "ActivityStack";

    private final ActivityManager mAms;
    private final Map<Integer, TaskRecord> mTasks = new LinkedHashMap<>();
    private final Set<ActivityRecord> mLaunchingActivities = new HashSet<>();
    // Retain the target while Android resolves the proxy task and process configuration.
    private final Map<IBinder, Intent> mActivityIntents = new ConcurrentHashMap<>();

    public static final int LAUNCH_TIME_OUT = 0;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_TIME_OUT:
                    ActivityRecord record = (ActivityRecord) msg.obj;
                    if (record != null) {
                        mLaunchingActivities.remove(record);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public ActivityStack() {
        mAms = (ActivityManager) BlackBoxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
    }

    public boolean containsFlag(Intent intent, int flag) {
        return (intent.getFlags() & flag) != 0;
    }

    /**
     * Bring an already-running clone task forward without starting its
     * launcher Activity. Starting the launcher first creates a short-lived
     * splash Activity which can visibly open and immediately close when the
     * existing task is selected.
     */
    public boolean moveTaskToFront(String packageName, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            List<TaskRecord> tasks = new ArrayList<>(mTasks.values());
            for (int i = tasks.size() - 1; i >= 0; i--) {
                TaskRecord task = tasks.get(i);
                if (task.userId != userId) {
                    continue;
                }
                boolean hasLiveCloneActivity = false;
                synchronized (task.activities) {
                    for (ActivityRecord activity : task.activities) {
                        if (!activity.finished && activity.info != null
                                && packageName.equals(activity.info.packageName)) {
                            hasLiveCloneActivity = true;
                            break;
                        }
                    }
                }
                if (!hasLiveCloneActivity) {
                    continue;
                }
                try {
                    mAms.moveTaskToFront(task.id, 0);
                    Log.i(TAG, "foreground existing clone task userId=" + userId +
                            " taskId=" + task.id + " package=" + packageName);
                    return true;
                } catch (Throwable throwable) {
                    Log.w(TAG, "Could not foreground clone task userId=" + userId +
                            " taskId=" + task.id + " package=" + packageName, throwable);
                    return false;
                }
            }
        }
        return false;
    }

    public int startActivitiesLocked(int userId, Intent[] intents, String[] resolvedTypes, IBinder resultTo, Bundle options) {
        if (intents == null) {
            throw new NullPointerException("intents is null");
        }
        if (resolvedTypes == null) {
            throw new NullPointerException("resolvedTypes is null");
        }
        if (intents.length != resolvedTypes.length) {
            throw new IllegalArgumentException("intents are length different than resolvedTypes");
        }
        for (int i = 0; i < intents.length; i++) {
            startActivityLocked(userId, intents[i], resolvedTypes[i], resultTo, null, -1, 0, options);
        }
        return 0;
    }

    public int startActivityLocked(int userId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options) {
        synchronized (mTasks) {
            synchronizeTasks();
        }

        ResolveInfo resolveInfo = BPackageManagerService.get().resolveActivity(intent, GET_ACTIVITIES, resolvedType, userId);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return 0;
        }
        Log.d(TAG, "startActivityLocked : " + resolveInfo.activityInfo);
        ActivityInfo activityInfo = resolveInfo.activityInfo;

        ActivityRecord sourceRecord = findActivityRecordByToken(userId, resultTo);
        if (sourceRecord == null) {
            resultTo = null;
        }
        TaskRecord sourceTask = null;
        if (sourceRecord != null) {
            sourceTask = sourceRecord.task;
        }

        String taskAffinity = ComponentUtils.getTaskAffinity(activityInfo);

        int launchModeFlags = 0;
        boolean singleTop = containsFlag(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP)
                || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP
                || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK
                || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE;
        boolean newTask = containsFlag(intent, Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean clearTop = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
        boolean clearTask = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);

        TaskRecord taskRecord = null;
        switch (activityInfo.launchMode) {
            case ActivityInfo.LAUNCH_SINGLE_TOP:
            case ActivityInfo.LAUNCH_MULTIPLE:
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity, intent);
                if (taskRecord == null && !newTask) {
                    taskRecord = sourceTask;
                }
                break;
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity, intent);
                break;
        }

        
        boolean sameLaunchTarget = taskRecord != null
                && taskRecord.rootIntent != null
                && ComponentUtils.intentFilterEquals(taskRecord.rootIntent, intent);
        if (taskRecord != null && taskRecord.needNewTask() && sameLaunchTarget && !clearTop) {
            // A task retained by Android can outlive the virtual ActivityRecord.
            // Such a task has nothing alive to receive the launch, so remove that
            // empty proxy task and recreate the virtual activity in a foreground task.
            Log.i(TAG, "relaunch stale task userId=" + userId + " taskId=" + taskRecord.id +
                    " target=" + ComponentUtils.toComponentName(activityInfo) +
                    " clearTop=" + clearTop);
            removeStaleTask(taskRecord);
            return startActivityInNewTaskLocked(userId, intent, activityInfo, resultTo, launchModeFlags);
        }

        if (taskRecord != null && taskRecord.needNewTask() && sameLaunchTarget && clearTop) {
            // CLEAR_TOP is an explicit request to bring the clone's target
            // activity back. A stale proxy task has no live activity to receive
            // it, so starting a fresh virtual activity is required.
            Log.i(TAG, "clearTop on empty task; start fresh userId=" + userId +
                    " staleTaskId=" + taskRecord.id + " target=" +
                    ComponentUtils.toComponentName(activityInfo) + " intentFlags=0x" +
                    Integer.toHexString(intent.getFlags()));
        }

        if (taskRecord == null || taskRecord.needNewTask()) {
            return startActivityInNewTaskLocked(userId, intent, activityInfo, resultTo, launchModeFlags);
        }
        
        mAms.moveTaskToFront(taskRecord.id, 0);

        boolean notStartToFront = false;
        if (clearTop || singleTop || clearTask) {
            notStartToFront = true;
        }

        // A launcher's MAIN/LAUNCHER intent is the request to open this app,
        // even when the task root intent was rewritten by the app or the
        // shortcut points at the app's declared SplashActivity. Match the
        // existing live task by virtual user + task affinity in that case;
        // starting the launcher again creates another proxy activity/task and
        // leaves the already-running clone behind it in Recents.
        boolean launcherIntent = Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        boolean startTaskToFront = !notStartToFront
                && (sameLaunchTarget || (newTask && launcherIntent));

        if (startTaskToFront) {
            Log.i(TAG, "reuse launcher task userId=" + userId + " taskId=" + taskRecord.id +
                    " target=" + ComponentUtils.toComponentName(activityInfo) +
                    " sameRootIntent=" + sameLaunchTarget + " launcherIntent=" + launcherIntent);
            return 0;
        }

        ActivityRecord topActivityRecord = taskRecord.getTopActivityRecord();
        ActivityRecord targetActivityRecord = clearTop
                ? findActivityRecordByComponentName(taskRecord, ComponentUtils.toComponentName(activityInfo))
                : null;
        ActivityRecord newIntentRecord = null;
        boolean ignore = false;
        List<ActivityRecord> activitiesToFinish = new ArrayList<>();

        if (clearTop) {
            boolean targetIsTop = targetActivityRecord != null && targetActivityRecord == topActivityRecord;
            Log.i(TAG, "clearTop route userId=" + userId + " taskId=" + taskRecord.id +
                    " target=" + ComponentUtils.toComponentName(activityInfo) +
                    " targetRecord=" + (targetActivityRecord == null ? "none" :
                    targetActivityRecord.component + ",finished=" + targetActivityRecord.finished) +
                    " targetIsTop=" + targetIsTop + " launchMode=" + activityInfo.launchMode +
                    " singleTop=" + singleTop + " intentFlags=0x" +
                    Integer.toHexString(intent.getFlags()));
        }

        if (clearTop) {
            if (targetActivityRecord != null) {
                boolean targetIsTop = targetActivityRecord == topActivityRecord;
                synchronized (targetActivityRecord.task.activities) {
                    for (int i = targetActivityRecord.task.activities.size() - 1; i >= 0; i--) {
                        ActivityRecord next = targetActivityRecord.task.activities.get(i);
                        if (next != targetActivityRecord) {
                            if (!next.finished) {
                                next.finished = true;
                                activitiesToFinish.add(next);
                                Log.d(TAG, "makerFinish: " + next.component + " reason=clearTop target=" +
                                        targetActivityRecord.component);
                            }
                        } else {
                            // Reuse a CLEAR_TOP target only when it is already
                            // the top virtual activity. On Android 17 the
                            // per-task proxy can represent the whole virtual
                            // stack; finishing an activity above a lower target
                            // may otherwise finish the sole host task before
                            // the lower target can be resumed. Recreate the
                            // target in that case, then finish the old records.
                            if (singleTop && targetIsTop) {
                                newIntentRecord = targetActivityRecord;
                            } else {
                                targetActivityRecord.finished = true;
                                activitiesToFinish.add(targetActivityRecord);
                                Log.i(TAG, "clearTop recreate target=" + targetActivityRecord.component +
                                        " becauseTargetIsTop=" + targetIsTop + " singleTop=" + singleTop +
                                        " taskId=" + targetActivityRecord.task.id + " userId=" + userId);
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (singleTop && !clearTop) {
            if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
                newIntentRecord = topActivityRecord;
            } else {
                synchronized (mLaunchingActivities) {
                    for (ActivityRecord launchingActivity : mLaunchingActivities) {
                        if (!launchingActivity.finished && launchingActivity.component.equals(intent.getComponent())) {
                            
                            ignore = true;
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK && !clearTop) {
            if (ComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
                newIntentRecord = topActivityRecord;
            } else {
                ActivityRecord record = findActivityRecordByComponentName(userId, ComponentUtils.toComponentName(activityInfo));
                if (record != null) {
                    
                    newIntentRecord = record;
                    
                    synchronized (taskRecord.activities) {
                        for (int i = taskRecord.activities.size() - 1; i >= 0; i--) {
                            ActivityRecord next = taskRecord.activities.get(i);
                            if (next != record) {
                                if (!next.finished) {
                                    next.finished = true;
                                    activitiesToFinish.add(next);
                                    Log.d(TAG, "makerFinish: " + next.component + " reason=singleTask target=" +
                                            record.component);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE && !clearTop) {
            newIntentRecord = topActivityRecord;
        }

        
        if (clearTask && newTask) {
            for (ActivityRecord activity : taskRecord.activities) {
                activity.finished = true;
            }
            finishAllActivity(userId);
        }

        if (newIntentRecord != null) {
            finishActivityRecords(activitiesToFinish, "before new intent delivery");
            Log.i(TAG, "deliverNewIntent target=" + newIntentRecord.component + " taskId=" +
                    (newIntentRecord.task == null ? -1 : newIntentRecord.task.id) +
                    " finished=" + newIntentRecord.finished + " userId=" + userId);
            deliverNewIntentLocked(newIntentRecord, intent);
            return 0;
        } else if (ignore) {
            return 0;
        }

        if (resultTo == null) {
            // A top-level launch (for example, a home-screen shortcut) has no
            // virtual caller task. Do not attach it to an unrelated virtual
            // ActivityThread: Android 16 validates that caller's UID against
            // the host package and rejects the launch with a source-UID mismatch.
            int launchResult = startActivityInNewTaskLocked(userId, intent, activityInfo, null, launchModeFlags);
            finishActivityRecords(activitiesToFinish, "after replacement launch");
            return launchResult;
        }

        if (sourceTask != null) {
            ActivityRecord top = sourceTask.getTopActivityRecord();
            if (top != null) {
                resultTo = top.token;
            }
        }
        if (clearTop) {
            Log.i(TAG, "clearTop recreate request userId=" + userId + " taskId=" + taskRecord.id +
                    " target=" + ComponentUtils.toComponentName(activityInfo) +
                    " source=" + (topActivityRecord == null ? "none" : topActivityRecord.component) +
                    " resultToPresent=" + (resultTo != null) +
                    " finishCount=" + activitiesToFinish.size());
        }
        int launchResult = startActivityInSourceTask(intent,
                resolvedType, resultTo, resultWho, requestCode, flags, options, userId, topActivityRecord, activityInfo, launchModeFlags);
        if (clearTop) {
            Log.i(TAG, "clearTop recreate requested userId=" + userId + " taskId=" + taskRecord.id +
                    " target=" + ComponentUtils.toComponentName(activityInfo) +
                    " launchResult=" + launchResult);
        }
        finishActivityRecords(activitiesToFinish, "after replacement launch");
        return launchResult;
    }

    private void finishActivityRecords(List<ActivityRecord> activities, String reason) {
        for (ActivityRecord activity : activities) {
            finishActivityRecord(activity, reason);
        }
    }

    private void deliverNewIntentLocked(ActivityRecord activityRecord, Intent intent) {
        try {
            activityRecord.processRecord.bActivityThread.handleNewIntent(activityRecord.token, intent);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to deliver new intent to " + activityRecord.component, e);
        }
    }

    private void finishActivityRecord(ActivityRecord activityRecord, String reason) {
        if (activityRecord == null || activityRecord.token == null || activityRecord.processRecord == null ||
                activityRecord.processRecord.bActivityThread == null) {
            Log.w(TAG, "Cannot finish virtual activity yet reason=" + reason + " record=" +
                    (activityRecord == null ? "null" : activityRecord.component));
            return;
        }
        Log.i(TAG, "finish activity request reason=" + reason + " component=" + activityRecord.component +
                " taskId=" + (activityRecord.task == null ? -1 : activityRecord.task.id) +
                " userId=" + activityRecord.userId);
        try {
            activityRecord.processRecord.bActivityThread.finishActivity(activityRecord.token);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to finish virtual activity " + activityRecord.component + " reason=" + reason, e);
        }
    }

    private Intent startActivityProcess(int userId, Intent intent, ActivityInfo
            info, ActivityRecord record) {
        ProxyActivityRecord stubRecord = new ProxyActivityRecord(userId, info, intent, record);
        ProcessRecord targetApp = BProcessManagerService.get().startProcessLocked(info.packageName, info.processName, userId, -1, Binder.getCallingPid());
        if (targetApp == null) {
            throw new RuntimeException("Unable to create process, name:" + info.name);
        }
        return getStartStubActivityIntentInner(intent, targetApp.bpid, userId, stubRecord, info);
    }

    private int startActivityInNewTaskLocked(int userId, Intent intent, ActivityInfo
            activityInfo, IBinder resultTo, int launchMode) {
        ActivityRecord record = newActivityRecord(intent, activityInfo, resultTo, userId);
        Intent shadow = startActivityProcess(userId, intent, activityInfo, record);

        shadow.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shadow.addFlags(launchMode);

        BlackBoxCore.getContext().startActivity(shadow);
        return 0;
    }

    private void removeStaleTask(TaskRecord staleTask) {
        try {
            for (ActivityManager.AppTask appTask : mAms.getAppTasks()) {
                ActivityManager.RecentTaskInfo taskInfo = appTask.getTaskInfo();
                if (taskInfo != null && taskInfo.id == staleTask.id) {
                    appTask.finishAndRemoveTask();
                    break;
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not remove stale proxy task taskId=" + staleTask.id, throwable);
        }
        synchronized (mTasks) {
            if (mTasks.get(staleTask.id) == staleTask) {
                mTasks.remove(staleTask.id);
            }
        }
    }

    private int startActivityInSourceTask(Intent intent, String resolvedType,
                                          IBinder resultTo, String resultWho, int requestCode, int flags,
                                          Bundle options,
                                          int userId, ActivityRecord sourceRecord, ActivityInfo activityInfo, int launchMode) {
        ActivityRecord selfRecord = newActivityRecord(intent, activityInfo, resultTo, userId);
        Intent shadow = startActivityProcess(userId, intent, activityInfo, selfRecord);
        shadow.setAction(UUID.randomUUID().toString());
        shadow.addFlags(launchMode);
        if (resultTo == null) {
            shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return realStartActivityLocked(sourceRecord.processRecord.appThread, shadow, resolvedType, resultTo, resultWho, requestCode, flags, options);
    }

    private int realStartActivityLocked(IInterface appThread, Intent intent, String resolvedType,
                                        IBinder resultTo, String resultWho, int requestCode, int flags,
                                        Bundle options) {
        try {
            flags &= ~ActivityManagerCompat.START_FLAG_DEBUG;
            flags &= ~ActivityManagerCompat.START_FLAG_NATIVE_DEBUGGING;
            flags &= ~ActivityManagerCompat.START_FLAG_TRACK_ALLOCATION;

            BRIActivityManager.get(BRActivityManagerNative.get().getDefault()).startActivity(appThread, BlackBoxCore.getHostPkg(), intent,
                    resolvedType, resultTo, resultWho, requestCode, flags, null, options);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private ActivityRecord getTopActivityRecord() {
        synchronized (mTasks) {
            synchronizeTasks();
        }
        List<TaskRecord> tasks = new LinkedList<>(mTasks.values());
        if (tasks.isEmpty())
            return null;
        return tasks.get(tasks.size() - 1).getTopActivityRecord();
    }

    private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                                   int userId, ProxyActivityRecord target,
                                                   ActivityInfo activityInfo) {
        Intent shadow = new Intent();
        TypedArray typedArray = null;
        try {
            Resources resources = PackageManagerCompat.getResources(BlackBoxCore.getContext(), activityInfo.applicationInfo);
            int id;
            if (activityInfo.theme != 0) {
                id = activityInfo.theme;
            } else {
                id = activityInfo.applicationInfo.theme;
            }
            assert resources != null;
            typedArray = resources.newTheme().obtainStyledAttributes(id, BRRstyleable.get().Window());
            boolean windowIsTranslucent = typedArray.getBoolean(BRRstyleable.get().Window_windowIsTranslucent(), false);
            if (windowIsTranslucent) {
                shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.TransparentProxyActivity(vpid)));
            } else {
                shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.getProxyActivity(vpid)));
            }
            Slog.d(TAG, activityInfo + ", windowIsTranslucent: " + windowIsTranslucent);
        } catch (Throwable e) {
            e.printStackTrace();
            shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.getProxyActivity(vpid)));
        } finally {
            if (typedArray != null) {
                typedArray.recycle();
            }
        }
        ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, target.mActivityRecord, target.mUserId);
        return shadow;
    }

    private void finishAllActivity(int userId) {
        for (TaskRecord task : mTasks.values()) {
            for (ActivityRecord activity : task.activities) {
                if (activity.userId == userId) {
                    if (activity.finished) {
                        try {
                            activity.processRecord.bActivityThread.finishActivity(activity.token);
                        } catch (RemoteException ignored) {
                        }
                    }
                }
            }
        }
    }

    ActivityRecord newActivityRecord(Intent intent, ActivityInfo info, IBinder resultTo,
                                     int userId) {
        ActivityRecord targetRecord = ActivityRecord.create(intent, info, resultTo, userId);
        mActivityIntents.put(targetRecord, new Intent(intent));
        Slog.i(TAG, "activity_target_saved userId=" + userId + " package=" + info.packageName);
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.add(targetRecord);
            Message obtain = Message.obtain(mHandler, LAUNCH_TIME_OUT, targetRecord);
            mHandler.sendMessageDelayed(obtain, 2000);
        }
        return targetRecord;
    }

    private ActivityRecord findActivityRecordByComponentName(int userId, ComponentName
            componentName) {
        ActivityRecord record = null;
        for (TaskRecord next : mTasks.values()) {
            if (userId == next.userId) {
                for (ActivityRecord activity : next.activities) {
                    if (!activity.finished && activity.component.equals(componentName)) {
                        record = activity;
                        break;
                    }
                }
            }
        }
        return record;
    }

    private ActivityRecord findActivityRecordByComponentName(TaskRecord task, ComponentName componentName) {
        if (task == null) {
            return null;
        }
        synchronized (task.activities) {
            for (int i = task.activities.size() - 1; i >= 0; i--) {
                ActivityRecord activity = task.activities.get(i);
                if (!activity.finished && componentName.equals(activity.component)) {
                    return activity;
                }
            }
        }
        return null;
    }

    private ActivityRecord findActivityRecordByToken(int userId, IBinder token) {
        ActivityRecord record = null;
        if (token != null) {
            for (TaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            record = activity;
                            break;
                        }
                    }
                }
            }
        }
        return record;
    }

    private TaskRecord findTaskRecordByTaskAffinityLocked(int userId, String taskAffinity, Intent intent) {
        synchronized (mTasks) {
            List<TaskRecord> tasks = new ArrayList<>(mTasks.values());
            TaskRecord exactLaunchTask = null;
            TaskRecord affinityFallback = null;
            // synchronizeTasks keeps this map oldest-to-newest. Prefer the newest
            // exact launch task, including a recent task whose virtual activity
            // record has already been destroyed, then fall back to a live affinity task.
            for (int i = tasks.size() - 1; i >= 0; i--) {
                TaskRecord next = tasks.get(i);
                if (userId != next.userId || !taskAffinity.equals(next.taskAffinity)) {
                    continue;
                }
                if (next.rootIntent != null && ComponentUtils.intentFilterEquals(next.rootIntent, intent)) {
                    if (exactLaunchTask == null) {
                        exactLaunchTask = next;
                    }
                } else if (!next.needNewTask() && affinityFallback == null) {
                    affinityFallback = next;
                }
            }
            return exactLaunchTask != null ? exactLaunchTask : affinityFallback;
        }
    }

    private TaskRecord findTaskRecordByTokenLocked(int userId, IBinder token) {
        synchronized (mTasks) {
            for (TaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            return next;
                        }
                    }
                }
            }
            return null;
        }
    }

    public void onActivityCreated(ProcessRecord processRecord, int taskId, IBinder
            token, ActivityRecord record) {
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.remove(record);
            mHandler.removeMessages(LAUNCH_TIME_OUT, record);
        }
        if (record.finished) {
            record.token = token;
            record.processRecord = processRecord;
            try {
                processRecord.bActivityThread.finishActivity(token);
            } catch (RemoteException ignored) {
            }
            return;
        }
        if (taskId < 0) {
            record.token = token;
            record.processRecord = processRecord;
            return;
        }
        synchronized (mTasks) {
            synchronizeTasks();
            TaskRecord taskRecord = mTasks.get(taskId);
            if (taskRecord == null) {
                taskRecord = new TaskRecord(taskId, record.userId, ComponentUtils.getTaskAffinity(record.info));
                taskRecord.rootIntent = record.intent;
                mTasks.put(taskId, taskRecord);
            }
            record.token = token;
            record.processRecord = processRecord;
            record.task = taskRecord;
            taskRecord.addTopActivity(record);
            Log.d(TAG, "onActivityCreated : " + record.component.toString());
        }
    }

    public void onActivityTaskIdResolved(int taskId, IBinder token, ActivityRecord record) {
        if (record == null) {
            return;
        }
        synchronized (mTasks) {
            record.token = token;
            if (taskId < 0) {
                return;
            }
            TaskRecord previousTask = record.task;
            if (previousTask != null && previousTask.id != taskId) {
                previousTask.removeActivity(record);
                if (previousTask.activities.isEmpty()) {
                    mTasks.remove(previousTask.id);
                }
            }

            TaskRecord taskRecord = mTasks.get(taskId);
            if (taskRecord == null) {
                taskRecord = new TaskRecord(taskId, record.userId, ComponentUtils.getTaskAffinity(record.info));
                taskRecord.rootIntent = record.intent;
                mTasks.put(taskId, taskRecord);
            }
            record.task = taskRecord;
            if (!taskRecord.activities.contains(record)) {
                taskRecord.addTopActivity(record);
            }
        }
    }

    public Intent getActivityIntent(IBinder activityRecordBinder) {
        Intent retained = mActivityIntents.get(activityRecordBinder);
        if (retained != null) {
            Slog.i(TAG, "activity_target_lookup hit");
            return new Intent(retained);
        }
        Slog.w(TAG, "activity_target_lookup miss; binder=" +
                (activityRecordBinder == null ? "null" : activityRecordBinder.getClass().getName()));
        if (!(activityRecordBinder instanceof ActivityRecord)) return null;
        ActivityRecord requested = (ActivityRecord) activityRecordBinder;
        synchronized (mLaunchingActivities) {
            for (ActivityRecord record : mLaunchingActivities) {
                if (record == requested) return new Intent(record.intent);
            }
        }
        synchronized (mTasks) {
            for (TaskRecord task : mTasks.values()) {
                for (ActivityRecord record : task.activities) {
                    if (record == requested) return new Intent(record.intent);
                }
            }
        }
        return null;
    }

    public List<ActivityRecord> markPackageActivitiesFinished(String packageName, int userId) {
        List<ActivityRecord> activitiesToFinish = new ArrayList<>();

        synchronized (mLaunchingActivities) {
            Iterator<ActivityRecord> iterator = mLaunchingActivities.iterator();
            while (iterator.hasNext()) {
                ActivityRecord activity = iterator.next();
                if (activity.userId == userId && activity.component != null
                        && packageName.equals(activity.component.getPackageName())) {
                    activity.finished = true;
                    mActivityIntents.remove(activity);
                    iterator.remove();
                    mHandler.removeMessages(LAUNCH_TIME_OUT, activity);
                }
            }
        }

        synchronized (mTasks) {
            synchronizeTasks();
            for (TaskRecord task : mTasks.values()) {
                if (task.userId != userId) {
                    continue;
                }
                for (ActivityRecord activity : task.activities) {
                    if (!activity.finished && activity.component != null
                            && activity.userId == userId
                            && packageName.equals(activity.component.getPackageName())) {
                        activity.finished = true;
                        mActivityIntents.remove(activity);
                        activitiesToFinish.add(activity);
                    }
                }
            }
        }

        return activitiesToFinish;
    }

    public void onActivityResumed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            Log.d(TAG, "onActivityResumed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
            activityRecord.task.addTopActivity(activityRecord);
        }
    }

    public void onActivityDestroyed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            activityRecord.finished = true;
            mActivityIntents.remove(activityRecord);
            Log.d(TAG, "onActivityDestroyed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
        }
    }

    public void onFinishActivity(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            activityRecord.finished = true;
            Log.d(TAG, "onFinishActivity : " + activityRecord.component.toString());
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                if (resultTo != null) {
                    return resultTo.info.packageName;
                }
            }
            return BlackBoxCore.getHostPkg();
        }
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                if (resultTo != null) {
                    return resultTo.component;
                }
            }
            return new ComponentName(BlackBoxCore.getHostPkg(), ProxyActivity.P0.class.getName());
        }
    }

    @SuppressWarnings("deprecation")
    private void synchronizeTasks() {
        List<ActivityManager.RecentTaskInfo> recentTasks = mAms.getRecentTasks(100, 0);
        Map<Integer, TaskRecord> newTacks = new LinkedHashMap<>();
        for (int i = recentTasks.size() - 1; i >= 0; i--) {
            ActivityManager.RecentTaskInfo next = recentTasks.get(i);
            TaskRecord taskRecord = mTasks.get(next.id);
            if (taskRecord == null)
                continue;
            newTacks.put(next.id, taskRecord);
        }
        mTasks.clear();
        mTasks.putAll(newTacks);
    }
}
