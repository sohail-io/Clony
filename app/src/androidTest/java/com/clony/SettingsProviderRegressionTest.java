package com.clony;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

import top.niunaijun.blackbox.fake.service.context.providers.SystemProviderStub;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsProviderRegressionTest {
    public interface Provider extends IInterface {
        Bundle call(String authority, String command, String arg, Bundle extras);
    }

    public interface MediaProvider extends IInterface {
        Bundle query(Object attribution, android.net.Uri uri, Bundle queryArgs);
    }

    public static class FakeMediaProvider implements MediaProvider {
        Object attribution;
        android.net.Uri uri;
        Bundle queryArgs;
        final Bundle response = new Bundle();
        @Override public IBinder asBinder() { return null; }
        @Override public Bundle query(Object source, android.net.Uri target, Bundle args) {
            attribution = source;
            uri = target;
            queryArgs = args;
            return response;
        }
    }

    @Test public void mediaQueriesRebuildHostAttributionAndPreserveQuery() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31);
        Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
        Object builder = builderClass.getConstructor(int.class).newInstance(210001);
        builderClass.getMethod("setPackageName", String.class).invoke(builder, "com.example.clone");
        Object virtualSource = builderClass.getMethod("build").invoke(builder);
        FakeMediaProvider base = new FakeMediaProvider();
        MediaProvider provider = (MediaProvider) new SystemProviderStub().wrapper(base, "com.clony");
        android.net.Uri uri = android.net.Uri.parse("content://media/external/images/media");
        Bundle args = new Bundle();
        args.putString("android:query-arg-sql-selection", "_size > 0");
        assertSame(base.response, provider.query(virtualSource, uri, args));
        assertNotSame(virtualSource, base.attribution);
        assertEquals(top.niunaijun.blackbox.BlackBoxCore.getHostUid(),
                base.attribution.getClass().getMethod("getUid").invoke(base.attribution));
        assertEquals("com.clony", base.attribution.getClass().getMethod("getPackageName").invoke(base.attribution));
        assertSame(uri, base.uri);
        assertSame(args, base.queryArgs);
    }

    public static class FakeProvider implements Provider {
        boolean reject = true;
        final Bundle response = new Bundle();

        @Override public IBinder asBinder() { return null; }

        @Override public Bundle call(String authority, String command, String arg, Bundle extras) {
            if (reject) {
                throw new SecurityException("Calling uid 123 doesn't match source uid 456");
            }
            return response;
        }
    }

    @Test public void rejectedConfigListContainsIterableMap() {
        Provider provider = (Provider) new SystemProviderStub().wrapper(new FakeProvider(), "com.clony");
        Bundle result = provider.call("settings", "LIST_config", null, new Bundle());
        HashMap<?, ?> values = (HashMap<?, ?>) result.getSerializable("value");
        assertNotNull(values);
        assertTrue(values.entrySet().isEmpty());
    }

    @Test public void rejectedSingleSettingRemainsAbsent() {
        Provider provider = (Provider) new SystemProviderStub().wrapper(new FakeProvider(), "com.clony");
        assertNull(provider.call("settings", "GET_secure", "example", null).get("value"));
    }

    @Test public void successfulConfigListIsPreserved() {
        FakeProvider base = new FakeProvider();
        base.reject = false;
        HashMap<String, String> flags = new HashMap<>();
        flags.put("autofill/example", "true");
        base.response.putSerializable("value", flags);
        Provider provider = (Provider) new SystemProviderStub().wrapper(base, "com.clony");
        assertSame(base.response, provider.call("settings", "LIST_config", null, null));
    }
}
