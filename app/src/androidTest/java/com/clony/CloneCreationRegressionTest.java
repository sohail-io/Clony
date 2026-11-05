package com.clony;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.entity.pm.InstallResult;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CloneCreationRegressionTest {
    @Test public void additionalCopyPreservesSharedPackageFiles() {
        BlackBoxCore runtime = BlackBoxCore.get();
        String pkg = "com.zhiliaoapp.musically";
        File apk = BEnvironment.getBaseApkDir(pkg);
        assertTrue("Existing TikTok clone required", apk.isFile());
        long modified = apk.lastModified();
        long length = apk.length();
        File marker = new File(BEnvironment.getAppDir(pkg), "clone-creation-regression-marker");
        int userId = 1;
        for (BUserInfo user : runtime.getUsers()) userId = Math.max(userId, user.id + 1);
        boolean created = false;
        try {
            assertTrue(marker.createNewFile());
            assertNotNull(runtime.createUser(userId));
            created = true;
            InstallResult result = runtime.installInstalledPackageAsUser(pkg, userId);
            assertNotNull(result);
            assertTrue(result.msg, result.success);
            assertTrue(runtime.isInstalled(pkg, userId));
            assertTrue("Adding a copy deleted the shared package directory", marker.isFile());
            assertEquals("Shared APK was rewritten", modified, apk.lastModified());
            assertEquals(length, apk.length());
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            if (created) runtime.deleteUser(userId);
            marker.delete();
        }
    }
}
