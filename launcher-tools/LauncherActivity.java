package com.clony.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** This companion app only launches an assigned virtual copy; it contains no guest app code. */
public final class LauncherActivity extends Activity {
    private static final String HOST_PACKAGE = "@@HOST_PACKAGE@@";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    HOST_PACKAGE, HOST_PACKAGE + ".AutomationLaunchActivity"));
            intent.putExtra("launcher_package", getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Install or update Clony to open this copy.", Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
