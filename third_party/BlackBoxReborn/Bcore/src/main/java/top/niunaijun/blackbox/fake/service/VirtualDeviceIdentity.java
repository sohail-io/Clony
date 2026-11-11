package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.util.AtomicFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Provides stable, clone-scoped identifiers without exposing host hardware IDs.
 * Android IDs, new-clone telephony IDs, and the legacy derivation seed live in no-backup storage.
 */
public final class VirtualDeviceIdentity {
    private static final String TAG = "VirtualDeviceIdentity";
    private static final String SEED_FILE_NAME = "virtual-device-identity.seed";
    private static final String LOCK_FILE_NAME = "virtual-device-identity.lock";
    private static final String ANDROID_ID_LOCK_FILE_NAME = "virtual-android-id.lock";
    private static final String TELEPHONY_ID_LOCK_FILE_NAME = "virtual-telephony-id.lock";
    private static final String ANDROID_ID_FILE_PREFIX = "virtual-android-id-";
    // v2 files are only written by clone creation; legacy clones keep their old HMAC IDs.
    private static final String TELEPHONY_ID_FILE_PREFIX = "virtual-telephony-id-v2-";
    private static final String PREVIOUS_TELEPHONY_ID_FILE_PREFIX = "virtual-telephony-id-";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SEED_LENGTH_BYTES = 32;
    private static final int ANDROID_ID_LENGTH = 16;

    private static final Object SEED_LOCK = new Object();
    private static final Object ANDROID_ID_LOCK = new Object();
    private static final Object TELEPHONY_ID_LOCK = new Object();
    private static volatile byte[] sSeed;
    private static final ConcurrentHashMap<String, String> sAndroidIds = new ConcurrentHashMap<>();

    private VirtualDeviceIdentity() {
    }

    public static String getAndroidId(Context context, String packageName, int userId) {
        return getOrCreateAndroidId(context, packageName, userId, false, true);
    }

    /** Creates a fresh random Android ID at clone creation, replacing any stale value. */
    public static String createAndroidId(Context context, String packageName, int userId) {
        return getOrCreateAndroidId(context, packageName, userId, true, false);
    }

    private static String getOrCreateAndroidId(
            Context context, String packageName, int userId,
            boolean forceRandom, boolean preserveLegacyValue) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return null;
        }
        String cloneKey = packageName + ":" + userId;
        synchronized (ANDROID_ID_LOCK) {
            String cached = sAndroidIds.get(cloneKey);
            if (!forceRandom && cached != null) return cached;
            if (forceRandom) sAndroidIds.remove(cloneKey);
            String generatedOrStored = readOrCreateAndroidId(
                    context, packageName, userId, forceRandom, preserveLegacyValue);
            if (generatedOrStored != null) sAndroidIds.put(cloneKey, generatedOrStored);
            return generatedOrStored;
        }
    }

    /** Removes the generated value after its virtual user is deleted. */
    public static boolean deleteAndroidId(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        String cloneKey = packageName + ":" + userId;
        synchronized (ANDROID_ID_LOCK) {
            sAndroidIds.remove(cloneKey);
            try {
                File directory = context.getNoBackupFilesDir();
                if (directory == null || !directory.exists()) return true;
                File lockPath = new File(directory, ANDROID_ID_LOCK_FILE_NAME);
                File androidIdPath = androidIdFile(directory, packageName, userId);
                try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                    FileChannel channel = lockFile.getChannel();
                    try (FileLock ignored = channel.lock()) {
                        new AtomicFile(androidIdPath).delete();
                    }
                }
                return true;
            } catch (IOException | SecurityException | GeneralSecurityException
                    | java.nio.channels.OverlappingFileLockException error) {
                Slog.w(TAG, "Could not remove a clone Android ID: "
                        + error.getClass().getSimpleName());
                return false;
            }
        }
    }

    private static String readOrCreateAndroidId(
            Context context, String packageName, int userId,
            boolean forceRandom, boolean preserveLegacyValue) {
        try {
            File directory = context.getNoBackupFilesDir();
            if (directory == null || (!directory.exists() && !directory.mkdirs())) {
                Slog.w(TAG, "Could not open private Android ID storage");
                return null;
            }

            File lockPath = new File(directory, ANDROID_ID_LOCK_FILE_NAME);
            File androidIdPath = androidIdFile(directory, packageName, userId);
            try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                FileChannel channel = lockFile.getChannel();
                try (FileLock ignored = channel.lock()) {
                    AtomicFile atomicFile = new AtomicFile(androidIdPath);
                    if (!forceRandom) {
                        String existing = readAndroidId(atomicFile);
                        if (isAndroidId(existing)) return existing;
                    }

                    String generated = null;
                    if (preserveLegacyValue) {
                        byte[] legacyDigest = derive(context, packageName, userId, "android_id");
                        if (legacyDigest != null) {
                            generated = toHex(legacyDigest, ANDROID_ID_LENGTH / 2);
                        }
                    }
                    if (generated == null) {
                        byte[] randomBytes = new byte[ANDROID_ID_LENGTH / 2];
                        new SecureRandom().nextBytes(randomBytes);
                        generated = toHex(randomBytes, randomBytes.length);
                    }
                    FileOutputStream output = atomicFile.startWrite();
                    try {
                        output.write(generated.getBytes(StandardCharsets.US_ASCII));
                        atomicFile.finishWrite(output);
                    } catch (IOException error) {
                        atomicFile.failWrite(output);
                        throw error;
                    }
                    return generated;
                }
            }
        } catch (IOException | SecurityException | GeneralSecurityException
                | java.nio.channels.OverlappingFileLockException error) {
            Slog.w(TAG, "Could not create a clone Android ID: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    private static String readAndroidId(AtomicFile atomicFile) throws IOException {
        try (FileInputStream input = atomicFile.openRead()) {
            byte[] value = new byte[ANDROID_ID_LENGTH];
            int offset = 0;
            while (offset < value.length) {
                int count = input.read(value, offset, value.length - offset);
                if (count < 0) return null;
                offset += count;
            }
            if (input.read() != -1) return null;
            String androidId = new String(value, StandardCharsets.US_ASCII);
            return isAndroidId(androidId) ? androidId : null;
        } catch (FileNotFoundException missing) {
            return null;
        }
    }

    private static boolean isAndroidId(String value) {
        if (value == null || value.length() != ANDROID_ID_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
    }

    private static File androidIdFile(File directory, String packageName, int userId)
            throws GeneralSecurityException {
        byte[] key = (packageName + "\u0000" + userId).getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
        return new File(directory, ANDROID_ID_FILE_PREFIX + toHex(digest, digest.length));
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
        if ("imei".equals(identifierKind) || "device_id".equals(identifierKind)
                || "imsi".equals(identifierKind)) {
            TelephonyIds ids = readTelephonyIds(context, packageName, userId);
            if (ids != null) return "imsi".equals(identifierKind) ? ids.imsi : ids.imei;
        }

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

    /**
     * Generates fresh telephony IDs for a new clone. Older clones without a v2 record
     * continue using their original seed-derived IDs, so an app update does not rotate them.
     */
    public static boolean initializeTelephonyIds(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        synchronized (TELEPHONY_ID_LOCK) {
            try {
                File directory = context.getNoBackupFilesDir();
                if (directory == null || (!directory.exists() && !directory.mkdirs())) {
                    Slog.w(TAG, "Could not open private telephony ID storage");
                    return false;
                }
                File lockPath = new File(directory, TELEPHONY_ID_LOCK_FILE_NAME);
                File identityPath = telephonyIdFile(directory, packageName, userId,
                        TELEPHONY_ID_FILE_PREFIX);
                try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                    FileChannel channel = lockFile.getChannel();
                    try (FileLock ignored = channel.lock()) {
                        TelephonyIds generated = generateTelephonyIds();
                        AtomicFile atomicFile = new AtomicFile(identityPath);
                        FileOutputStream output = atomicFile.startWrite();
                        try {
                            String record = generated.imei + "\n" + generated.imsi + "\n";
                            output.write(record.getBytes(StandardCharsets.US_ASCII));
                            atomicFile.finishWrite(output);
                        } catch (IOException error) {
                            atomicFile.failWrite(output);
                            throw error;
                        }
                        return true;
                    }
                }
            } catch (IOException | SecurityException | GeneralSecurityException
                    | java.nio.channels.OverlappingFileLockException error) {
                Slog.w(TAG, "Could not initialize clone telephony IDs: "
                        + error.getClass().getSimpleName());
                return false;
            }
        }
    }

    /** Removes generated telephony IDs after the virtual user is deleted. */
    public static boolean deleteTelephonyIds(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return false;
        }
        synchronized (TELEPHONY_ID_LOCK) {
            try {
                File directory = context.getNoBackupFilesDir();
                if (directory == null || !directory.exists()) return true;
                File lockPath = new File(directory, TELEPHONY_ID_LOCK_FILE_NAME);
                File currentPath = telephonyIdFile(directory, packageName, userId,
                        TELEPHONY_ID_FILE_PREFIX);
                File previousPath = telephonyIdFile(directory, packageName, userId,
                        PREVIOUS_TELEPHONY_ID_FILE_PREFIX);
                try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                    FileChannel channel = lockFile.getChannel();
                    try (FileLock ignored = channel.lock()) {
                        new AtomicFile(currentPath).delete();
                        new AtomicFile(previousPath).delete();
                    }
                }
                return true;
            } catch (IOException | SecurityException | GeneralSecurityException
                    | java.nio.channels.OverlappingFileLockException error) {
                Slog.w(TAG, "Could not remove clone telephony IDs: "
                        + error.getClass().getSimpleName());
                return false;
            }
        }
    }

    private static TelephonyIds readTelephonyIds(Context context, String packageName, int userId) {
        if (context == null || packageName == null || packageName.isEmpty() || userId < 0) {
            return null;
        }
        synchronized (TELEPHONY_ID_LOCK) {
            try {
                File directory = context.getNoBackupFilesDir();
                if (directory == null || !directory.exists()) return null;
                File lockPath = new File(directory, TELEPHONY_ID_LOCK_FILE_NAME);
                File identityPath = telephonyIdFile(directory, packageName, userId,
                        TELEPHONY_ID_FILE_PREFIX);
                try (RandomAccessFile lockFile = new RandomAccessFile(lockPath, "rw")) {
                    FileChannel channel = lockFile.getChannel();
                    try (FileLock ignored = channel.lock()) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                new AtomicFile(identityPath).openRead(), StandardCharsets.US_ASCII))) {
                            String imei = reader.readLine();
                            String imsi = reader.readLine();
                            if (reader.readLine() != null || !isValidImei(imei)
                                    || !isDigits(imsi, 15)) return null;
                            return new TelephonyIds(imei, imsi);
                        } catch (FileNotFoundException missing) {
                            return null;
                        }
                    }
                }
            } catch (IOException | SecurityException | GeneralSecurityException
                    | java.nio.channels.OverlappingFileLockException error) {
                Slog.w(TAG, "Could not read clone telephony IDs: "
                        + error.getClass().getSimpleName());
                return null;
            }
        }
    }

    private static TelephonyIds generateTelephonyIds() {
        SecureRandom random = new SecureRandom();
        StringBuilder imeiBody = new StringBuilder(14);
        imeiBody.append(3 + random.nextInt(7));
        for (int i = 1; i < 14; i++) imeiBody.append(random.nextInt(10));
        String imei = withLuhnCheckDigit(imeiBody.toString());

        StringBuilder imsi = new StringBuilder(15);
        imsi.append(1 + random.nextInt(8));
        for (int i = 1; i < 15; i++) imsi.append(random.nextInt(10));
        return new TelephonyIds(imei, imsi.toString());
    }

    private static boolean isValidImei(String imei) {
        if (!isDigits(imei, 15)) return false;
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int digit = imei.charAt(i) - '0';
            if ((i & 1) == 1) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10 == imei.charAt(14) - '0';
    }

    private static boolean isDigits(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }

    private static File telephonyIdFile(
            File directory, String packageName, int userId, String prefix)
            throws GeneralSecurityException {
        byte[] key = (packageName + "\u0000" + userId).getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
        return new File(directory, prefix + toHex(digest, digest.length));
    }

    private static final class TelephonyIds {
        final String imei;
        final String imsi;

        TelephonyIds(String imei, String imsi) {
            this.imei = imei;
            this.imsi = imsi;
        }
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
        synchronized (ANDROID_ID_LOCK) {
            sAndroidIds.clear();
        }
    }
}
