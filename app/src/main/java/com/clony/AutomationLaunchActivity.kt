package com.clony

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Resolves only a persisted launcher assignment, never arbitrary guest extras. */
class AutomationLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val launcher = intent.getStringExtra("launcher_package").orEmpty()
            val target = CloneLaunchers.resolve(this, launcher)
            if (target == null) {
                Toast.makeText(this, "This launcher has no active copy. Open Clony to choose a copy.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, CloneShortcutActivity::class.java).apply {
                    action = ACTION_OPEN_CLONE
                    putExtra(EXTRA_CLONE_PACKAGE, target.first)
                    putExtra(EXTRA_CLONE_USER_ID, target.second)
                })
            }
        } catch (error: Exception) {
            android.util.Log.e("ClonyTrace", "Automation launcher failed", error)
            Toast.makeText(this, "Couldn't open this copy. Open Clony and try again.", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
