package com.clony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimProfileDirectoryTest {
    @Test
    fun everyBundledProfileHasValidAndReviewableMetadata() {
        val profiles = SimProfileDirectory.allProfiles()

        assertTrue(profiles.isNotEmpty())
        assertEquals(profiles.size, profiles.map(SimCarrierProfile::countryIso).distinct().size)
        profiles.forEach { profile ->
            assertTrue("Invalid catalog entry for ${profile.countryIso}", SimProfileDirectory.isComplete(profile))
        }
    }

    @Test
    fun countryLookupIsCaseInsensitiveAndDoesNotInventUnsupportedEntries() {
        val unitedArabEmirates = SimProfileDirectory.findByIso("AE")

        assertNotNull(unitedArabEmirates)
        assertEquals("Etisalat", unitedArabEmirates?.carrierName)
        assertEquals("42402", unitedArabEmirates?.operatorCode)
        assertNull(SimProfileDirectory.findByIso("ZZ"))
    }
}
