package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Provides stable, clone-scoped identifiers without exposing host hardware IDs.
 * The installation seed lives in the host app's no-backup private storage.
 */
public final class VirtualDeviceIdentity {
    private static final String TAG = "VirtualDeviceIdentity";
    private static final String SEED_FILE_NAME = "virtual-device-identity.seed";
    private static final String LOCK_FILE_NAME = "virtual-device-identity.lock";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SEED_LENGTH_BYTES = 32;
    private static final int ANDROID_ID_LENGTH = 16;

    private static final Object SEED_LOCK = new Object();
    private static volatile byte[] sSeed;

    private VirtualDeviceIdentity() {
    }

    public static String getAndroidId(Context context, String packageName, int userId) {
        byte[] digest = derive(context, packageName, userId, "android_id");
        if (digest == null) return null;
        return toHex(digest, ANDROID_ID_LENGTH / 2);
    }

    /**
     * Returns a stable pseudonym in a format suitable for the requested
     * telephony identifier. Callers must first let Android enforce access and
     * availability by invoking the underlying telephony service.
     */
    public static String getTelephonyId(
            Context context,
            String packageName,
            int userId,
            String identifierKind
    ) {
        byte[] digest = derive(context, packageName, userId, identifierKind);
        if (digest == null) return null;

        if ("meid".equals(identifierKind)) {
            return toHex(digest, 7).toUpperCase(java.util.Locale.ROOT);
        }

        String digits = toDigits(digest, 15);
        if ("imei".equals(identifierKind) || "device_id".equals(identifierKind)) {
            return withLuhnCheckDigit(digits.substring(0, 14));
        }
        return digits;
    }

    private static byte[] derive(Context context, String packageName, int userId, String kind) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0
                || kind == null || kind.isEmpty()) {
            return null;
        }
        byte[] seed = getSeed(context);
        if (seed == null) return null;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(seed, HMAC_ALGORITHM));
            String input = "clony-virtual-id-v1\u0000" + packageName + "\u0000" + userId
                    + "\u0000" + kind;
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            Slog.w(TAG, "Could not derive a virtual identity: " + error.getClass().getSimpleName());
            return null;
        }
    }

    private static byte[] getSeed(Context context) {
        byte[] cached = sSeed;
        if (cached != null) return cached.clone();

        synchronized (SEED_LOCK) {
            cached = sSeed;
            if (cached != null) return cached.clone();

            File directory = context.getNoBackupFilesDir();
            if (directory == null || (!directory.exists() && !directory.mkdirs())) {
                Slog.w(TAG, "Could not open private identity storage");
                return null;
            }

            File seedPath = new File(directory, SEED_FILE_NAME);
            File lockPath = new File(directory, LOCK_FILE_NAME);
            try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                FileChannel channel = lockFile.getChannel();
                try (FileLock ignored = channel.lock()) {
                    AtomicFile atomicFile = new AtomicFile(seedPath);
                    byte[] existing = readSeed(atomicFile);
                    if (existing != null) {
                        sSeed = existing;
                        return existing.clone();
                    }

                    byte[] generated = new byte[SEED_LENGTH_BYTES];
                    new SecureRandom().nextBytes(generated);
                    FileOutputStream output = atomicFile.startWrite();
                    try {
                        output.write(generated);
                        atomicFile.finishWrite(output);
                    } catch (IOException error) {
                        atomicFile.failWrite(output);
                        throw error;
                    }
                    sSeed = generated;
                    return generated.clone();
                }
            } catch (IOException | SecurityException | java.nio.channels.OverlappingFileLockException error) {
                Slog.w(TAG, "Could not load the persistent identity seed: "
                        + error.getClass().getSimpleName());
                return null;
            }
        }
    }

    private static byte[] readSeed(AtomicFile atomicFile) throws IOException {
        try (FileInputStream input = atomicFile.openRead()) {
            byte[] seed = new byte[SEED_LENGTH_BYTES];
            int offset = 0;
            while (offset < seed.length) {
                int count = input.read(seed, offset, seed.length - offset);
                if (count < 0) return null;
                offset += count;
            }
            return input.read() == -1 ? seed : null;
        } catch (FileNotFoundException missing) {
            return null;
        }
    }

    private static String toHex(byte[] bytes, int byteCount) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(byteCount * 2);
        for (int i = 0; i < byteCount; i++) {
            int value = bytes[i] & 0xff;
            result.append(hex[value >>> 4]).append(hex[value & 0x0f]);
        }
        return result.toString();
    }

    private static String toDigits(byte[] bytes, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append((bytes[i % bytes.length] & 0xff) % 10);
        }
        return result.toString();
    }

    private static String withLuhnCheckDigit(String fourteenDigits) {
        int sum = 0;
        for (int i = 0; i < fourteenDigits.length(); i++) {
            int digit = fourteenDigits.charAt(i) - '0';
            if ((i & 1) == 1) {
                digit *= 2;
                digit = (digit / 10) + (digit % 10);
            }
            sum += digit;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return fourteenDigits + checkDigit;
    }

    static void clearProcessCacheForTesting() {
        synchronized (SEED_LOCK) {
            sSeed = null;
        }
    }
}
