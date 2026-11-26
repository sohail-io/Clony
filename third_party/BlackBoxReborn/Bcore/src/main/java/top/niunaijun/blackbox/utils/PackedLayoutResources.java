package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

import org.tukaani.xz.XZInputStream;

/** Resolves Meta APK layout resources stored in assets/layouts.bin.spo. */
public final class PackedLayoutResources extends Resources {
    private static final String TAG = "ClonyPackedLayouts";
    private static final byte[] XZ_MAGIC = new byte[]{(byte) 0xfd, 0x37, 0x7a, 0x58, 0x5a};
    private static final Map<String, byte[]> sLayoutBundles = new ConcurrentHashMap<>();
    private static final Map<XmlResourceParser, Object> sOpenXmlBlocks =
            new WeakHashMap<>();

    private final String mSourceDir;
    private final String mPackageName;

    private PackedLayoutResources(Resources base, String packageName, String sourceDir) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        mPackageName = packageName == null ? "unknown" : packageName;
        mSourceDir = sourceDir == null ? "" : sourceDir;
    }

    /** Installs a Resources subclass on the package context and its LoadedApk. */
    public static boolean install(Context context, Object loadedApk, String sourceDir) {
        if (context == null || loadedApk == null) return false;
        Resources base = context.getResources();
        try (InputStream ignored = base.getAssets().open("layouts.bin.spo")) {
            PackedLayoutResources packed = new PackedLayoutResources(base,
                    context.getPackageName(), sourceDir);
            black.android.app.BRContextImpl.get(context)._set_mResources(packed);
            black.android.app.BRLoadedApk.get(loadedApk)._set_mResources(packed);
            Slog.i(TAG, "packed layout support installed package=" + context.getPackageName()
                    + " source=" + sourceDir + " assets=" + base.getAssets());
            return true;
        } catch (Throwable error) {
            Slog.d(TAG, "packed layout bundle not present package=" + context.getPackageName()
                    + " reason=" + error.getClass().getSimpleName());
            return false;
        }
    }

    /** Replaces the Activity's own ContextImpl resources, which Android may cache separately. */
    public static void installForActivity(Activity activity) {
        if (activity == null) return;
        try {
            Context context = activity.getBaseContext();
            while (context instanceof ContextWrapper) {
                Context next = ((ContextWrapper) context).getBaseContext();
                if (next == null || next == context) break;
                context = next;
            }
            if (context == null) return;
            Resources baseContextResources = context.getResources();
            Resources activityResources = activity.getResources();
            if (activityResources instanceof PackedLayoutResources) return;
            Object loadedApk = black.android.app.BRContextImpl.get(context).mPackageInfo();
            String sourceDir = activity.getApplicationInfo() == null
                    ? null : activity.getApplicationInfo().sourceDir;
            try (InputStream ignored = activityResources.getAssets().open("layouts.bin.spo")) {
                // Activity.getResources() may be an Instagram Resources subclass that
                // exposes resources initialized by the guest. Preserve that exact impl.
                PackedLayoutResources packed = new PackedLayoutResources(activityResources,
                        activity.getPackageName(), sourceDir);
                black.android.app.BRContextImpl.get(context)._set_mResources(packed);
                // ContextThemeWrapper caches its own Resources instance. Updating only its
                // base ContextImpl leaves Activity.getResources() on the stock loader.
                if (activity instanceof ContextThemeWrapper) {
                    black.android.view.BRContextThemeWrapper.get(activity)._set_mResources(packed);
                }
                if (loadedApk != null) {
                    black.android.app.BRLoadedApk.get(loadedApk)._set_mResources(packed);
                }
                Slog.i(TAG, "activity resources replaced package=" + activity.getPackageName()
                        + " activity=" + activity.getClass().getName()
                        + " previous=" + activityResources.getClass().getName()
                        + " baseContextResources=" + baseContextResources.getClass().getName()
                        + " activityResources=" + activity.getResources().getClass().getName()
                        + " assets=" + activityResources.getAssets());
            } catch (Throwable error) {
                Slog.d(TAG, "activity has no packed layout bundle package="
                        + activity.getPackageName() + " activity=" + activity.getClass().getName()
                        + " reason=" + error.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            Slog.w(TAG, "could not install packed layout resources on activity="
                    + activity.getClass().getName(), error);
        }
    }

    @Override
    public XmlResourceParser getLayout(int id) throws NotFoundException {
        TypedValue value = new TypedValue();
        super.getValue(id, value, true);
        String packedPath = value.string == null ? null : value.string.toString();
        LayoutReference reference = parseReference(packedPath);
        if (reference == null) return super.getLayout(id);

        try {
            byte[] bundle = getLayoutBundle();
            long end = reference.offset + reference.length;
            if (reference.offset < 0 || reference.length <= 0 || end > bundle.length) {
                throw new IllegalArgumentException("layout range outside layouts.bin: offset="
                        + reference.offset + " length=" + reference.length
                        + " bundleBytes=" + bundle.length);
            }
            byte[] xml = Arrays.copyOfRange(bundle, (int) reference.offset, (int) end);
            CRC32 crc = new CRC32();
            crc.update(xml);
            int crcTail = (int) (crc.getValue() & 0xffffL);
            if (crcTail != reference.crcTail) {
                throw new IllegalArgumentException("layout CRC tail mismatch expected=0x"
                        + Integer.toHexString(reference.crcTail) + " actual=0x"
                        + Integer.toHexString(crcTail));
            }

            XmlResourceParser parser = createParser(xml);
            Slog.i(TAG, "packed layout opened package=" + mPackageName
                    + " resourceId=0x" + Integer.toHexString(id)
                    + " resource=" + safeResourceName(id)
                    + " offset=0x" + Long.toHexString(reference.offset)
                    + " bytes=" + reference.length + " crcTail=0x"
                    + Integer.toHexString(reference.crcTail));
            return parser;
        } catch (Throwable error) {
            Slog.e(TAG, "packed layout load failed package=" + mPackageName
                    + " resourceId=0x" + Integer.toHexString(id)
                    + " resource=" + safeResourceName(id)
                    + " path=" + packedPath, error);
            throw new NotFoundException("Could not load packed layout " + safeResourceName(id)
                    + " from " + packedPath + ": " + error);
        }
    }

    @Override
    public CharSequence getText(int id) throws NotFoundException {
        try {
            return super.getText(id);
        } catch (NotFoundException error) {
            // Instagram 457 has a small block of string IDs in its action-bar views that
            // are referenced by code/XML but absent from the installed resource table.
            // Preserve the back label where known and keep other optional view labels from
            // taking down the whole Activity. Log every fallback with the exact callsite.
            boolean instagramMissingString = "com.instagram.android".equals(mPackageName)
                    && (id & 0xffff0000) == 0x7f130000;
            if (instagramMissingString) {
                StackTraceElement[] frames = Thread.currentThread().getStackTrace();
                String callsite = frames.length > 4 ? frames[4].toString() : "unknown";
                Slog.w(TAG, "missing Instagram string fallback id=0x"
                        + Integer.toHexString(id) + " callsite=" + callsite);
                return id == 0x7f134a54 ? "Back" : "";
            }
            throw error;
        }
    }

    private byte[] getLayoutBundle() throws Exception {
        byte[] cached = sLayoutBundles.get(mSourceDir);
        if (cached != null) return cached;
        byte[] packed;
        try (InputStream input = getAssets().open("layouts.bin.spo");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            packed = output.toByteArray();
        }
        int streamStart = findXzStream(packed);
        if (streamStart < 0) {
            throw new IllegalArgumentException("layouts.bin.spo has no XZ stream; bytes="
                    + packed.length);
        }
        byte[] unpacked;
        try (XZInputStream input = new XZInputStream(
                new ByteArrayInputStream(packed, streamStart, packed.length - streamStart));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            unpacked = output.toByteArray();
        }
        sLayoutBundles.put(mSourceDir, unpacked);
        Slog.i(TAG, "packed layout bundle decoded source=" + mSourceDir
                + " packedBytes=" + packed.length + " xzOffset=" + streamStart
                + " layoutBytes=" + unpacked.length);
        return unpacked;
    }

    private static XmlResourceParser createParser(byte[] xml) throws Exception {
        Class<?> xmlBlockClass = Class.forName("android.content.res.XmlBlock");
        Constructor<?> constructor = xmlBlockClass.getDeclaredConstructor(byte[].class);
        constructor.setAccessible(true);
        Object block = constructor.newInstance((Object) xml);
        Method newParser = xmlBlockClass.getDeclaredMethod("newParser");
        newParser.setAccessible(true);
        XmlResourceParser parser = (XmlResourceParser) newParser.invoke(block);
        synchronized (sOpenXmlBlocks) {
            sOpenXmlBlocks.put(parser, block);
        }
        return parser;
    }

    private static LayoutReference parseReference(String value) {
        if (value == null || !value.startsWith("L|")) return null;
        String[] parts = value.split("\\|");
        if (parts.length != 4) return null;
        try {
            return new LayoutReference(Long.parseLong(parts[1], 16),
                    Integer.parseInt(parts[2], 16), Integer.parseInt(parts[3], 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String safeResourceName(int id) {
        try {
            return getResourceName(id);
        } catch (NotFoundException ignored) {
            return "unknown";
        }
    }

    private static int findXzStream(byte[] bytes) {
        outer:
        for (int i = 0; i <= bytes.length - XZ_MAGIC.length; i++) {
            for (int j = 0; j < XZ_MAGIC.length; j++) {
                if (bytes[i + j] != XZ_MAGIC[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) throws Exception {
        byte[] buffer = new byte[32 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static final class LayoutReference {
        final long offset;
        final int length;
        final int crcTail;

        LayoutReference(long offset, int length, int crcTail) {
            this.offset = offset;
            this.length = length;
            this.crcTail = crcTail;
        }
    }
}
