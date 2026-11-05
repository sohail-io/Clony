package top.niunaijun.blackbox.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;

/** Routes virtual account and Google Sign-In UI through Clony's account runtime. */
public final class VirtualAccountActivityRouter {
    private static final String TAG = "VirtualAccountRouter";
    private static final String CHOOSE_TYPE_AND_ACCOUNT =
            "android.accounts.ChooseTypeAndAccountActivity";
    private static final String CHOOSE_ACCOUNT = "android.accounts.ChooseAccountActivity";
    private static final String ADD_ACCOUNT_SETTINGS = "android.settings.ADD_ACCOUNT_SETTINGS";
    private static final String GOOGLE_SERVICES_PACKAGE = "com.google.android.gms";
    private static final String GOOGLE_ADD_ACCOUNT_ACTIVITY =
            "com.google.android.gms.auth.uiflows.addaccount.AccountIntroActivity";

    private VirtualAccountActivityRouter() {
    }

    /**
     * Rewrites account activities that belong to Android itself. The original
     * Android picker cannot see accounts held in BlackBox's per-clone store.
     */
    public static Intent route(Context context, Intent source, int virtualUserId) {
        if (context == null || source == null || virtualUserId < 0) {
            return source;
        }

        if (isGoogleAuthIntent(source)) {
            ComponentName observedComponent = source.getComponent();
            Log.i(TAG, "Observed clone Google-auth handoff; action=" + source.getAction()
                    + ", package=" + source.getPackage()
                    + ", component=" + (observedComponent == null ? "<implicit>"
                    : observedComponent.flattenToShortString()));
        }

        ComponentName component = source.getComponent();
        String className = component != null ? component.getClassName() : null;
        if (CHOOSE_TYPE_AND_ACCOUNT.equals(className) || CHOOSE_ACCOUNT.equals(className)) {
            Intent picker = new Intent(source);
            picker.setComponent(new ComponentName(context, VirtualAccountChooserActivity.class));
            picker.setPackage(context.getPackageName());
            picker.putExtra(VirtualAccountChooserActivity.EXTRA_VIRTUAL_USER_ID, virtualUserId);
            Log.i(TAG, "Routed Android account picker to clone account store; user="
                    + virtualUserId + ", requested=" + className);
            return picker;
        }

        if (ADD_ACCOUNT_SETTINGS.equals(source.getAction())) {
            ComponentName googleAccountSetup = new ComponentName(
                    GOOGLE_SERVICES_PACKAGE, GOOGLE_ADD_ACCOUNT_ACTIVITY);
            try {
                ActivityInfo activity = context.getPackageManager().getActivityInfo(googleAccountSetup, 0);
                if (activity.exported) {
                    Intent setup = new Intent();
                    setup.setComponent(googleAccountSetup);
                    setup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Log.i(TAG, "Routed add-account settings to Google Play services");
                    return setup;
                }
                Log.w(TAG, "Google Play services account setup is not exported; keeping system intent");
            } catch (Exception unavailable) {
                Log.i(TAG, "Google Play services account setup unavailable; keeping system intent");
            }
        }

        return source;
    }

    private static boolean isGoogleAuthIntent(Intent intent) {
        String action = intent.getAction();
        String packageName = intent.getPackage();
        ComponentName component = intent.getComponent();
        String componentPackage = component == null ? null : component.getPackageName();
        String componentClass = component == null ? null : component.getClassName();
        return (action != null && (action.contains("GOOGLE_SIGN_IN")
                || action.contains("SIGN_IN") || action.contains("ACCOUNT")
                || action.contains("CREDENTIAL")))
                || GOOGLE_SERVICES_PACKAGE.equals(packageName)
                || GOOGLE_SERVICES_PACKAGE.equals(componentPackage)
                || (componentClass != null && componentClass.contains("SignIn"));
    }
}
