package top.niunaijun.blackbox.core.system.os;

import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import java.io.File;
import java.io.IOException;

import black.android.os.storage.BRStorageManager;
import black.android.os.storage.BRStorageVolume;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.fake.provider.FileProvider;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.Slog;


public class BStorageManagerService extends IBStorageManagerService.Stub implements ISystemService {
    private static final BStorageManagerService sService = new BStorageManagerService();

    public static BStorageManagerService get() {
        return sService;
    }

    public BStorageManagerService() {
    }

    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) throws RemoteException {
        if (BRStorageManager.get().getVolumeList(0, 0) == null) {
            if (isTikTokPackage(packageName)) {
                Slog.w("BStorageManager", "profile-crop volume list unavailable package=" + packageName
                        + " userId=" + userId + " source=system");
            }
            return null;
        }
        try {
            StorageVolume[] storageVolumes = BRStorageManager.get().getVolumeList(BUserHandle.getUserId(Process.myUid()), 0);
            if (storageVolumes == null) {
                if (isTikTokPackage(packageName)) {
                    Slog.w("BStorageManager", "profile-crop volume list unavailable package=" + packageName
                            + " userId=" + userId + " source=virtual");
                }
                return null;
            }
            // Keep the framework-visible mount point stable. On Android 17,
            // Environment.isExternalStorageEmulated() resolves the volume by
            // this logical path and throws if it has been replaced with Clony's
            // private backing directory. IOCore redirects file access from the
            // logical mount to the selected virtual user's backing storage.
            File visibleStoragePath = Environment.getExternalStorageDirectory();
            File virtualStoragePath = BEnvironment.getExternalUserDir(userId);
            boolean virtualStorageWritable = isVirtualStorageWritable(userId);
            if (isTikTokPackage(packageName)) {
                Slog.i("BStorageManager", "profile-crop volumes package=" + packageName
                        + " userId=" + userId + " visiblePath=" + visibleStoragePath
                        + " backingPath=" + virtualStoragePath + " backingWritable=" + virtualStorageWritable
                        + " count=" + storageVolumes.length);
            }
            for (StorageVolume storageVolume : storageVolumes) {
                BRStorageVolume.get(storageVolume)._set_mPath(visibleStoragePath);
                if (BuildCompat.isPie()) {
                    BRStorageVolume.get(storageVolume)._set_mInternalPath(visibleStoragePath);
                }
                if (storageVolume.isPrimary() && virtualStorageWritable) {
                    // The guest sees the logical mount path, while Clony owns the
                    // backing directory. Report the volume as mounted only when that
                    // backing directory accepts writes.
                    BRStorageVolume.get(storageVolume)._set_mState(Environment.MEDIA_MOUNTED);
                }
                if (isTikTokPackage(packageName)) {
                    Slog.i("BStorageManager", "profile-crop volume package=" + packageName
                            + " primary=" + storageVolume.isPrimary() + " state=" + storageVolume.getState()
                            + " path=" + BRStorageVolume.get(storageVolume).mPath());
                }
            }
            return storageVolumes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Uri getUriForFile(String file) throws RemoteException {
        return FileProvider.getUriForFile(BlackBoxCore.getContext(), ProxyManifest.getProxyFileProvider(), new File(file));
    }

    public static boolean isVirtualStorageWritable(int userId) {
        File virtualStoragePath = BEnvironment.getExternalUserDir(userId);
        if (!virtualStoragePath.isDirectory() && !virtualStoragePath.mkdirs()) {
            return false;
        }

        File probe = null;
        try {
            probe = File.createTempFile("clony-storage-probe-", ".tmp", virtualStoragePath);
            return true;
        } catch (IOException error) {
            android.util.Log.w("BStorageManager", "profile-crop backing storage probe failed path="
                    + virtualStoragePath + " userId=" + userId, error);
            return false;
        } finally {
            if (probe != null) {
                probe.delete();
            }
        }
    }

    private static boolean isTikTokPackage(String packageName) {
        return packageName != null && (packageName.equals("com.zhiliaoapp.musically")
                || packageName.startsWith("com.zhiliaoapp.musically."));
    }

    @Override
    public void systemReady() {

    }
}
