package com.clony

import android.app.Application
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import top.niunaijun.blackbox.utils.SimpleCrashFix

private const val TRACE_TAG = "ClonyTrace"

private fun logVirtualActivity(event: String, activity: Activity) {
    Log.i(
        TRACE_TAG,
        "virtualActivity=" + event +
            " package=" + activity.packageName +
            " class=" + activity.javaClass.name +
            " taskId=" + activity.taskId +
            " processId=" + android.os.Process.myPid() +
            " intentAction=" + activity.intent?.action +
            " intentComponent=" + activity.intent?.component +
            " intentFlags=0x" + Integer.toHexString(activity.intent?.flags ?: 0) +
            " finishing=" + activity.isFinishing +
            " changingConfig=" + activity.isChangingConfigurations +
            " elapsedMs=" + SystemClock.elapsedRealtime()
    )
}

class ClonyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        val runtime = BlackBoxCore.get()
        runtime.onBeforeMainApplicationAttach(this, base)
        runtime.doAttachBaseContext(base, object : ClientConfiguration() {
            override fun getHostPackageName(): String = base.packageName

            override fun isHideRoot(): Boolean = false

            override fun isEnableDaemonService(): Boolean = false

            override fun isUseVpnNetwork(): Boolean = false

            override fun isDisableFlagSecure(): Boolean = false

            override fun getLogSenderChatId(): String = ""
        })
        runtime.onAfterMainApplicationAttach(this, base)
        runtime.addAppLifecycleCallback(object : AppLifecycleCallback() {
            override fun beforeMainLaunchApk(packageName: String, userid: Int) {
                Log.i(TRACE_TAG, "runtimeLaunch stage=beforeMainLaunch package=" + packageName + " userId=" + userid)
            }

            override fun beforeCreateApplication(
                packageName: String?,
                processName: String?,
                context: Context?,
                userId: Int
            ) {
                Log.i(TRACE_TAG, "virtualApp stage=beforeCreate package=" + packageName +
                    " process=" + processName + " userId=" + userId)
            }

            override fun beforeApplicationOnCreate(
                packageName: String?,
                processName: String?,
                application: Application?,
                userId: Int
            ) {
                Log.i(TRACE_TAG, "virtualApp stage=beforeOnCreate package=" + packageName +
                    " process=" + processName + " userId=" + userId)
                application?.registerActivityLifecycleCallbacks(object :
                    Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityResumed(activity: Activity) {
                        SimpleCrashFix.onVirtualActivityResumed(activity)
                    }
                    override fun onActivityPaused(activity: Activity) {
                        SimpleCrashFix.onVirtualActivityPaused(activity)
                    }
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        state: android.os.Bundle
                    ) = Unit
                    override fun onActivityDestroyed(activity: Activity) {
                        SimpleCrashFix.onVirtualActivityDestroyed(activity)
                    }
                })
            }

            override fun afterApplicationOnCreate(
                packageName: String?,
                processName: String?,
                application: Application?,
                userId: Int
            ) {
                Log.i(TRACE_TAG, "virtualApp stage=afterOnCreate package=" + packageName +
                    " process=" + processName + " userId=" + userId)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) =
                logVirtualActivity("onCreate", activity)

            override fun onActivityStarted(activity: Activity) =
                logVirtualActivity("onStart", activity)

            override fun onActivityResumed(activity: Activity) {
                SimpleCrashFix.onVirtualActivityResumed(activity)
                logVirtualActivity("onResume", activity)
            }

            override fun onActivityPaused(activity: Activity) {
                SimpleCrashFix.onVirtualActivityPaused(activity)
                logVirtualActivity("onPause", activity)
            }

            override fun onActivityStopped(activity: Activity) =
                logVirtualActivity("onStop", activity)

            override fun onActivityDestroyed(activity: Activity) {
                SimpleCrashFix.onVirtualActivityDestroyed(activity)
                logVirtualActivity("onDestroy", activity)
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        BlackBoxCore.get().doCreate()
        CloneShortcutMigration.migratePinnedShortcuts(this)
    }
}
