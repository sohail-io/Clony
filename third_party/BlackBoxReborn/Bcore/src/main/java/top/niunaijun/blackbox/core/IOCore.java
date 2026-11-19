package top.niunaijun.blackbox.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Process;
import android.text.TextUtils;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import black.android.app.BRContextImplKitkat;
import top.niunaijun.blackbox.BlackBoxCore;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.CloneHardwareIdentityStore;
import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.TrieTree;


@SuppressLint("SdCardPath")
public class IOCore {
    public static final String TAG = "IOCore";

    private static final IOCore sIOCore = new IOCore();
    private static final TrieTree mTrieTree = new TrieTree();
    private static final TrieTree sBlackTree = new TrieTree();
    private final Map<String, String> mRedirectMap = new LinkedHashMap<>();

    private static final Map<String, Map<String, String>> sCachePackageRedirect = new HashMap<>();

    public static IOCore get() {
        return sIOCore;
    }

    
    public void addRedirect(String origPath, String redirectPath) {
        if (TextUtils.isEmpty(origPath) || TextUtils.isEmpty(redirectPath))
            return;
        if (origPath.equals(redirectPath)) {
            mRedirectMap.remove(origPath);
            NativeCore.removeIORule(origPath);
            return;
        }
        
        mTrieTree.add(origPath);
        mRedirectMap.put(origPath, redirectPath);
        File redirectFile = new File(redirectPath);
        if (!redirectFile.exists()) {
            FileUtils.mkdirs(redirectPath);
        }
        NativeCore.addIORule(origPath, redirectPath);
    }

    public void addBlackRedirect(String path) {
        if (TextUtils.isEmpty(path))
            return;
        sBlackTree.add(path);
    }

    public String redirectPath(String path) {
        if (TextUtils.isEmpty(path))
            return path;
        if (path.contains("/blackbox/")) {
            return path;
        }
        String search = sBlackTree.search(path);
        if (!TextUtils.isEmpty(search)
                && (path.equals(search) || path.startsWith(search + "/"))
                && !path.contains("/../") && !path.endsWith("/.."))
            return path;

        
        String key = mTrieTree.search(path);
        String mappedPath = TextUtils.isEmpty(key) ? null : mRedirectMap.get(key);
        if (!TextUtils.isEmpty(mappedPath) && !key.equals(mappedPath))
            path = path.replace(key, mappedPath);

        return path;
    }

    public File redirectPath(File path) {
        if (path == null)
            return null;
        String pathStr = path.getAbsolutePath();
        return new File(redirectPath(pathStr));
    }

    public String redirectPath(String path, Map<String, String> rule) {
        if (TextUtils.isEmpty(path))
            return path;

        
        String key = mTrieTree.search(path);
        String mappedPath = TextUtils.isEmpty(key) ? null : rule.get(key);
        if (!TextUtils.isEmpty(mappedPath) && !key.equals(mappedPath))
            path = path.replace(key, mappedPath);

        return path;
    }

    public File redirectPath(File path, Map<String, String> rule) {
        if (path == null)
            return null;
        String pathStr = path.getAbsolutePath();
        return new File(redirectPath(pathStr, rule));
    }

    

    public void enableRedirect(Context context) {
        Map<String, String> rule = new LinkedHashMap<>();
        Set<String> blackRule = new HashSet<>();
        String packageName = context.getPackageName();
        CloneHardwareIdentityStore.Options hardwareOptions = CloneHardwareIdentityStore.read(
                BlackBoxCore.getContext(), packageName, BlackBoxCore.getUserId());
        CloneNetworkIdentityStore.Options networkOptions = CloneNetworkIdentityStore.read(
                BlackBoxCore.getContext(), packageName, BlackBoxCore.getUserId());
        NativeCore.setHideCpuGpuInfo(hardwareOptions.hideCpuGpuInfo);
        NativeCore.setHideSimOperatorInfo(networkOptions.hideSimOperatorInfo);

        try {
            ApplicationInfo packageInfo = BlackBoxCore.getBPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA, BlackBoxCore.getUserId());
            int systemUserId = BlackBoxCore.getHostUserId();
            rule.put(String.format("/data/data/%s/lib", packageName), packageInfo.nativeLibraryDir);
            rule.put(String.format("/data/user/%d/%s/lib", systemUserId, packageName), packageInfo.nativeLibraryDir);

            rule.put(String.format("/data/data/%s", packageName), packageInfo.dataDir);
            rule.put(String.format("/data/user/%d/%s", systemUserId, packageName), packageInfo.dataDir);

            
            File profilesRoot = new File(BEnvironment.getVirtualRoot(), "profiles");
            FileUtils.mkdirs(profilesRoot.getAbsolutePath());
            
            rule.put("/data/misc/profiles", profilesRoot.getAbsolutePath());

            File profilesCurDir = new File(profilesRoot, String.format("cur/%d/%s", BlackBoxCore.getUserId(), packageName));
            File profilesRefDir = new File(profilesRoot, String.format("ref/%d/%s", BlackBoxCore.getUserId(), packageName));
            FileUtils.mkdirs(profilesCurDir.getAbsolutePath());
            FileUtils.mkdirs(profilesRefDir.getAbsolutePath());
            rule.put(String.format("/data/misc/profiles/cur/%d/%s", BlackBoxCore.getUserId(), packageName), profilesCurDir.getAbsolutePath());
            rule.put(String.format("/data/misc/profiles/ref/%d/%s", BlackBoxCore.getUserId(), packageName), profilesRefDir.getAbsolutePath());

            // Build the virtual external-storage mapping even when the host or guest
            // Context reports a null external cache directory. TikTok's crop flow
            // checks external cache/files before it creates its output directory.
            File external = BEnvironment.getExternalUserDir(BlackBoxCore.getUserId());
            if (external != null) {
                rule.put("/sdcard", external.getAbsolutePath());
                rule.put(String.format("/storage/emulated/%d", systemUserId), external.getAbsolutePath());

                // Keep user-facing media folders on the device's shared volume so
                // media pickers can see photos and videos stored by the user.
                // Android/media stays redirected with Android/data and Android/obb
                // because apps can keep clone-specific state there.
                for (String directory : new String[]{
                        "DCIM",
                        "Pictures",
                        "Movies",
                        "Download"
                }) {
                    blackRule.add("/sdcard/" + directory);
                    blackRule.add(String.format("/storage/emulated/%d/%s", systemUserId, directory));
                }
            }
            if (BlackBoxCore.get().isHideRoot()) {
                hideRoot(rule);
            }
            proc(rule);
            if (hardwareOptions.hideCpuGpuInfo) {
                addHardwareIdentityRedirects(rule, context);
            } else {
                addHardwareIdentityPassthroughs(rule);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (String key : rule.keySet()) {
            get().addRedirect(key, rule.get(key));
        }
        for (String s : blackRule) {
            get().addBlackRedirect(s);
        }
        if (isTikTokPackage(packageName)) {
            android.util.Log.i(TAG, "profile-crop-native-hooks enable begin package=" + packageName
                    + " userId=" + BlackBoxCore.getUserId());
        }
        NativeCore.enableIO();
        if (isTikTokPackage(packageName)) {
            android.util.Log.i(TAG, "profile-crop-native-hooks enable returned package=" + packageName
                    + " userId=" + BlackBoxCore.getUserId());
        }
        exposeVirtualExternalDirs(context, packageName);
    }

    private void addHardwareIdentityRedirects(Map<String, String> rule, Context context) {
        File hardwareRoot = new File(context.getFilesDir(), ".clony/hardware-identity");
        File gpuRoot = new File(hardwareRoot, "gpu");
        File cpuInfo = new File(hardwareRoot, "cpuinfo");
        if (!gpuRoot.mkdirs() && !gpuRoot.isDirectory()) {
            android.util.Log.w(TAG, "Could not create generic GPU identity directory");
        }

        try {
            writeGenericCpuInfo(cpuInfo);
            rule.put("/proc/cpuinfo", cpuInfo.getAbsolutePath());
            rule.put("/sys/class/kgsl", new File(gpuRoot, "kgsl").getAbsolutePath());
            rule.put("/sys/class/misc/mali0", new File(gpuRoot, "mali0").getAbsolutePath());
            rule.put("/sys/devices/platform/kgsl-3d0", new File(gpuRoot, "kgsl-device").getAbsolutePath());
        } catch (IOException error) {
            android.util.Log.w(TAG, "Could not prepare generic hardware identity files", error);
        }
    }

    private void addHardwareIdentityPassthroughs(Map<String, String> rule) {
        // The native process can be reused for another virtual copy. Reset shared
        // hardware paths so a prior copy's masking does not bleed into this one.
        rule.put("/proc/cpuinfo", "/proc/cpuinfo");
        rule.put("/sys/class/kgsl", "/sys/class/kgsl");
        rule.put("/sys/class/misc/mali0", "/sys/class/misc/mali0");
        rule.put("/sys/devices/platform/kgsl-3d0", "/sys/devices/platform/kgsl-3d0");
    }

    private void writeGenericCpuInfo(File destination) throws IOException {
        int processorCount = 1;
        File source = new File("/proc/cpuinfo");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            int highestProcessor = -1;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator < 0 || !"processor".equalsIgnoreCase(line.substring(0, separator).trim())) {
                    continue;
                }
                try {
                    highestProcessor = Math.max(highestProcessor,
                            Integer.parseInt(line.substring(separator + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // Keep scanning; a generic single-processor view is used if no count is readable.
                }
            }
            processorCount = Math.max(1, Math.min(64, highestProcessor + 1));
        } catch (IOException ignored) {
            // A minimal generic view is still useful when procfs cannot be read.
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create hardware identity directory");
        }
        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            for (int index = 0; index < processorCount; index++) {
                String block = "processor\t: " + index + "\n"
                        + "Processor\t: Generic Processor\n"
                        + "BogoMIPS\t: 0.00\n\n";
                output.write(block.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public void exposeVirtualExternalDirs(Context context, String packageName) {
        try {
            Context contextImpl = context;
            while (contextImpl instanceof android.content.ContextWrapper) {
                Context baseContext = ((android.content.ContextWrapper) contextImpl).getBaseContext();
                if (baseContext == null || baseContext == contextImpl) {
                    break;
                }
                contextImpl = baseContext;
            }

            File backingAppDir = BEnvironment.getExternalDataDir(packageName, BlackBoxCore.getUserId());
            File backingCacheDir = new File(backingAppDir, "cache");
            File backingFilesDir = new File(backingAppDir, "files");
            FileUtils.mkdirs(backingCacheDir);
            FileUtils.mkdirs(backingFilesDir);
            File avatarCropCacheDir = new File(backingCacheDir, "profileHeader");
            if (isTikTokPackage(packageName)) {
                FileUtils.mkdirs(avatarCropCacheDir);
                android.util.Log.i(TAG, "profile-crop-cache prepared package=" + packageName
                        + " userId=" + BlackBoxCore.getUserId()
                        + " path=" + avatarCropCacheDir.getAbsolutePath()
                        + " exists=" + avatarCropCacheDir.isDirectory()
                        + " writable=" + avatarCropCacheDir.canWrite());
            }

            File visibleAppDir = new File(
                    new File(Environment.getExternalStorageDirectory(), "Android/data"), packageName);
            File visibleCacheDir = new File(visibleAppDir, "cache");
            File visibleFilesDir = new File(visibleAppDir, "files");
            BRContextImplKitkat.get(contextImpl)._set_mExternalCacheDirs(new File[]{visibleCacheDir});
            BRContextImplKitkat.get(contextImpl)._set_mExternalFilesDirs(new File[]{visibleFilesDir});
            if (isTikTokPackage(packageName)) {
                android.util.Log.i(TAG, "profile-crop-context package=" + packageName
                        + " userId=" + BlackBoxCore.getUserId()
                        + " context=" + contextImpl.getClass().getName()
                        + " cache=" + visibleCacheDir.getAbsolutePath()
                        + " cacheBackingExists=" + backingCacheDir.isDirectory()
                        + " cacheBackingWritable=" + backingCacheDir.canWrite()
                        + " files=" + visibleFilesDir.getAbsolutePath()
                        + " filesBackingExists=" + backingFilesDir.isDirectory()
                        + " filesBackingWritable=" + backingFilesDir.canWrite());
            }
        } catch (Throwable error) {
            android.util.Log.w(TAG, "Could not expose virtual external app directories for " + packageName, error);
        }
    }

    private static boolean isTikTokPackage(String packageName) {
        return packageName != null && (packageName.equals("com.zhiliaoapp.musically")
                || packageName.startsWith("com.zhiliaoapp.musically."));
    }

    private void hideRoot(Map<String, String> rule) {
        rule.put("/system/app/Superuser.apk", "/system/app/Superuser.apk-fake");
        rule.put("/sbin/su", "/sbin/su-fake");
        rule.put("/system/bin/su", "/system/bin/su-fake");
        rule.put("/system/xbin/su", "/system/xbin/su-fake");
        rule.put("/data/local/xbin/su", "/data/local/xbin/su-fake");
        rule.put("/data/local/bin/su", "/data/local/bin/su-fake");
        rule.put("/system/sd/xbin/su", "/system/sd/xbin/su-fake");
        rule.put("/system/bin/failsafe/su", "/system/bin/failsafe/su-fake");
        rule.put("/data/local/su", "/data/local/su-fake");
        rule.put("/su/bin/su", "/su/bin/su-fake");
    }

    private void proc(Map<String, String> rule) {
        int appPid = BlackBoxCore.getAppPid();
        int pid = Process.myPid();
        String selfProc = "/proc/self/";
        String proc = "/proc/" + pid + "/";

        String cmdline = new File(BEnvironment.getProcDir(appPid), "cmdline").getAbsolutePath();
        rule.put(proc + "cmdline", cmdline);
        rule.put(selfProc + "cmdline", cmdline);
    }
}
