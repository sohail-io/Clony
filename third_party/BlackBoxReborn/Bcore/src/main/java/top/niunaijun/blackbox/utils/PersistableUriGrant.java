package top.niunaijun.blackbox.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.LinkedHashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;

/** Retains picker/document URI access before the guest receives an activity result. */
public final class PersistableUriGrant {
    private static final String TAG = "PickerUriGrant";

    private PersistableUriGrant() { }

    public static void preserve(Intent result) {
        if (result == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        int accessFlags = result.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (accessFlags == 0) {
            return;
        }

        Set<Uri> uris = new LinkedHashSet<>();
        if (result.getData() != null) {
            uris.add(result.getData());
        }
        ClipData clipData = result.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }
        try {
            if (result.getExtras() != null) {
                Object stream = result.getExtras().get(Intent.EXTRA_STREAM);
                if (stream instanceof Uri) {
                    uris.add((Uri) stream);
                } else if (stream instanceof Iterable<?>) {
                    for (Object item : (Iterable<?>) stream) {
                        if (item instanceof Uri) {
                            uris.add((Uri) item);
                        }
                    }
                } else if (stream instanceof Uri[]) {
                    for (Uri uri : (Uri[]) stream) {
                        if (uri != null) {
                            uris.add(uri);
                        }
                    }
                }
            }
        } catch (RuntimeException error) {
            android.util.Log.w(TAG, "Could not read selected media URIs from result extras", error);
        }

        Context hostContext = BlackBoxCore.getContext();
        if (hostContext == null) {
            return;
        }
        for (Uri uri : uris) {
            try {
                hostContext.getContentResolver().takePersistableUriPermission(uri, accessFlags);
            } catch (SecurityException ignored) {
                // ACTION_GET_CONTENT can return a temporary grant. Photo Picker and
                // document providers may permit persistence without a persistable flag.
                android.util.Log.d(TAG, "Picker URI is temporary or non-persistable: "
                        + uri.getAuthority());
            } catch (RuntimeException error) {
                android.util.Log.w(TAG, "Could not preserve picker URI access for authority "
                        + uri.getAuthority(), error);
            }
        }
    }
}
