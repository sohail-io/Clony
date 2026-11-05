package com.clony

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

internal data class ClonePackageUpdate(
    val packageName: String,
    val label: String,
    val userIds: List<Int>,
    val updatedVersionCode: Long,
    val updatedVersionName: String?
)

internal data class ClonePackageUpdateOutcome(
    val updated: Boolean = false,
    val label: String? = null,
    val copyCount: Int = 0,
    val versionName: String? = null,
    val error: String? = null
)

@Suppress("DEPRECATION")
private fun packageVersionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

internal object ClonePackageUpdater {
    private const val TAG = "ClonyCloneUpdate"
    private const val RUNTIME_USER_ID_START = 1
    private val mutex = Mutex()

    fun findOutdatedClones(context: Context, packageFilter: String?): List<ClonePackageUpdate> {
        Log.d(TAG, "scan start packageFilter=" + packageFilter)
        val runtime = BlackBoxCore.get()
        val packageManager = context.packageManager
        val launchableHostPackages = runCatching {
            context.getSystemService(LauncherApps::class.java)
                .getActivityList(null, Process.myUserHandle())
                .map { it.applicationInfo.packageName }
                .toSet()
        }.onFailure { Log.e(TAG, "scan failed to read host launchable apps", it) }.getOrDefault(emptySet())
        val cloneUsersByPackage = runtime.getUsers().orEmpty()
            .asSequence()
            .filter { it.id >= RUNTIME_USER_ID_START }
            .flatMap { user ->
                runCatching { runtime.getInstalledPackages(0, user.id).orEmpty() }
                    .onFailure { Log.e(TAG, "scan failed to read virtual packages userId=" + user.id, it) }
                    .getOrDefault(emptyList())
                    .asSequence()
                    .map { it.packageName to user.id }
            }
            .groupBy({ it.first }, { it.second })

        return cloneUsersByPackage.mapNotNull { (clonedPackage, userIds) ->
            if (clonedPackage == context.packageName || clonedPackage !in launchableHostPackages ||
                (packageFilter != null && clonedPackage != packageFilter)
            ) {
                return@mapNotNull null
            }
            val installedUsers = userIds.distinct().sorted()
            val sampleUser = installedUsers.firstOrNull() ?: return@mapNotNull null
            runCatching {
                val hostInfo = packageManager.getPackageInfo(clonedPackage, 0)
                val cloneInfo = BlackBoxCore.getBPackageManager()
                    .getPackageInfo(clonedPackage, 0, sampleUser)
                    ?: return@mapNotNull null
                val hostVersionCode = packageVersionCode(hostInfo)
                val cloneVersionCode = packageVersionCode(cloneInfo)
                if (hostVersionCode <= cloneVersionCode) {
                    Log.v(TAG, "clone current package=" + clonedPackage +
                        " hostVersion=" + hostVersionCode +
                        " cloneVersion=" + cloneVersionCode +
                        " users=" + installedUsers)
                    return@mapNotNull null
                }
                Log.w(TAG, "clone outdated package=" + clonedPackage +
                    " hostVersion=" + hostVersionCode +
                    " cloneVersion=" + cloneVersionCode +
                    " users=" + installedUsers)
                val label = hostInfo.applicationInfo?.loadLabel(packageManager)?.toString()
                    ?.takeIf(String::isNotBlank) ?: clonedPackage
                ClonePackageUpdate(
                    packageName = clonedPackage,
                    label = label,
                    userIds = installedUsers,
                    updatedVersionCode = hostVersionCode,
                    updatedVersionName = hostInfo.versionName
                )
            }.onFailure {
                Log.e(TAG, "scan failed for cloned package=" + clonedPackage + " users=" + installedUsers, it)
            }.getOrNull()
        }
    }

    fun isLaunchableClone(context: Context, packageName: String, userId: Int): Boolean {
        if (userId < RUNTIME_USER_ID_START || packageName == context.packageName) return false
        val hasHostLauncherActivity = runCatching {
            context.getSystemService(LauncherApps::class.java)
                .getActivityList(packageName, Process.myUserHandle())
                .isNotEmpty()
        }.getOrDefault(false)
        return hasHostLauncherActivity && runCatching {
            BlackBoxCore.get().isInstalled(packageName, userId)
        }.getOrDefault(false)
    }

    suspend fun updateIfNeeded(context: Context, packageName: String): ClonePackageUpdateOutcome =
        mutex.withLock {
            val update = withContext(Dispatchers.IO) {
                findOutdatedClones(context, packageName).firstOrNull()
            } ?: run {
                Log.d(TAG, "update not needed package=" + packageName)
                return@withLock ClonePackageUpdateOutcome()
            }

            Log.w(TAG, "update start package=" + packageName +
                " targetVersion=" + update.updatedVersionCode + " users=" + update.userIds)
            try {
                withContext(Dispatchers.IO) {
                    val runtime = BlackBoxCore.get()
                    update.userIds.forEach { userId ->
                        Log.w(TAG, "update stopping clone package=" + packageName +
                            " userId=" + userId + " reason=host_version_newer")
                        runtime.stopPackage(packageName, userId)
                    }

                    // The APK and split set are shared by package; each virtual user's data stays separate.
                    val result = runtime.installInstalledPackageAsUser(packageName, update.userIds.first())
                        ?: error("The virtual package service did not return an update result.")
                    if (!result.success) {
                        error(result.msg?.takeIf(String::isNotBlank) ?: "The runtime rejected the updated APK.")
                    }
                    Log.i(TAG, "update install returned success package=" + packageName +
                        " userId=" + update.userIds.first() + " message=" + result.msg)

                    val installedVersion = BlackBoxCore.getBPackageManager()
                        .getPackageInfo(packageName, 0, update.userIds.first())
                        ?.let(::packageVersionCode)
                    Log.i(TAG, "update verify package=" + packageName +
                        " expected=" + update.updatedVersionCode + " actual=" + installedVersion)
                    if (installedVersion != update.updatedVersionCode) {
                        error("The runtime still reports version $installedVersion after the update.")
                    }
                }
                ClonePackageUpdateOutcome(
                    updated = true,
                    label = update.label,
                    copyCount = update.userIds.size,
                    versionName = update.updatedVersionName
                )
            } catch (error: Throwable) {
                Log.e(TAG, "update failed package=" + packageName +
                    " targetVersion=" + update.updatedVersionCode + " users=" + update.userIds, error)
                ClonePackageUpdateOutcome(
                    error = error.message?.takeIf(String::isNotBlank)
                        ?: "the runtime rejected the update."
                )
            }
        }
}
