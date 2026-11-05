package top.niunaijun.blackbox.core.system.os;

import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import java.io.File;

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
            return null;
        }
        try {
            StorageVolume[] storageVolumes = BRStorageManager.get().getVolumeList(BUserHandle.getUserId(Process.myUid()), 0);
            if (storageVolumes == null)
                return null;
            // Keep the framework-visible mount point stable. On Android 17,
            // Environment.isExternalStorageEmulated() resolves the volume by
            // this logical path and throws if it has been replaced with Clony's
            // private backing directory. IOCore redirects file access from the
            // logical mount to the selected virtual user's backing storage.
            File visibleStoragePath = Environment.getExternalStorageDirectory();
            File virtualStoragePath = BEnvironment.getExternalUserDir(userId);
            Slog.i("BStorageManager", "Returning virtual storage for userId=" + userId
                    + " visiblePath=" + visibleStoragePath
                    + " backingPath=" + virtualStoragePath);
            for (StorageVolume storageVolume : storageVolumes) {
                BRStorageVolume.get(storageVolume)._set_mPath(visibleStoragePath);
                if (BuildCompat.isPie()) {
                    BRStorageVolume.get(storageVolume)._set_mInternalPath(visibleStoragePath);
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

    @Override
    public void systemReady() {

    }
}
