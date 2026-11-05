package com.clony

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CancellationException
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

private const val SHORTCUT_TRACE_TAG = "ClonyTrace"

class CloneShortcutActivity : ComponentActivity() {
    private var launchStartedAtMs = 0L
    private var removeShortcutTaskOnFinish = false
    private var isPinnedShortcutLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isPinnedShortcutLaunch = intent.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK != 0
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_CLONE_PACKAGE)
        val userId = intent.getIntExtra(EXTRA_CLONE_USER_ID, -1)
        removeShortcutTaskOnFinish = isPinnedShortcutLaunch
        launchStartedAtMs = SystemClock.elapsedRealtime()
        traceShortcut("onCreate", packageName, userId)
        if (intent.action != ACTION_OPEN_CLONE || packageName.isNullOrBlank()) {
            traceShortcut("invalid_intent_finish", packageName, userId)
            finishShortcutActivity()
            return
        }

        lifecycleScope.launch {
            try {
                val isValidClone = withContext(Dispatchers.IO) {
                    ClonePackageUpdater.isLaunchableClone(this@CloneShortcutActivity, packageName, userId)
                }
                traceShortcut("validation_result valid=" + isValidClone, packageName, userId)
                if (!isValidClone) {
                    toast("This copy is no longer available.")
                    return@launch
                }

                // Existing clones should resume immediately. Starting the app's
                // launch activity first would briefly flash its splash before
                // the existing task comes forward.
                val resumedExistingTask = withContext(Dispatchers.IO) {
                    BlackBoxCore.getBActivityManager().moveTaskToFront(packageName, userId)
                }
                traceShortcut("existing_task_foregrounded=" + resumedExistingTask, packageName, userId)
                if (resumedExistingTask) {
                    return@launch
                }

                val update = ClonePackageUpdater.updateIfNeeded(this@CloneShortcutActivity, packageName)
                traceShortcut("update_result updated=" + update.updated + " error=" + update.error, packageName, userId)
                if (update.error != null) {
                    toast("Couldn't update ${update.label ?: packageName}: ${update.error}")
                    return@launch
                }

                val googleServices = withContext(Dispatchers.IO) {
                    BlackBoxCore.get().installGoogleSignInServices(userId)
                }
                traceShortcut(
                    "google_services_preflight success=${googleServices.success} message=${googleServices.msg}",
                    packageName,
                    userId
                )
                if (!googleServices.success) {
                    Log.w(
                        SHORTCUT_TRACE_TAG,
                        "Google sign-in runtime unavailable for clone; continuing app launch package=" +
                            packageName + " userId=" + userId + " reason=" + googleServices.msg
                    )
                }

                // Keep this bridge visually quiet so the clone's own launcher
                // screen (TikTok's SplashActivity for TikTok) is visible during
                // the foreground handoff below.
                val launched = withContext(Dispatchers.IO) {
                    runCatching {
                        val runtime = BlackBoxCore.get()
                        val installed = runtime.isInstalled(packageName, userId)
                        traceShortcut("installed_check installed=" + installed, packageName, userId)
                        if (!installed) return@runCatching false
                        traceShortcut("runtime_launch_call", packageName, userId)
                        val accepted = runtime.launchApkWithoutSplash(packageName, userId)
                        traceShortcut("runtime_launch_return accepted=" + accepted, packageName, userId)
                        accepted
                    }.onFailure { error ->
                        Log.e(SHORTCUT_TRACE_TAG, "shortcut stage=launch_exception package=" +
                            packageName + " userId=" + userId, error)
                    }.getOrDefault(false)
                }
                traceShortcut("launch_result launched=" + launched, packageName, userId)
                if (!launched) {
                    toast("Clony couldn't start this copy of $packageName.")
                } else {
                    val cloneTaskForegrounded = withContext(Dispatchers.IO) {
                        val activityManager = BlackBoxCore.getBActivityManager()
                        repeat(200) {
                            if (activityManager.moveTaskToFront(packageName, userId)) {
                                return@withContext true
                            }
                            Thread.sleep(100)
                        }
                        false
                    }
                    traceShortcut("clone_task_foregrounded=" + cloneTaskForegrounded, packageName, userId)
                    if (!cloneTaskForegrounded) {
                        Log.w(SHORTCUT_TRACE_TAG, "shortcut stage=foreground_timeout package=$packageName userId=$userId")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(SHORTCUT_TRACE_TAG, "shortcut stage=exception package=" + packageName +
                    " userId=" + userId, error)
                toast(error.message?.takeIf(String::isNotBlank) ?: "Clony couldn't open this copy.")
            } finally {
                traceShortcut("finish", packageName, userId)
                finishShortcutActivity()
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun traceShortcut(stage: String, packageName: String?, userId: Int) {
        Log.i(
            SHORTCUT_TRACE_TAG,
            "shortcut stage=" + stage +
                " package=" + packageName +
                " userId=" + userId +
                " taskId=" + taskId +
                " finishing=" + isFinishing +
                " intentAction=" + intent?.action +
                " intentFlags=0x" + Integer.toHexString(intent?.flags ?: 0) +
                " launchElapsedMs=" + (if (launchStartedAtMs == 0L) -1L else SystemClock.elapsedRealtime() - launchStartedAtMs) +
                " elapsedMs=" + SystemClock.elapsedRealtime()
        )
    }

    override fun onStart() {
        super.onStart()
        traceShortcut("activity_onStart", intent.getStringExtra(EXTRA_CLONE_PACKAGE), intent.getIntExtra(EXTRA_CLONE_USER_ID, -1))
    }

    override fun onResume() {
        super.onResume()
        traceShortcut("activity_onResume", intent.getStringExtra(EXTRA_CLONE_PACKAGE), intent.getIntExtra(EXTRA_CLONE_USER_ID, -1))
    }

    override fun onPause() {
        traceShortcut("activity_onPause", intent.getStringExtra(EXTRA_CLONE_PACKAGE), intent.getIntExtra(EXTRA_CLONE_USER_ID, -1))
        super.onPause()
    }

    override fun onStop() {
        traceShortcut("activity_onStop", intent.getStringExtra(EXTRA_CLONE_PACKAGE), intent.getIntExtra(EXTRA_CLONE_USER_ID, -1))
        super.onStop()
    }

    override fun onDestroy() {
        traceShortcut("activity_onDestroy", intent.getStringExtra(EXTRA_CLONE_PACKAGE), intent.getIntExtra(EXTRA_CLONE_USER_ID, -1))
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun finishShortcutActivity() {
        if (removeShortcutTaskOnFinish && isTaskRoot) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }
}

internal object CloneShortcutMigration {
    fun migratePinnedShortcuts(context: Context) {
        val pinnedShortcuts = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        }.getOrDefault(emptyList())

        val updates = pinnedShortcuts.mapNotNull { shortcut ->
            val oldIntent = shortcut.intent ?: return@mapNotNull null
            if (oldIntent.action != ACTION_OPEN_CLONE) {
                return@mapNotNull null
            }
            val packageName = oldIntent.getStringExtra(EXTRA_CLONE_PACKAGE)
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val userId = oldIntent.getIntExtra(EXTRA_CLONE_USER_ID, -1)
            if (userId < 1) return@mapNotNull null

            val newIntent = Intent(context, CloneShortcutActivity::class.java).apply {
                action = ACTION_OPEN_CLONE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_CLONE_PACKAGE, packageName)
                putExtra(EXTRA_CLONE_USER_ID, userId)
            }
            val shortLabel = shortcut.shortLabel?.toString()
                ?.takeIf(String::isNotBlank) ?: packageName.take(25)
            val builder = ShortcutInfoCompat.Builder(context, shortcut.id)
                .setShortLabel(shortLabel)
                .setActivity(ComponentName(context, CloneShortcutActivity::class.java))
                .setIntent(newIntent)
            shortcut.longLabel?.let(builder::setLongLabel)
            shortcut.icon?.let(builder::setIcon)
            builder.build()
        }

        if (updates.isNotEmpty()) {
            runCatching { ShortcutManagerCompat.updateShortcuts(context, updates) }
        }
    }
}

/** Remove launchable dynamic entries and revoke pinned entries for a deleted clone. */
internal object CloneShortcutCleanup {
    private const val TAG = SHORTCUT_TRACE_TAG

    fun removeForClone(context: Context, packageName: String, userId: Int) {
        val preferences = context.getSharedPreferences("clone_shortcuts", Context.MODE_PRIVATE)
        val preferenceKey = "$packageName:$userId"
        val rememberedId = preferences.getString(preferenceKey, null)
        Log.i(TAG, "shortcut_cleanup stage=begin package=$packageName userId=$userId rememberedId=$rememberedId")
        val dynamicShortcuts = runCatching {
            ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
            )
        }.onFailure { Log.e(TAG, "shortcut_cleanup stage=query_dynamic_failed package=$packageName userId=$userId", it) }
            .getOrDefault(emptyList())
        val pinnedShortcuts = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        }.onFailure { Log.e(TAG, "shortcut_cleanup stage=query_pinned_failed package=$packageName userId=$userId", it) }
            .getOrDefault(emptyList())
        fun belongsToClone(shortcut: ShortcutInfoCompat): Boolean =
            shortcut.id == rememberedId || shortcut.intent?.let { shortcutIntent ->
                shortcutIntent.action == ACTION_OPEN_CLONE &&
                    shortcutIntent.getStringExtra(EXTRA_CLONE_PACKAGE) == packageName &&
                    shortcutIntent.getIntExtra(EXTRA_CLONE_USER_ID, -1) == userId
            } == true
        val dynamicIds = dynamicShortcuts.filter(::belongsToClone).map { it.id }.distinct()
        val pinnedIds = pinnedShortcuts.filter(::belongsToClone).map { it.id }.distinct()
        Log.i(
            TAG,
            "shortcut_cleanup stage=matched package=$packageName userId=$userId " +
                "dynamicIds=$dynamicIds pinnedIds=$pinnedIds " +
                "dynamicSnapshot=${dynamicShortcuts.filter(::belongsToClone).map(::describeShortcut)} " +
                "pinnedSnapshot=${pinnedShortcuts.filter(::belongsToClone).map(::describeShortcut)}"
        )

        if (dynamicIds.isNotEmpty()) {
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, dynamicIds) }
                .onSuccess { Log.i(TAG, "shortcut_cleanup stage=remove_dynamic_ok package=$packageName userId=$userId ids=$dynamicIds") }
                .onFailure { Log.e(TAG, "shortcut_cleanup stage=remove_dynamic_failed package=$packageName userId=$userId ids=$dynamicIds", it) }
        } else {
            Log.i(TAG, "shortcut_cleanup stage=no_dynamic_matches package=$packageName userId=$userId")
        }
        if (pinnedIds.isNotEmpty()) {
            // Android does not let an app silently unpin a launcher-owned shortcut.
            runCatching {
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    pinnedIds,
                    "This clone was deleted. Remove this shortcut from the home screen."
                )
            }.onSuccess {
                Log.i(TAG, "shortcut_cleanup stage=disable_pinned_ok package=$packageName userId=$userId ids=$pinnedIds")
            }.onFailure {
                Log.e(TAG, "shortcut_cleanup stage=disable_pinned_failed package=$packageName userId=$userId ids=$pinnedIds", it)
            }
        } else {
            Log.i(TAG, "shortcut_cleanup stage=no_pinned_matches package=$packageName userId=$userId")
        }
        val preferencesRemoved = preferences.edit().remove(preferenceKey).commit()
        val remainingDynamic = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
                .filter(::belongsToClone).map(::describeShortcut)
        }.onFailure { Log.e(TAG, "shortcut_cleanup stage=verify_dynamic_failed package=$packageName userId=$userId", it) }
            .getOrDefault(emptyList())
        val remainingPinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter(::belongsToClone).map(::describeShortcut)
        }.onFailure { Log.e(TAG, "shortcut_cleanup stage=verify_pinned_failed package=$packageName userId=$userId", it) }
            .getOrDefault(emptyList())
        Log.i(
            TAG,
            "shortcut_cleanup stage=complete package=$packageName userId=$userId preferencesRemoved=$preferencesRemoved " +
                "remainingDynamic=$remainingDynamic remainingPinned=$remainingPinned"
        )
    }

    private fun describeShortcut(shortcut: ShortcutInfoCompat): String =
        "${shortcut.id}(label=${shortcut.shortLabel},action=${shortcut.intent?.action}," +
            "package=${shortcut.intent?.getStringExtra(EXTRA_CLONE_PACKAGE)}," +
            "userId=${shortcut.intent?.getIntExtra(EXTRA_CLONE_USER_ID, -1)})"
}

class CloneShortcutMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CloneShortcutMigration.migratePinnedShortcuts(context)
        }
    }
}

class ShortcutPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHORTCUT_PINNED) return
        val label = intent.getStringExtra(EXTRA_SHORTCUT_LABEL) ?: "Clone shortcut"
        Log.i(SHORTCUT_TRACE_TAG, "shortcut_pin stage=callback label=$label resultCode=$resultCode")
        Toast.makeText(context, "$label added to the home screen.", Toast.LENGTH_LONG).show()
    }
}
