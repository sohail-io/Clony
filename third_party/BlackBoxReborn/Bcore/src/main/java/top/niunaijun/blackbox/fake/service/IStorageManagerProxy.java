package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.os.Environment;
import android.os.storage.StorageVolume;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import black.android.os.BRServiceManager;
import black.android.os.mount.BRIMountServiceStub;
import black.android.os.storage.BRIStorageManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IStorageManagerProxy extends BinderInvocationStub {

    public IStorageManagerProxy() {
        super(BRServiceManager.get().getService("mount"));
    }

    @Override
    protected Object getWho() {
        IInterface mount;
        if (BuildCompat.isOreo()) {
            mount = BRIStorageManagerStub.get().asInterface(BRServiceManager.get().getService("mount"));
        } else {
            mount = BRIMountServiceStub.get().asInterface(BRServiceManager.get().getService("mount"));
        }
        return mount;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("mount");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getVolumeList")
    public static class GetVolumeList extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null) {
                StorageVolume[] volumeList = BlackBoxCore.getBStorageManager().getVolumeList(BActivityThread.getBUid(), null, 0, BActivityThread.getUserId());
                if (volumeList == null) {
                    return method.invoke(who, args);
                }
                return volumeList;
            }
            try {
                int uid = (int) args[0];
                String packageName = (String) args[1];
                int flags = (int) args[2];
                StorageVolume[] volumeList = BlackBoxCore.getBStorageManager().getVolumeList(uid, packageName, flags, BActivityThread.getUserId());
                if (volumeList == null) {
                    return method.invoke(who, args);
                }
                return volumeList;
            } catch (Throwable t) {
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("mkdirs")
    public static class mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null || args.length < 2 || !(args[0] instanceof String) || !(args[1] instanceof String)) {
                throw new IllegalArgumentException("Unexpected StorageManager.mkdirs arguments");
            }

            String callingPackage = (String) args[0];
            String appPackage = BActivityThread.getAppPackageName();
            String hostPackage = BlackBoxCore.getHostPkg();
            if (appPackage == null ||
                    (!appPackage.equals(callingPackage) && !hostPackage.equals(callingPackage))) {
                throw new SecurityException("Cannot create external directories for another package");
            }

            String requestedPath = (String) args[1];
            Path normalizedPath = Paths.get(requestedPath).toAbsolutePath().normalize();
            String visibleRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
            Path allowedData = Paths.get(visibleRoot, "Android", "data", appPackage).normalize();
            Path allowedObb = Paths.get(visibleRoot, "Android", "obb", appPackage).normalize();
            Path allowedMedia = Paths.get(visibleRoot, "Android", "media", appPackage).normalize();
            if (!isWithin(normalizedPath, allowedData) &&
                    !isWithin(normalizedPath, allowedObb) &&
                    !isWithin(normalizedPath, allowedMedia)) {
                throw new SecurityException("Cannot create an external directory outside the calling app");
            }

            File virtualUserRoot = BEnvironment.getExternalUserDir(BActivityThread.getUserId());
            String canonicalRoot = virtualUserRoot.getCanonicalPath() + File.separator;
            Path visibleRootPath = Paths.get(visibleRoot).toAbsolutePath().normalize();
            Path virtualDirectoryPath = virtualUserRoot.toPath().resolve(visibleRootPath.relativize(normalizedPath));
            File directory = virtualDirectoryPath.toFile();
            String canonicalDirectory = directory.getCanonicalPath();
            if (!canonicalDirectory.startsWith(canonicalRoot)) {
                throw new SecurityException("External directory escaped the virtual user's storage");
            }

            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IllegalStateException("Could not create virtual external directory");
            }
            return method.getReturnType() == Void.TYPE ? null : 0;
        }

        private static boolean isWithin(Path path, Path root) {
            return path.equals(root) || path.startsWith(root);
        }
    }
}
