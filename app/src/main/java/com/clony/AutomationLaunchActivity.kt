package com.clony

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Resolves only persisted legacy-launcher or clone-package-alias assignments. */
class AutomationLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val launcher = intent.getStringExtra("launcher_package").orEmpty()
            val target = CloneLaunchers.resolve(this, launcher)
                ?: ClonePackageNameStore.resolve(this, launcher)
            if (target == null) {
                android.util.Log.w("ClonyTrace", "automation_launch alias_unresolved launcherPackage=$launcher")
                Toast.makeText(this, "No active copy uses this package alias. Open TikViral and check the clone package name.", Toast.LENGTH_LONG).show()
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
}
