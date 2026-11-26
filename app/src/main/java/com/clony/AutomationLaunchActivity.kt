package com.clony

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import top.niunaijun.blackbox.BlackBoxCore

/** Resolves launcher aliases and GenFarm clone IDs to BlackBox's numeric virtual user ID. */
class AutomationLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val launcher = intent.getStringExtra("launcher_package").orEmpty()
            val target = if (launcher.isNotBlank()) {
                CloneLaunchers.resolve(this, launcher)
                    ?: ClonePackageNameStore.resolve(this, launcher)
            } else {
                resolveGenFarmTarget()
            }
            if (target == null) {
                android.util.Log.w("ClonyTrace", "automation_launch target_unresolved launcherPackage=$launcher")
                Toast.makeText(this, "No active copy matches this launcher or clone ID.", Toast.LENGTH_LONG).show()
            } else {
                android.util.Log.i("ClonyTrace", "automation_launch resolved launcherPackage=$launcher package=${target.first} userId=${target.second}")
                startActivity(Intent(this, CloneShortcutActivity::class.java).apply {
                    action = ACTION_OPEN_CLONE
                    putExtra(EXTRA_CLONE_PACKAGE, target.first)
                    putExtra(EXTRA_CLONE_USER_ID, target.second)
                })
            }
        } catch (error: Exception) {
            android.util.Log.e("ClonyTrace", "Automation launcher failed", error)
            Toast.makeText(this, "Couldn't open this copy. Open TikViral and try again.", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    private fun resolveGenFarmTarget(): Pair<String, Int>? {
        val extras = intent.extras ?: return null
        val packageName = extras.getString("package_name")?.takeIf(String::isNotBlank) ?: return null
        return when (val cloneUserId = cloneUserIdValue(extras)) {
            is Number -> cloneUserId.toInt().takeIf { it > 0 }?.let { packageName to it }
            is String -> {
                val match = Regex("^(.+?)\\s+([1-9][0-9]*)$")
                    .find(cloneUserId.trim())
                    ?: return null
                val appLabel = runCatching {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString().trim()
                }.getOrNull() ?: return null
                if (!match.groupValues[1].trim().equals(appLabel, ignoreCase = true)) return null
                val copyNumber = match.groupValues[2].toIntOrNull() ?: return null
                resolveNamedCopy(packageName, copyNumber)?.let { packageName to it }
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun cloneUserIdValue(extras: Bundle): Any? = extras.get("clone_user_id")

    private fun resolveNamedCopy(packageName: String, copyNumber: Int): Int? {
        CloneCopyNameStore.resolveUserId(this, packageName, copyNumber)?.let { return it }

        // Populate assignments for copies created before copy names were persisted.
        val runtime = runCatching { BlackBoxCore.get() }.getOrNull() ?: return null
        val users = runCatching { runtime.getUsers().orEmpty() }.getOrDefault(emptyList())
        users.asSequence()
            .filter { it.id > 0 && runCatching { runtime.isInstalled(packageName, it.id) }.getOrDefault(false) }
            .sortedBy { it.id }
            .forEach { user ->
                CloneCopyNameStore.assign(
                    this,
                    packageName,
                    user.id,
                    ClonePackageNameStore.read(this, packageName, user.id)
                )
            }
        return CloneCopyNameStore.resolveUserId(this, packageName, copyNumber)
    }
}
