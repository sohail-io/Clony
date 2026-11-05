package com.clony;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import top.niunaijun.blackbox.core.NativeCore;
import top.niunaijun.blackbox.core.IOCore;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.service.IActivityManagerProxy;
import top.niunaijun.blackbox.fake.service.IPackageManagerProxy;
import top.niunaijun.blackbox.fake.service.IPermissionManagerProxy;
import top.niunaijun.blackbox.fake.service.IVibratorServiceProxy;
import top.niunaijun.blackbox.utils.HostRuntimePermissions;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CapturePermissionRegressionTest {
    private static final String CLONE = "com.zhiliaoapp.musically";

    @Test public void packageMappingPreservesPermissionDeviceAndUserArguments() {
        Object[] modern = {CLONE, Manifest.permission.CAMERA, "default:0", 0};
        assertTrue(HostRuntimePermissions.rewritePackage(modern, CLONE, "com.clony"));
        assertArrayEquals(new Object[]{"com.clony", Manifest.permission.CAMERA, "default:0", 0}, modern);

        Object[] legacy = {Manifest.permission.RECORD_AUDIO, CLONE, 0};
        assertTrue(HostRuntimePermissions.rewritePackage(legacy, CLONE, "com.clony"));
        assertArrayEquals(new Object[]{Manifest.permission.RECORD_AUDIO, "com.clony", 0}, legacy);
    }

    @Test public void packageMappingLeavesPlatformAndUnrelatedQueriesUntouched() {
        Object[] platform = {"com.android.settings", Manifest.permission.CAMERA, 0};
        assertFalse(HostRuntimePermissions.rewritePackage(platform, CLONE, "com.clony"));
        assertEquals("com.android.settings", platform[0]);
        Object[] originalApp = {CLONE, Manifest.permission.CAMERA, 0};
        assertFalse(HostRuntimePermissions.rewritePackage(originalApp, null, "com.clony"));
        assertEquals(CLONE, originalApp[0]);
        assertFalse(HostRuntimePermissions.isCaptureOrMediaPermission(Manifest.permission.ACCOUNT_MANAGER));
    }

    @Test public void legacyPackageChecksReturnActualDenialsAndGrantsForCaptureAndMedia() throws Exception {
        FakePermissionService service = new FakePermissionService();
        Method method = FakePermissionService.class.getMethod("checkPackage", String.class, String.class, int.class);
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        for (String permission : permissions) {
            assertTrue(HostRuntimePermissions.isCaptureOrMediaPermission(permission));
            for (MethodHook hook : new MethodHook[]{new IPackageManagerProxy.SimpleAudioPermissionHook(),
                    new IPackageManagerProxy.CheckSelfPermission()}) {
                service.result = PackageManager.PERMISSION_DENIED;
                assertEquals(PackageManager.PERMISSION_DENIED, invoke(hook, service, method,
                        new Object[]{permission, BlackBoxCore.getHostPkg(), 0}));
                service.result = PackageManager.PERMISSION_GRANTED;
                assertEquals(PackageManager.PERMISSION_GRANTED, invoke(hook, service, method,
                        new Object[]{permission, BlackBoxCore.getHostPkg(), 0}));
            }
        }
        assertEquals(permissions.length * 4, service.calls);
    }

    @Test public void legacyRationaleDoesNotHideRecoverableMicrophoneOrMediaDenials() throws Exception {
        FakePermissionService service = new FakePermissionService();
        Method method = FakePermissionService.class.getMethod("rationale", String.class, String.class, int.class);
        for (String permission : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE}) {
            assertEquals(true, invoke(new IPackageManagerProxy.ShouldShowRequestPermissionRationale(),
                    service, method, new Object[]{permission, BlackBoxCore.getHostPkg(), 0}));
        }
        assertEquals(2, service.calls);
    }

    @Test public void activityManagerDoesNotSynthesizeMicrophoneOrMediaGrants() throws Exception {
        FakePermissionService service = new FakePermissionService();
        Method method = FakePermissionService.class.getMethod("checkUid", String.class, int.class, int.class);
        for (String permission : new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE}) {
            service.result = PackageManager.PERMISSION_DENIED;
            assertEquals(PackageManager.PERMISSION_DENIED, invoke(new IActivityManagerProxy.checkPermission(),
                    service, method, new Object[]{permission, android.os.Process.myPid(), BlackBoxCore.getHostUid()}));
        }
        assertEquals(3, service.calls);
    }

    @Test public void modernPermissionQueriesPreserveRealServiceResults() throws Exception {
        FakePermissionService service = new FakePermissionService();
        Method method = FakePermissionService.class.getMethod("checkPackage", String.class, String.class, int.class);
        for (String query : new String[]{"checkPermission", "getPermissionFlags", "getPermissionRequestState"}) {
            service.result = query.equals("checkPermission") ? PackageManager.PERMISSION_DENIED : 64;
            assertEquals(service.result, invoke(new IPermissionManagerProxy.HostPackageQuery(query), service,
                    method, new Object[]{BlackBoxCore.getHostPkg(), Manifest.permission.CAMERA, 0}));
        }
        assertEquals(3, service.calls);
    }

    @Test public void permissionProxyInstallsInBothFrameworkCachesWithoutCallingHiddenStub() throws Exception {
        android.content.Context context = BlackBoxCore.getContext();
        Object manager = context.getSystemService("permission");
        assertNotNull(manager);
        IPermissionManagerProxy proxy = new IPermissionManagerProxy();
        proxy.injectHook();
        Object cached = FrameworkServiceCache.get(manager, "android.permission.IPermissionManager");
        assertNotNull("Permission hook must acquire an existing framework Binder interface", cached);
        assertTrue(Proxy.isProxyClass(cached.getClass()));
        assertSame(proxy, Proxy.getInvocationHandler(cached));
        assertSame("ActivityThread's static cache must also use the hook", cached,
                FrameworkServiceCache.get(Class.forName("android.app.ActivityThread"),
                        "android.permission.IPermissionManager"));
        assertEquals(context.checkSelfPermission(Manifest.permission.CAMERA),
                context.getPackageManager().checkPermission(Manifest.permission.CAMERA, context.getPackageName()));
    }

    @Test public void vibratorProxyReplacesCachedManagerInterface() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31);
        android.content.Context context = BlackBoxCore.getContext();
        Object manager = context.getSystemService("vibrator_manager");
        assertNotNull(manager);
        IVibratorServiceProxy proxy = new IVibratorServiceProxy();
        proxy.injectHook();
        Object cached = FrameworkServiceCache.get(manager, "android.os.IVibratorManagerService");
        assertNotNull(cached);
        assertTrue("The framework must use the hooked Binder, not its original cache",
                Proxy.isProxyClass(cached.getClass()));
        assertSame(proxy, Proxy.getInvocationHandler(cached));
        assertNotNull(((android.os.VibratorManager) manager).getVibratorIds());
    }

    @Test public void nativeServiceBridgeRejectsUnlistedManagersAndInterfaces() {
        assertNull(NativeCore.getFrameworkService(new Object(), "android.permission.IPermissionManager"));
        assertNull(NativeCore.getFrameworkService(BlackBoxCore.getContext().getSystemService("permission"),
                "android.os.IBinder"));
        assertFalse(NativeCore.setFrameworkService(new Object(), "android.permission.IPermissionManager", null));
    }

    @Test public void vibrationUsesHostUidEvenWhenFrameworkRetainsSourceAppUid() {
        for (String method : new String[]{"vibrate", "setAlwaysOnEffect", "performHapticFeedback"}) {
            Object[] args = {10313, 0, "com.clony", "effect"};
            IVibratorServiceProxy.rewriteCallerUid(method, args, 10322);
            assertArrayEquals(new Object[]{10322, 0, "com.clony", "effect"}, args);
        }
        Object[] unrelated = {7};
        IVibratorServiceProxy.rewriteCallerUid("getVibratorInfo", unrelated, 10322);
        assertEquals(7, unrelated[0]);
    }

    @Test public void sharedMediaKeepsFullFilePathAndAppStorageStaysVirtual() {
        String root = "/clony-storage-regression";
        String backing = new java.io.File(BlackBoxCore.getContext().getCacheDir(), "media-regression").getPath();
        IOCore io = IOCore.get();
        io.addRedirect(root, backing);
        io.addBlackRedirect(root + "/Pictures");
        assertEquals(root + "/Pictures/photo.jpg", io.redirectPath(root + "/Pictures/photo.jpg"));
        assertEquals(backing + "/Android/data/app/file", io.redirectPath(root + "/Android/data/app/file"));
        assertEquals(backing + "/Pictures-private/photo.jpg", io.redirectPath(root + "/Pictures-private/photo.jpg"));
        assertEquals(backing + "/Pictures/../Android/data/app/file",
                io.redirectPath(root + "/Pictures/../Android/data/app/file"));
    }

    private static Object invoke(MethodHook hook, Object service, Method method, Object[] args) throws Exception {
        Method entry = hook.getClass().getDeclaredMethod("hook", Object.class, Method.class, Object[].class);
        entry.setAccessible(true);
        return entry.invoke(hook, service, method, args);
    }

    public static class FakePermissionService {
        int result = PackageManager.PERMISSION_DENIED;
        int calls;
        public int checkPackage(String first, String second, int userId) { calls++; return result; }
        public int checkUid(String permission, int pid, int uid) { calls++; return result; }
        public boolean rationale(String permission, String packageName, int userId) { calls++; return true; }
    }
}
