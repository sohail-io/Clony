package com.clony

import android.app.Application
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.BActivityThread
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import top.niunaijun.blackbox.utils.VirtualRuntimeTrace
import top.niunaijun.blackbox.utils.SimpleCrashFix
import java.security.MessageDigest

private const val TRACE_TAG = "ClonyTrace"

/**
 * Virtual apps run in Clony-owned processes, so Android otherwise gives all of
 * them the same WebView data/cache directory. WebView requires a distinct
 * directory for each process. Set the suffix before the virtual Application is
 * created (and before it can construct a WebView).
 */
private object VirtualWebViewDataDirectoryCompat {
    private const val TAG = "ClonyWebView"
    private const val HEX = "0123456789abcdef"

    fun configure(packageName: String?, processName: String?, userId: Int, context: Context?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        if (packageName.isNullOrBlank()) return

        val identity = "$packageName\u0000${processName.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        val suffix = buildString {
            append("clony_u").append(userId).append('_')
            digest.take(12).forEach { value ->
                val byte = value.toInt() and 0xff
                append(HEX[byte ushr 4])
                append(HEX[byte and 0x0f])
            }
        }

        runCatching { WebView.setDataDirectorySuffix(suffix) }
            .onSuccess { Log.i(TAG, "Set WebView data directory for $packageName process=${processName.orEmpty()} user=$userId") }
            .onFailure { error -> Log.w(TAG, "Could not set WebView data directory for $packageName", error) }

        runCatching {
            val connectivity = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivity?.activeNetwork
            val capabilities = network?.let(connectivity::getNetworkCapabilities)
            val dnsServers = network?.let { connectivity?.getLinkProperties(it)?.dnsServers }
            Log.i(
                TAG,
                "Clone network package=$packageName active=${network != null} " +
                    "internet=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
                    "dnsCount=${dnsServers?.size ?: -1}"
            )
        }.onFailure { error -> Log.w(TAG, "Could not inspect clone network for $packageName", error) }
    }
}

private fun logVirtualActivity(event: String, activity: Activity) {
    VirtualRuntimeTrace.activity(event, activity, BActivityThread.getUserId())
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
                VirtualWebViewDataDirectoryCompat.configure(packageName, processName, userId, context)
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
                    override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {
                        Unit
                    }
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
