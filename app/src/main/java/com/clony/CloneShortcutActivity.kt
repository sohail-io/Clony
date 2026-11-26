package com.clony

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

private const val SHORTCUT_TRACE_TAG = "ClonyTrace"
private const val REQUEST_CODE_CAMERA_PERMISSION = 1003

class CloneShortcutActivity : ComponentActivity() {
    private var launchStartedAtMs = 0L
    private var removeShortcutTaskOnFinish = false
    private var isPinnedShortcutLaunch = false
    private var cloneRequestsCamera = false

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
                    finishShortcutActivity()
                    return@launch
                }

                val requestedPermissions = withContext(Dispatchers.IO) {
                    BlackBoxCore.getBPackageManager()
                        .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId)
                        ?.requestedPermissions.orEmpty()
                }
                cloneRequestsCamera = requestedPermissions.contains(Manifest.permission.CAMERA)
                val requestsMedia = requestedPermissions.any { permission ->
                    permission == Manifest.permission.READ_EXTERNAL_STORAGE ||
                        permission == Manifest.permission.READ_MEDIA_IMAGES ||
                        permission == Manifest.permission.READ_MEDIA_VIDEO ||
                        permission == Manifest.permission.READ_MEDIA_AUDIO ||
                        permission == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                }
                if (requestsMedia && !BlackBoxCore.get().hasStoragePermission()) {
                    traceShortcut("storage_permission_request", packageName, userId)
                    BlackBoxCore.get().requestStoragePermission(this@CloneShortcutActivity)
                    return@launch
                }

                requestCameraPermissionOrLaunch(packageName, userId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(SHORTCUT_TRACE_TAG, "shortcut stage=permission_preflight_failed package=" +
                    packageName + " userId=" + userId, error)
                toast(error.message?.takeIf(String::isNotBlank) ?: "TikViral couldn't open this copy.")
                finishShortcutActivity()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val packageName = intent.getStringExtra(EXTRA_CLONE_PACKAGE)
        val userId = intent.getIntExtra(EXTRA_CLONE_USER_ID, -1)

        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            traceShortcut("camera_permission_result granted=" + granted, packageName, userId)
            if (!granted) {
                toast("Camera access is off; camera features may not work in this copy.")
            }
            if (!packageName.isNullOrBlank() && userId >= 1) {
                startCloneLaunch(packageName, userId)
            } else {
                finishShortcutActivity()
            }
            return
        }

        if (requestCode != BlackBoxCore.getStoragePermissionRequestCode()) return
        val granted = BlackBoxCore.get().hasStoragePermission()
        traceShortcut("storage_permission_result granted=" + granted, packageName, userId)
        if (!granted) {
            toast("Media access is off; uploads may not show your photos or videos.")
        }
        if (!packageName.isNullOrBlank() && userId >= 1) {
            requestCameraPermissionOrLaunch(packageName, userId)
        } else {
            finishShortcutActivity()
        }
    }

    private fun requestCameraPermissionOrLaunch(packageName: String, userId: Int) {
        val hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (cloneRequestsCamera && !hasCameraPermission) {
            traceShortcut("camera_permission_request", packageName, userId)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
            return
        }
        startCloneLaunch(packageName, userId)
    }

    private fun startCloneLaunch(packageName: String, userId: Int) {
        lifecycleScope.launch {
            try {
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
                    toast("TikViral couldn't start this copy of $packageName.")
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
                toast(error.message?.takeIf(String::isNotBlank) ?: "TikViral couldn't open this copy.")
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
            val sourceLabel = runCatching {
                val sourceInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(sourceInfo).toString()
            }.getOrNull()?.takeIf(String::isNotBlank)
            val copyNumber = CloneCopyNameStore.assign(
                context,
                packageName,
                userId,
                ClonePackageNameStore.read(context, packageName, userId)
            )
            val previousLabel = shortcut.longLabel?.toString()?.takeIf(String::isNotBlank)
                ?: shortcut.shortLabel?.toString()?.takeIf(String::isNotBlank)
                ?: packageName
            // When the source app label can't be read, the shortcut label is the only
            // available name. It already includes this copy number, so remove repeated
            // trailing copies before rebuilding the migrated label. This also repairs labels
            // written by earlier migrations, such as "TikTok 1 1".
            val fallbackLabel = previousLabel
                .replace(Regex("(?:\\s+$copyNumber)+${'$'}"), "")
                .takeIf(String::isNotBlank)
                ?: previousLabel
            val shortcutLabel = sourceLabel ?: fallbackLabel
            val copyShortcutLabel = "$shortcutLabel $copyNumber"
            val builder = ShortcutInfoCompat.Builder(context, shortcut.id)
                .setShortLabel(copyShortcutLabel.take(25))
                .setLongLabel(copyShortcutLabel)
                .setActivity(ComponentName(context, CloneShortcutActivity::class.java))
                .setIntent(newIntent)
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
