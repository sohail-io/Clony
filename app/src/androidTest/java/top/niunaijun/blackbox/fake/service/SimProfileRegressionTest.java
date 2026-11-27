package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import top.niunaijun.blackbox.core.CloneNetworkIdentityStore;
import top.niunaijun.blackbox.fake.service.VirtualDeviceIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SimProfileRegressionTest {
    @Test
    public void selectedProfileOverridesProxyCountryAcrossTelephonyStringHooks() {
        CloneNetworkIdentityStore.Options options = options(
                true, false, true, "T-Mobile", "us", "310120", "ca");

        assertEquals("us", ITelephonyManagerProxy.operatorStringOverride(
                options, "getSimCountryIsoForPhone"));
        assertEquals("us", ITelephonyManagerProxy.operatorStringOverride(
                options, "getNetworkCountryIsoForSubscriber"));
        assertEquals("T-Mobile", ITelephonyManagerProxy.operatorStringOverride(
                options, "getSimOperatorNameForPhone"));
        assertEquals("T-Mobile", ITelephonyManagerProxy.operatorStringOverride(
                options, "getNetworkOperatorName"));
        assertEquals("T-Mobile", ITelephonyManagerProxy.operatorStringOverride(
                options, "getServiceProviderNameForPhone"));
        assertEquals("310120", ITelephonyManagerProxy.operatorStringOverride(
                options, "getSimOperatorNumericForPhone"));
        assertEquals("310120", ITelephonyManagerProxy.operatorStringOverride(
                options, "getNetworkOperator"));
        assertTrue(ITelephonyManagerProxy.shouldSuppressCellDetails(options));
        assertFalse(ITelephonyManagerProxy.shouldSuppressPhoneNumber(options));
        assertEquals(android.telephony.TelephonyManager.SIM_STATE_READY,
                ITelephonyManagerProxy.simStateOverride(options, 1));
    }

    @Test
    public void disabledProfileKeepsProxyCountryBehaviorAndNormalDefaults() {
        CloneNetworkIdentityStore.Options proxyMatched = options(
                false, false, true, "T-Mobile", "us", "310120", "ca");
        assertEquals("ca", ITelephonyManagerProxy.operatorStringOverride(
                proxyMatched, "getNetworkCountryIsoForPhone"));
        assertEquals("", ITelephonyManagerProxy.operatorStringOverride(
                proxyMatched, "getNetworkOperatorName"));
        assertTrue(ITelephonyManagerProxy.shouldSuppressCellDetails(proxyMatched));
        assertTrue(ITelephonyManagerProxy.shouldSuppressPhoneNumber(proxyMatched));

        CloneNetworkIdentityStore.Options defaults = CloneNetworkIdentityStore.defaults();
        assertNull(ITelephonyManagerProxy.operatorStringOverride(defaults, "getNetworkOperator"));
        assertFalse(ITelephonyManagerProxy.shouldSuppressCellDetails(defaults));
        assertFalse(ITelephonyManagerProxy.shouldSuppressPhoneNumber(defaults));
        assertNull(ISubProxy.phoneNumberOverride(defaults));
    }

    @Test
    public void selectedProfileSuppliesValuesEvenWhenRedactionIsAlsoEnabled() {
        CloneNetworkIdentityStore.Options options = options(
                true, true, true, "T-Mobile", "us", "310120", "ca");

        assertEquals("310120", ITelephonyManagerProxy.operatorStringOverride(options, "getSimOperator"));
        assertEquals("T-Mobile", ITelephonyManagerProxy.operatorStringOverride(options, "getSimOperatorName"));
        assertTrue(ITelephonyManagerProxy.shouldSuppressCellDetails(options));
        assertFalse(ITelephonyManagerProxy.shouldSuppressPhoneNumber(options));
    }

    @Test
    public void generatedProfileIdentifiersAreStableAndMatchTheSelectedOperator() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = "com.clony.simprofile.ids" + UUID.randomUUID().toString().replace("-", "");
        int userId = 58_942;
        String operatorCode = "310120";

        String imsi = VirtualDeviceIdentity.getProfileImsi(context, packageName, userId, operatorCode);
        String iccid = VirtualDeviceIdentity.getProfileIccid(context, packageName, userId, operatorCode);
        String phoneNumber = VirtualDeviceIdentity.getProfilePhoneNumber(
                context, packageName, userId, "us");
        String imei = VirtualDeviceIdentity.getTelephonyId(context, packageName, userId, "imei");

        assertNotNull(imsi);
        assertNotNull(iccid);
        assertNotNull(phoneNumber);
        assertNotNull(imei);
        assertEquals(15, imsi.length());
        assertTrue(imsi.startsWith(operatorCode));
        assertEquals(imsi, VirtualDeviceIdentity.getProfileImsi(context, packageName, userId, operatorCode));
        assertEquals(20, iccid.length());
        assertTrue(iccid.matches("[0-9]{20}"));
        assertTrue(hasValidLuhnCheckDigit(iccid));
        assertEquals(iccid, VirtualDeviceIdentity.getProfileIccid(context, packageName, userId, operatorCode));
        assertEquals("+1", phoneNumber.substring(0, 2));
        assertEquals(12, phoneNumber.length());
        assertTrue(phoneNumber.matches("\\+1[0-9]{10}"));
        assertNotEquals(phoneNumber, VirtualDeviceIdentity.getProfilePhoneNumber(
                context, packageName, userId + 1, "us"));
        assertEquals(phoneNumber, VirtualDeviceIdentity.getProfilePhoneNumber(
                context, packageName, userId, "US"));
        assertEquals(15, imei.length());
        assertEquals(imei, VirtualDeviceIdentity.getTelephonyId(context, packageName, userId, "imei"));
    }

    @Test
    public void profilePersistsForTemplateDefaultsAndAnIndividualClone() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = "com.clony.simprofile.test" + UUID.randomUUID().toString().replace("-", "");
        int userId = 48_271;
        CloneNetworkIdentityStore.Options profile = options(
                true, false, false, "T-Mobile", "us", "310120", "");

        try {
            assertTrue(CloneNetworkIdentityStore.writeTemplate(context, packageName, profile));
            assertProfile(profile, CloneNetworkIdentityStore.readTemplate(context, packageName));

            assertTrue(CloneNetworkIdentityStore.write(context, packageName, userId, profile));
            assertProfile(profile, CloneNetworkIdentityStore.read(context, packageName, userId));
        } finally {
            CloneNetworkIdentityStore.delete(context, packageName, userId);
            deleteTemplatePreferences(context, packageName);
        }
    }

    private static CloneNetworkIdentityStore.Options options(
            boolean simulateProfile,
            boolean hideOperatorInfo,
            boolean matchProxy,
            String carrier,
            String countryIso,
            String operatorCode,
            String proxyCountryIso
    ) {
        return new CloneNetworkIdentityStore.Options(
                true,
                hideOperatorInfo,
                true,
                false,
                false,
                simulateProfile,
                "",
                carrier,
                countryIso,
                operatorCode,
                matchProxy,
                proxyCountryIso
        );
    }

    private static void assertProfile(
            CloneNetworkIdentityStore.Options expected,
            CloneNetworkIdentityStore.Options actual
    ) {
        assertTrue(actual.simulateSimProfile);
        assertEquals(expected.simCarrierName, actual.simCarrierName);
        assertEquals(expected.simCountryIso, actual.simCountryIso);
        assertEquals(expected.simOperatorCode, actual.simOperatorCode);
    }

    private static void deleteTemplatePreferences(Context context, String packageName) {
        SharedPreferences preferences = context.getSharedPreferences(
                "clone_network_identity_v1", Context.MODE_PRIVATE);
        String prefix = "network_identity:template:" + packageName + ":";
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.commit();
    }

    private static boolean hasValidLuhnCheckDigit(String value) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            if (((value.length() - 1 - i) & 1) == 1) {
                digit *= 2;
                digit = (digit / 10) + (digit % 10);
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
