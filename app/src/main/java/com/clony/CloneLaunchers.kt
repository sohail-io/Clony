package com.clony

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

internal object CloneLaunchers {
    private fun preferences(context: Context) =
        context.getSharedPreferences("automation_launchers_v1", Context.MODE_PRIVATE)

    private fun read(context: Context): LauncherRegistry {
        val prefs = preferences(context)
        val entries = JSONObject(prefs.getString("assignments", "{}")!!)
        val assignments = linkedMapOf<String, Int>()
        entries.keys().forEach { assignments[it] = entries.getInt(it) }
        return LauncherRegistry(assignments, prefs.getInt("next_slot", 1))
    }

    private fun save(context: Context, registry: LauncherRegistry) {
        check(preferences(context).edit()
            .putString("assignments", JSONObject(registry.assignments.toMap()).toString())
            .putInt("next_slot", registry.nextSlot)
            .commit()) { "Couldn't save the launcher assignment." }
    }

    @Synchronized
    fun assign(context: Context, packageName: String, userId: Int): String? {
        val key = "$packageName:$userId"
        val registry = read(context)
        val existing = registry.assignments[key]
        val slot = registry.assign(key) ?: return null
        if (existing == null) save(context, registry)
        return LauncherRegistry.packageName(slot)
    }

    @Synchronized
    fun remove(context: Context, packageName: String, userId: Int) {
        val registry = read(context)
        registry.remove("$packageName:$userId")
        save(context, registry)
    }

    @Synchronized
    fun resolve(context: Context, launcherPackage: String): Pair<String, Int>? {
        val slot = LauncherRegistry.slot(launcherPackage) ?: return null
        val key = read(context).resolve(slot) ?: return null
        val split = key.lastIndexOf(':')
        if (split < 1) return null
        return key.substring(0, split) to (key.substring(split + 1).toIntOrNull() ?: return null)
    }

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        context.packageManager.checkSignatures(context.packageName, packageName) ==
            android.content.pm.PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun installIntent(activity: Activity, launcherPackage: String): Intent {
        val slot = requireNotNull(LauncherRegistry.slot(launcherPackage))
        val directory = File(activity.cacheDir, "automation-launchers").apply { mkdirs() }
        val apk = File(directory, "copy$slot.apk")
        activity.assets.open("launchers/copy$slot.apk").use { input ->
            apk.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.launcherfiles", apk)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }
}
