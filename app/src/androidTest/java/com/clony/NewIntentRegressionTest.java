package com.clony;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.FragmentController;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import top.niunaijun.blackbox.app.VirtualNewIntentDispatcher;
import top.niunaijun.blackbox.utils.FrameworkServiceCache;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewIntentRegressionTest {
    @Before public void requireModernDeliveryPath() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31);
    }

    public static class ReceivingActivity extends Activity {
        public ReceivingActivity() {
            attachBaseContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        }
        Intent received;
        int calls;
        boolean stateSavedDuringDelivery;
        @Override protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            received = intent;
            calls++;
            stateSavedDuringDelivery = getFragmentManager().isStateSaved();
        }
    }

    @Test public void reusedActivityReceivesCompletionIntentExactlyOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ReceivingActivity activity = new ReceivingActivity();
            Intent original = new Intent("test.publish.COMPLETE", Uri.parse("test://completed"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("publish_id", "completed-post")
                    .putExtra("published", true);
            VirtualNewIntentDispatcher.deliver(activity, original, new Instrumentation());
            assertEquals(1, activity.calls);
            assertNotSame(original, activity.received);
            assertEquals(original.getAction(), activity.received.getAction());
            assertEquals(original.getData(), activity.received.getData());
            assertEquals(original.getFlags(), activity.received.getFlags());
            assertEquals("completed-post", activity.received.getStringExtra("publish_id"));
            assertTrue(activity.received.getBooleanExtra("published", false));
        });
    }

    @Test public void platformFragmentStateIsUnsavedBeforeNewIntent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ReceivingActivity activity = new ReceivingActivity();
            try {
                FragmentController fragments = (FragmentController) FrameworkServiceCache.get(
                        activity, "android.app.FragmentController");
                assertFalse(top.niunaijun.blackbox.core.NativeCore.setFrameworkService(
                        activity, "android.app.FragmentController", fragments));
                fragments.attachHost(null);
                fragments.dispatchCreate();
                fragments.saveAllState();
                assertTrue("Test must begin after fragment state was saved",
                        activity.getFragmentManager().isStateSaved());
                VirtualNewIntentDispatcher.deliver(activity, new Intent("test.RESUME"), new Instrumentation());
                assertEquals(1, activity.calls);
                assertFalse(activity.stateSavedDuringDelivery);
                assertFalse(activity.getFragmentManager().isStateSaved());
                fragments.dispatchDestroy();
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
    }
}
