package com.clony;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.dispatcher.AppServiceDispatcher;
import top.niunaijun.blackbox.core.CrashHandler;
import top.niunaijun.blackbox.core.system.accounts.BAccountManagerService;
import top.niunaijun.blackbox.core.system.accounts.BUserAccounts;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.fake.delegate.ServiceConnectionDelegate;
import top.niunaijun.blackbox.fake.frameworks.BAccountManager;
import top.niunaijun.blackbox.fake.service.IClipboardManagerProxy;
import top.niunaijun.blackbox.fake.service.IActivityManagerProxy;
import top.niunaijun.blackbox.proxy.record.ProxyServiceRecord;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AuthenticationRuntimeRegressionTest {
    @Test public void clipboardRewritesCallerWithoutChangingAttributionOrPayload() {
        Object clip = new Object();
        Object[] args = {clip, "com.zhiliaoapp.musically", "feature-tag", 12345, 0};
        IClipboardManagerProxy.rewriteCaller(args, "com.clony", 12345, 10311);
        assertSame(clip, args[0]);
        assertEquals("com.clony", args[1]);
        assertEquals("feature-tag", args[2]);
        assertEquals(10311, args[3]);
        assertEquals(0, args[4]);
    }

    @Test public void clipboardProxySupportsPasteChecksAndListenerRegistration() {
        IClipboardManagerProxy proxy = new IClipboardManagerProxy();
        proxy.injectHook();
        android.content.ClipboardManager manager = (android.content.ClipboardManager)
                BlackBoxCore.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipboardManager.OnPrimaryClipChangedListener listener = () -> { };
        manager.hasPrimaryClip();
        manager.addPrimaryClipChangedListener(listener);
        manager.removePrimaryClipChangedListener(listener);
    }

    @Test public void keyboardCallerRewritePreservesGuestEditor() {
        android.view.inputmethod.EditorInfo editor = new android.view.inputmethod.EditorInfo();
        editor.packageName = "com.zhiliaoapp.musically";
        editor.fieldId = 42;
        editor.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        android.view.inputmethod.EditorInfo forwarded = top.niunaijun.blackbox.fake.service.IInputMethodManagerProxy.forHost(editor, "com.clony");
        assertEquals("com.clony", forwarded.packageName);
        assertEquals("com.zhiliaoapp.musically", editor.packageName);
        assertEquals(editor.fieldId, forwarded.fieldId);
        assertEquals(editor.inputType, forwarded.inputType);
    }

    @Test public void networkCapabilitiesRewritesOnlyTheClonePackageArgument() {
        Object network = new Object();
        Object[] args = {network, "com.facebook.katana", "network-feature"};
        assertTrue(top.niunaijun.blackbox.fake.service.IConnectivityManagerProxy
                .rewriteVirtualCallerPackage(args, "com.facebook.katana", "com.clony"));
        assertSame(network, args[0]);
        assertEquals("com.clony", args[1]);
        assertEquals("network-feature", args[2]);

        Object[] unrelated = {network, "com.instagram.android", "network-feature"};
        assertFalse(top.niunaijun.blackbox.fake.service.IConnectivityManagerProxy
                .rewriteVirtualCallerPackage(unrelated, "com.facebook.katana", "com.clony"));
        assertEquals("com.instagram.android", unrelated[1]);
    }

    @Test public void filteredCredentialRequestGetsNormalNoCredentialCallback() throws Throwable {
        FakeCredentialCallback callback = new FakeCredentialCallback();
        FakeCredentialRequest request = new FakeCredentialRequest(Collections.singletonList(
                new FakeCredentialOption(Collections.singleton("com.android.credentials"))));
        assertTrue(top.niunaijun.blackbox.fake.service.ICredentialManagerProxy
                .ExecuteGetCredential.completeNoCredentialFallback(request, callback, true, false));
        assertEquals(1, callback.calls.get());
        assertEquals("android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL", callback.type);

        FakeCredentialCallback unrestrictedCallback = new FakeCredentialCallback();
        FakeCredentialRequest unrestricted = new FakeCredentialRequest(Collections.singletonList(
                new FakeCredentialOption(Collections.emptyList())));
        assertFalse(top.niunaijun.blackbox.fake.service.ICredentialManagerProxy
                .ExecuteGetCredential.completeNoCredentialFallback(unrestricted,
                        unrestrictedCallback, true, false));
        assertEquals(0, unrestrictedCallback.calls.get());

        FakeCredentialCallback privilegedCallback = new FakeCredentialCallback();
        assertFalse(top.niunaijun.blackbox.fake.service.ICredentialManagerProxy
                .ExecuteGetCredential.completeNoCredentialFallback(request,
                        privilegedCallback, true, true));
        assertEquals(0, privilegedCallback.calls.get());
    }

    @Test public void staleFacebookActivityMetadataCanBeBoundToVirtualApplication() {
        String packageName = "com.facebook.katana";
        ComponentName component = new ComponentName(packageName,
                "com.facebook.katana.activity.FbMainTabActivity");
        Intent intent = new Intent().setComponent(component);
        assertTrue(top.niunaijun.blackbox.app.VirtualActivityResolver
                .targetsVirtualPackage(intent, packageName));
        assertFalse(top.niunaijun.blackbox.app.VirtualActivityResolver
                .targetsVirtualPackage(intent, "com.instagram.android"));

        ActivityInfo hostInfo = new ActivityInfo();
        hostInfo.packageName = packageName;
        hostInfo.name = component.getClassName();
        hostInfo.applicationInfo = new ApplicationInfo();
        hostInfo.applicationInfo.packageName = packageName;
        hostInfo.applicationInfo.uid = 10314;
        ApplicationInfo virtualApp = new ApplicationInfo();
        virtualApp.packageName = packageName;
        virtualApp.uid = 190314;
        ActivityInfo virtualInfo = top.niunaijun.blackbox.app.VirtualActivityResolver
                .attachVirtualApplication(hostInfo, virtualApp);
        assertNotNull(virtualInfo);
        assertEquals(component.getClassName(), virtualInfo.name);
        assertEquals(190314, virtualInfo.applicationInfo.uid);
        assertEquals(10314, hostInfo.applicationInfo.uid);
    }

    @Test public void installedFacebookCloneResolvesExplicitInternalActivity() throws Exception {
        BlackBoxCore runtime = BlackBoxCore.get();
        String packageName = "com.facebook.katana";
        ComponentName component = new ComponentName(packageName,
                "com.facebook.katana.activity.FbMainTabActivity");
        try {
            BlackBoxCore.getPackageManager().getActivityInfo(component, 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
            org.junit.Assume.assumeNoException("Facebook component is unavailable on this device", missing);
        }

        int userId = 1;
        for (BUserInfo user : runtime.getUsers()) userId = Math.max(userId, user.id + 1);
        assertNotNull(runtime.createUser(userId));
        try {
            top.niunaijun.blackbox.entity.pm.InstallResult installed =
                    runtime.installInstalledPackageAsUser(packageName, userId);
            assertNotNull(installed);
            assertTrue(installed.msg, installed.success);
            ResolveInfo resolved = runtime.getBPackageManager().resolveActivity(
                    new Intent().setComponent(component), 0, null, userId);
            assertNotNull("Explicit Facebook activity must resolve in the clone", resolved);
            assertEquals(packageName, resolved.activityInfo.packageName);
            assertNotEquals(BlackBoxCore.getHostUid(),
                    resolved.activityInfo.applicationInfo.uid);
            assertTrue(resolved.activityInfo.applicationInfo.dataDir
                    .contains("/" + userId + "/" + packageName));
        } finally {
            runtime.deleteUser(userId);
        }
    }

    public static class FakeCredentialRequest {
        private final Iterable<FakeCredentialOption> options;
        FakeCredentialRequest(Iterable<FakeCredentialOption> options) { this.options = options; }
        public Iterable<FakeCredentialOption> getCredentialOptions() { return options; }
    }

    public static class FakeCredentialOption {
        private final Iterable<String> providers;
        FakeCredentialOption(Iterable<String> providers) { this.providers = providers; }
        public Iterable<String> getAllowedProviders() { return providers; }
    }

    public static class FakeCredentialCallback {
        final AtomicInteger calls = new AtomicInteger();
        String type;
        public void onError(String type, String message) {
            this.type = type;
            calls.incrementAndGet();
        }
    }

    @Test public void virtualBroadcastUserMapsToPhysicalHostUser() {
        assertEquals(0, IActivityManagerProxy.BroadcastIntent.rewriteBroadcastUser(-1, 0));
        assertEquals(0, IActivityManagerProxy.BroadcastIntent.rewriteBroadcastUser(13, 0));
        assertEquals(0, IActivityManagerProxy.BroadcastIntent.hostUserId());
    }

    @Test public void virtualGoogleRestrictionsDoNotQueryHostUserManager() {
        assertTrue(top.niunaijun.blackbox.fake.service.IUserManagerProxy
                .shouldReturnEmptyVirtualRestrictions("com.google.android.gms", "com.google.android.gms"));
        assertFalse(top.niunaijun.blackbox.fake.service.IUserManagerProxy
                .shouldReturnEmptyVirtualRestrictions("com.example.other", "com.google.android.gms"));
    }

    @Test public void packageUidLookupUsesTheVirtualApplicationInfoUid() {
        android.content.pm.PackageInfo packageInfo = new android.content.pm.PackageInfo();
        packageInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        packageInfo.applicationInfo.uid = 10313;
        assertEquals(10313, top.niunaijun.blackbox.fake.service.IPackageManagerProxy.GetPackageUid
                .virtualPackageUid(packageInfo));
        assertEquals(-1, top.niunaijun.blackbox.fake.service.IPackageManagerProxy.GetPackageUid
                .virtualPackageUid(null));
        packageInfo.applicationInfo.uid = 0;
        assertEquals(-1, top.niunaijun.blackbox.fake.service.IPackageManagerProxy.GetPackageUid
                .virtualPackageUid(packageInfo));
    }

    @Test public void installedTikTokCloneHasAResolvableVirtualUid() {
        BlackBoxCore runtime = BlackBoxCore.get();
        boolean found = false;
        for (BUserInfo user : runtime.getUsers()) {
            if (!runtime.isInstalled("com.zhiliaoapp.musically", user.id)) continue;
            android.content.pm.PackageInfo packageInfo = runtime.getBPackageManager()
                    .getPackageInfo("com.zhiliaoapp.musically", 0, user.id);
            assertNotNull(packageInfo);
            assertNotNull(packageInfo.applicationInfo);
            int virtualUid = top.niunaijun.blackbox.fake.service.IPackageManagerProxy.GetPackageUid
                    .virtualPackageUid(packageInfo);
            assertEquals(packageInfo.applicationInfo.uid, virtualUid);
            assertNotEquals("The clone must not report Clony's host UID",
                    BlackBoxCore.getHostUid(), virtualUid);
            found = true;
        }
        assertTrue("Expected the saved TikTok clone to remain installed", found);
    }

    @Test public void virtualGoogleAccountPickerHasNoDevicePolicyRestrictions() {
        assertEquals(0, top.niunaijun.blackbox.fake.service.IDevicePolicyManagerProxy
                .virtualDisabledAccountTypes().length);
    }

    @Test public void proxyActivityDoesNotExposeGuestIntentInFrameworkExtras() {
        Intent stub = new Intent();
        Intent target = new Intent("test.guest.activity");
        top.niunaijun.blackbox.proxy.record.ProxyActivityRecord.saveStub(
                stub, target, activityInfo(), new Binder(), 13);
        assertNull("Guest Activity intent must stay in the virtual runtime record",
                stub.getParcelableExtra("_B_|_target_"));
        Parcel parcel = Parcel.obtain();
        try {
            stub.writeToParcel(parcel, 0);
            assertEquals("test.guest.activity", target.getAction());
        } finally { parcel.recycle(); }
    }

    @Test public void callbackPreservesSessionAndResolvedComponent() throws Exception {
        verifyCallback(true);
    }

    @Test public void callbackSupportsOlderFrameworkLayout() throws Exception {
        verifyCallback(false);
    }

    private void verifyCallback(boolean sessionLayout) throws Exception {
        ComponentName component = new ComponentName("com.google.android.gms", "SignInService");
        IBinder service = new Binder();
        IBinder session = new Binder();
        AtomicInteger received = new AtomicInteger();
        Binder receiver = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                data.enforceInterface("android.app.IServiceConnection");
                assertEquals(1, data.readInt());
                assertEquals(component, ComponentName.CREATOR.createFromParcel(data));
                assertSame(service, data.readStrongBinder());
                if (sessionLayout) assertSame(session, data.readStrongBinder());
                assertEquals(0, data.readInt());
                assertEquals(0, data.dataAvail());
                received.incrementAndGet();
                return true;
            }
        };
        ServiceConnectionDelegate.forwardConnection(receiver, component, service, session, false, sessionLayout);
        assertEquals(1, received.get());
        // Exercise the incoming platform transaction as well as its forwarding.
        android.app.IServiceConnection original = android.app.IServiceConnection.Stub.asInterface(receiver);
        android.app.IServiceConnection delegate = ServiceConnectionDelegate.createProxy(original,
                new Intent("test.service").setPackage("com.google.android.gms"), true);
        ServiceConnectionDelegate.forwardConnection(delegate.asBinder(), component, service, session, false, sessionLayout);
        assertEquals(2, received.get());
    }

    @Test public void proxyServiceRecordRetainsProcessConfigurationAfterParceling() {
        AppConfig config = config(42);
        Intent proxy = new Intent();
        ServiceInfo service = service();
        ProxyServiceRecord.saveStub(proxy, new Intent("test.action"), service, new Binder(), 42, 0);
        proxy.putExtra(AppConfig.KEY, config.toBundle());
        Parcel parcel = Parcel.obtain();
        try {
            proxy.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ProxyServiceRecord restored = ProxyServiceRecord.create(Intent.CREATOR.createFromParcel(parcel));
            assertNotNull(restored.mAppConfig);
            AppServiceDispatcher.validateServiceConfig(restored.mAppConfig, restored.mServiceInfo, 42);
            assertEquals(42, restored.mAppConfig.userId);
            assertEquals("com.google.android.gms", restored.mAppConfig.packageName);
        } finally { parcel.recycle(); }
    }

    @Test public void proxyConfigurationIsReadableWithFrameworkClassLoader() {
        Parcel parcel = Parcel.obtain();
        try {
            Intent intent = new Intent().putExtra(AppConfig.KEY, config(42).toBundle());
            intent.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Intent restored = Intent.CREATOR.createFromParcel(parcel);
            restored.setExtrasClassLoader(null);
            assertEquals(42, restored.getBundleExtra(AppConfig.KEY).getInt("userId"));
        } finally { parcel.recycle(); }
    }

    public static class NetworkPolicy {
        boolean called;
        public void setUidCleartextNetworkPolicy(int uid, int policy) { called = true; }
    }

    @Test public void guestStrictModeCannotChangeSharedHostNetworkPolicy() throws Exception {
        NetworkPolicy base = new NetworkPolicy();
        Class<?> hookClass = top.niunaijun.blackbox.fake.service.INetworkManagementServiceProxy.SetUidCleartextNetworkPolicy.class;
        java.lang.reflect.Method hook = hookClass.getDeclaredMethod("hook", Object.class, java.lang.reflect.Method.class, Object[].class);
        hook.setAccessible(true);
        hook.invoke(hookClass.getConstructor().newInstance(), base,
                NetworkPolicy.class.getMethod("setUidCleartextNetworkPolicy", int.class, int.class), new Object[]{10311, 2});
        assertFalse("Guest policy must not alter the shared Android UID policy", base.called);
    }

    public interface RejectedService { void request(); }
    public static class RejectingService implements RejectedService {
        @Override public void request() { throw new SecurityException("Expected service rejection"); }
    }
    public static class RejectedServiceProxy extends top.niunaijun.blackbox.fake.hook.ClassInvocationStub {
        @Override protected Object getWho() { return new RejectingService(); }
        @Override protected void inject(Object base, Object proxy) { }
        @Override public boolean isBadEnv() { return false; }
        public RejectedService service() { return (RejectedService) getProxyInvocation(); }
        @top.niunaijun.blackbox.fake.hook.ProxyMethod("request")
        public static class Request extends top.niunaijun.blackbox.fake.hook.MethodHook {
            @Override protected Object hook(Object who, java.lang.reflect.Method method, Object[] args) throws Throwable {
                return method.invoke(who, args);
            }
        }
    }

    @Test public void hookedServicePreservesOriginalSecurityFailure() {
        RejectedServiceProxy proxy = new RejectedServiceProxy();
        proxy.injectHook();
        try {
            proxy.service().request();
            fail("Service rejection must remain visible");
        } catch (SecurityException expected) {
            assertEquals("Expected service rejection", expected.getMessage());
        }
    }

    @Test public void missingAndCrossUserServiceConfigurationIsRejected() {
        assertRejected(null, service(), 42);
        assertRejected(config(41), service(), 42);
        AppConfig wrongProcess = config(42);
        wrongProcess.processName = "wrong.process";
        assertRejected(wrongProcess, service(), 42);
    }

    private void assertRejected(AppConfig config, ServiceInfo service, int user) {
        try {
            AppServiceDispatcher.validateServiceConfig(config, service, user);
            fail("Mismatched service must not bind");
        } catch (IllegalStateException expected) { }
    }

    @Test public void fatalExceptionReachesPreviousHandlerExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException original = new RuntimeException("Synthetic authentication regression failure");
        CrashHandler handler = new CrashHandler((thread, error) -> {
            assertSame(original, error);
            calls.incrementAndGet();
        });
        handler.uncaughtException(Thread.currentThread(), original);
        assertEquals(1, calls.get());
        assertTrue(top.niunaijun.blackbox.utils.CrashMonitor.getCrashStats().contains("Recovered Crashes: 0"));
    }

    @Test public void missingPackageMetadataReportsFailureInsteadOfNullDereference() {
        try {
            new top.niunaijun.blackbox.app.BActivityThread().handleBindApplication("com.clony.missing.package", "com.clony.missing.package");
            fail("Missing package must fail initialization");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Missing virtual package metadata"));
        }
    }

    @Test public void failedMainThreadInitializationReleasesBackgroundWaiter() {
        long start = android.os.SystemClock.elapsedRealtime();
        try {
            new top.niunaijun.blackbox.app.BActivityThread().bindApplication("com.clony.missing.package", "com.clony.missing.package");
            fail("Missing package must fail initialization");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
            assertTrue("Initialization failure must release its waiter immediately",
                    android.os.SystemClock.elapsedRealtime() - start < 3000);
        }
    }

    @Test public void freshVirtualUserResolvesGoogleSignInAndAuthenticators() throws Exception {
        BlackBoxCore runtime = BlackBoxCore.get();
        int userId = 1;
        for (BUserInfo user : runtime.getUsers()) userId = Math.max(userId, user.id + 1);
        assertNotNull(runtime.createUser(userId));
        try {
            top.niunaijun.blackbox.entity.pm.InstallResult install = runtime.installGoogleSignInServices(userId);
            assertNotNull(install);
            assertTrue(install.msg, install.success);
            ResolveInfo resolved = BlackBoxCore.getBPackageManager().resolveService(new Intent(
                    "com.google.android.gms.auth.api.signin.service.START").setPackage("com.google.android.gms"), 0, null, userId);
            assertNotNull("Google sign-in must resolve inside the virtual user", resolved);
            assertTrue(resolved.serviceInfo.applicationInfo.dataDir.contains("/" + userId + "/com.google.android.gms"));
            assertNull("Resolution must not leak to another user", BlackBoxCore.getBPackageManager()
                    .resolveService(new Intent("com.google.android.gms.auth.api.signin.service.START")
                    .setPackage("com.google.android.gms"), 0, null, userId + 10000));
            ResolveInfo signInUi = BlackBoxCore.getBPackageManager().resolveActivity(new Intent(
                    "com.google.android.gms.auth.GOOGLE_SIGN_IN").setPackage("com.google.android.gms"), 0, null, userId);
            assertNotNull("Google sign-in UI must resolve inside the virtual user", signInUi);
            assertTrue(signInUi.activityInfo.applicationInfo.dataDir.contains("/" + userId + "/com.google.android.gms"));
            AuthenticatorDescription[] types = BAccountManager.get().getService().getAuthenticatorTypes(userId);
            boolean google = false;
            for (AuthenticatorDescription type : types) google |= "com.google".equals(type.type);
            assertTrue("A fresh user must offer Google account creation", google);
        } finally {
            runtime.deleteUser(userId);
        }
    }

    @Test public void accountSessionRetainsTheVirtualUserForAuthenticatorBinding() {
        int userId = 9876;
        BUserAccounts accounts = BAccountManagerService.get().getUserAccounts(userId);
        assertEquals("Authenticator session must bind in its owning virtual user", userId, accounts.userId);
    }

    @Test public void accountManagerPermissionIsScopedToVirtualGoogleServices() {
        String permission = android.Manifest.permission.ACCOUNT_MANAGER;
        int hostUid = BlackBoxCore.getHostUid();
        assertTrue(IActivityManagerProxy.shouldGrantVirtualAccountManagerPermission(
                permission, "com.google.android.gms", hostUid, hostUid));
        assertFalse(IActivityManagerProxy.shouldGrantVirtualAccountManagerPermission(
                permission, "com.zhiliaoapp.musically", hostUid, hostUid));
        assertFalse(IActivityManagerProxy.shouldGrantVirtualAccountManagerPermission(
                permission, "com.google.android.gms", hostUid + 1, hostUid));
        assertFalse(IActivityManagerProxy.shouldGrantVirtualAccountManagerPermission(
                android.Manifest.permission.GET_ACCOUNTS, "com.google.android.gms", hostUid, hostUid));
    }

    private static AppConfig config(int userId) {
        AppConfig config = new AppConfig();
        config.packageName = "com.google.android.gms";
        config.processName = "com.google.android.gms";
        config.userId = userId;
        config.token = new Binder();
        return config;
    }

    private static ServiceInfo service() {
        ServiceInfo service = new ServiceInfo();
        service.packageName = "com.google.android.gms";
        service.processName = "com.google.android.gms";
        service.name = "SignInService";
        service.applicationInfo = new android.content.pm.ApplicationInfo();
        service.applicationInfo.packageName = service.packageName;
        return service;
    }

    private static android.content.pm.ActivityInfo activityInfo() {
        android.content.pm.ActivityInfo activity = new android.content.pm.ActivityInfo();
        activity.packageName = "com.google.android.gms";
        activity.name = "SignInActivity";
        activity.applicationInfo = new android.content.pm.ApplicationInfo();
        activity.applicationInfo.packageName = activity.packageName;
        return activity;
    }
}
