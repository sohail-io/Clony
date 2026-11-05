package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.NativeUtils;


public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        List<File> sourceApks = new ArrayList<>();
        sourceApks.add(new File(ps.pkg.baseCodePath));
        if (ps.pkg.splitCodePaths != null) {
            for (String splitPath : ps.pkg.splitCodePaths) {
                if (splitPath != null && !splitPath.isEmpty()) sourceApks.add(new File(splitPath));
            }
        }
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                for (File sourceApk : sourceApks) {
                    NativeUtils.copyNativeLib(sourceApk, BEnvironment.getAppLibDir(ps.pkg.packageName));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(ps.pkg.packageName);
            try {
                if (option.isFlag(InstallOption.FLAG_URI_FILE)) {
                    boolean b = FileUtils.renameTo(origFile, newFile);
                    if (!b) {
                        FileUtils.copyFile(origFile, newFile);
                    }
                } else {
                    FileUtils.copyFile(origFile, newFile);
                }
                newFile.setReadOnly();
                
                ps.pkg.baseCodePath = newFile.getAbsolutePath();

                String[] sourceSplitPaths = ps.pkg.splitCodePaths == null
                        ? new String[0] : ps.pkg.splitCodePaths;
                String[] splitNames = ps.pkg.splitNames == null
                        ? new String[0] : ps.pkg.splitNames;
                String[] copiedSplitPaths = new String[sourceSplitPaths.length];
                for (int i = 0; i < sourceSplitPaths.length; i++) {
                    File sourceSplit = new File(sourceSplitPaths[i]);
                    String splitName = i < splitNames.length ? splitNames[i] : "split_" + i;
                    String safeSplitName = splitName == null
                            ? "split_" + i : splitName.replaceAll("[^A-Za-z0-9_.-]", "_");
                    File copiedSplit = new File(BEnvironment.getAppDir(ps.pkg.packageName),
                            "split_" + safeSplitName + ".apk");
                    FileUtils.copyFile(sourceSplit, copiedSplit);
                    copiedSplit.setReadOnly();
                    copiedSplitPaths[i] = copiedSplit.getAbsolutePath();
                }
                ps.pkg.splitCodePaths = copiedSplitPaths;
                if (ps.pkg.applicationInfo != null) {
                    ps.pkg.applicationInfo.splitNames = ps.pkg.splitNames == null
                            ? null : ps.pkg.splitNames.clone();
                    ps.pkg.applicationInfo.splitSourceDirs = copiedSplitPaths.clone();
                    ps.pkg.applicationInfo.splitPublicSourceDirs = copiedSplitPaths.clone();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
            
        }
        return 0;
    }
}
