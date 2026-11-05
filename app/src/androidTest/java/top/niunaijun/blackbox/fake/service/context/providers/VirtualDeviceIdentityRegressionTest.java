package top.niunaijun.blackbox.fake.service.context.providers;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

import top.niunaijun.blackbox.fake.service.ITelephonyManagerProxy;
import top.niunaijun.blackbox.fake.service.VirtualDeviceIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class VirtualDeviceIdentityRegressionTest {
    public interface SettingsProvider extends IInterface {
        Bundle call(String authority, String command, String arg, Bundle extras);
        Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder);
    }

    private static final class FakeSettingsProvider implements SettingsProvider {
        private final Bundle callResult = new Bundle();
        boolean rejectAttribution;

        FakeSettingsProvider() {
            callResult.putString("value", "physical-device-id");
        }

        @Override
        public IBinder asBinder() {
            return null;
        }

        @Override
        public Bundle call(String authority, String command, String arg, Bundle extras) {
            if (rejectAttribution) {
                throw new SecurityException("Calling uid 123 doesn't match source uid 456");
            }
            return callResult;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"name", "value"});
            cursor.addRow(new Object[]{"android_id", "physical-device-id"});
            cursor.addRow(new Object[]{"device_name", "Pixel"});
            return cursor;
        }
    }

    private static final class CloneSettingsProviderStub extends SystemProviderStub {
        private final Context context;
        private final String packageName;
        private final int userId;

        CloneSettingsProviderStub(Context context, String packageName, int userId) {
            this.context = context;
            this.packageName = packageName;
            this.userId = userId;
        }

        @Override
        protected String getVirtualPackageName() {
            return packageName;
        }

        @Override
        protected int getVirtualUserId() {
            return userId;
        }

        @Override
        protected Context getIdentityContext() {
            return context;
        }
    }

    public static final class RestrictedTelephonyService {
        public String getDeviceId() {
            throw new SecurityException("READ_PHONE_STATE required");
        }

        public String getSubscriberId() {
            return null;
        }
    }

    @Test
    public void androidIdIsStableAcrossProcessCacheResetAndIsolatedPerClone() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String first = VirtualDeviceIdentity.getAndroidId(context, "com.example.alpha", 0);
        assertNotNull(first);
        assertEquals(16, first.length());
        assertEquals(first, first.toLowerCase(Locale.ROOT));
        assertEquals(first, VirtualDeviceIdentity.getAndroidId(context, "com.example.alpha", 0));

        clearProcessSeedCache();
        assertEquals(first, VirtualDeviceIdentity.getAndroidId(context, "com.example.alpha", 0));
        assertNotEquals(first, VirtualDeviceIdentity.getAndroidId(context, "com.example.beta", 0));
        assertNotEquals(first, VirtualDeviceIdentity.getAndroidId(context, "com.example.alpha", 1));
    }

    @Test
    public void telephonyPseudonymsAreStableAndUseExpectedFormats() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String imei = VirtualDeviceIdentity.getTelephonyId(
                context, "com.example.alpha", 0, "imei");
        assertNotNull(imei);
        assertEquals(15, imei.length());
        assertTrue(imei.matches("[0-9]{15}"));
        assertTrue(hasValidLuhnCheckDigit(imei));

        clearProcessSeedCache();
        assertEquals(imei, VirtualDeviceIdentity.getTelephonyId(
                context, "com.example.alpha", 0, "imei"));
        assertNotEquals(imei, VirtualDeviceIdentity.getTelephonyId(
                context, "com.example.beta", 0, "imei"));

        String meid = VirtualDeviceIdentity.getTelephonyId(
                context, "com.example.alpha", 0, "meid");
        assertEquals(14, meid.length());
        assertTrue(meid.matches("[0-9A-F]{14}"));
    }

    @Test
    public void telephonyPermissionDenialsAndUnavailableValuesArePreserved() throws Throwable {
        RestrictedTelephonyService service = new RestrictedTelephonyService();
        Method getDeviceId = RestrictedTelephonyService.class.getMethod("getDeviceId");
        try {
            invokeTelephonyIdentityHook(service, getDeviceId, "imei");
            fail("Expected the platform permission denial to propagate");
        } catch (SecurityException expected) {
            assertEquals("READ_PHONE_STATE required", expected.getMessage());
        }

        Method getSubscriberId = RestrictedTelephonyService.class.getMethod("getSubscriberId");
        assertNull(invokeTelephonyIdentityHook(service, getSubscriberId, "imsi"));
    }

    @Test
    public void settingsCallAndQueryReturnTheSameCloneScopedAndroidId() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String expected = VirtualDeviceIdentity.getAndroidId(context, "com.example.tiktok", 3);
        FakeSettingsProvider base = new FakeSettingsProvider();
        SettingsProvider provider = (SettingsProvider) new CloneSettingsProviderStub(
                context, "com.example.tiktok", 3).wrapper(base, "com.clony");

        Bundle result = provider.call("settings", "GET_secure", "android_id", null);
        assertEquals(expected, result.getString("value"));
        assertEquals("physical-device-id", base.call(
                "settings", "GET_secure", "android_id", null).getString("value"));

        Cursor cursor = provider.query(Uri.parse("content://settings/secure"),
                new String[]{"name", "value"}, "name=?", new String[]{"android_id"}, null);
        try {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("android_id", cursor.getString(cursor.getColumnIndexOrThrow("name")));
            assertEquals(expected, cursor.getString(cursor.getColumnIndexOrThrow("value")));
            cursor.moveToNext();
            assertEquals("Pixel", cursor.getString(cursor.getColumnIndexOrThrow("value")));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void otherSecureSettingsAreNotRewritten() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SettingsProvider provider = (SettingsProvider) new CloneSettingsProviderStub(
                context, "com.example.tiktok", 3).wrapper(new FakeSettingsProvider(), "com.clony");

        assertEquals("physical-device-id",
                provider.call("settings", "GET_secure", "device_name", null).getString("value"));
    }

    @Test
    public void androidIdStillUsesCloneValueWhenSettingsAttributionIsRejected() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String expected = VirtualDeviceIdentity.getAndroidId(context, "com.example.tiktok", 3);
        FakeSettingsProvider base = new FakeSettingsProvider();
        base.rejectAttribution = true;
        SettingsProvider provider = (SettingsProvider) new CloneSettingsProviderStub(
                context, "com.example.tiktok", 3).wrapper(base, "com.clony");

        assertEquals(expected, provider.call("settings", "GET_secure", "android_id", null)
                .getString("value"));
    }

    private static void clearProcessSeedCache() throws Exception {
        Method clear = VirtualDeviceIdentity.class.getDeclaredMethod("clearProcessCacheForTesting");
        clear.setAccessible(true);
        clear.invoke(null);
    }

    private static Object invokeTelephonyIdentityHook(
            Object service, Method method, String identifierKind) throws Throwable {
        Method hook = ITelephonyManagerProxy.class.getDeclaredMethod(
                "virtualizedIdentifier", Object.class, Method.class, Object[].class, String.class);
        hook.setAccessible(true);
        try {
            return hook.invoke(null, service, method, new Object[0], identifierKind);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            throw cause;
        }
    }

    private static boolean hasValidLuhnCheckDigit(String value) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            if ((i & 1) == 1) {
                digit *= 2;
                digit = digit / 10 + digit % 10;
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
