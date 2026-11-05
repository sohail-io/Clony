package top.niunaijun.blackbox.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.FragmentController;
import android.content.Intent;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;

/** Deliver a reused activity's intent without hidden ActivityThread entry points. */
public final class VirtualNewIntentDispatcher {
    private VirtualNewIntentDispatcher() { }

    public static void deliver(Activity activity, Intent intent, Instrumentation instrumentation) {
        if (activity == null || intent == null || instrumentation == null) {
            throw new IllegalArgumentException("New-intent delivery requires an activity, intent and instrumentation");
        }
        Intent delivered = new Intent(intent);
        delivered.setExtrasClassLoader(activity.getClass().getClassLoader());
        try {
            // Match ActivityThread's delivery ordering. Platform fragments must
            // accept transactions again before the guest's onNewIntent runs.
            FragmentController fragments = (FragmentController) FrameworkServiceCache.get(
                    activity, "android.app.FragmentController");
            fragments.noteStateNotSaved();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot prepare activity for new intent", error);
        }
        // The public API also dispatches ComponentCaller-aware onNewIntent on
        // current Android. It does not require a hidden ReferrerIntent constructor.
        instrumentation.callActivityOnNewIntent(activity, delivered);
    }
}
