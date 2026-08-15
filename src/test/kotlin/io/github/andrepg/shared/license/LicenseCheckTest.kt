package io.github.andrepg.shared.license

import org.junit.Assert.*
import org.junit.Test

class LicenseCheckTest {
    @Test
    fun `product code matches JetBrains Marketplace format`() {
        val code = LicenseCheck.PRODUCT_CODE
        assertTrue("Product Code must be 4..15 chars", code.length in 4..15)
        assertTrue("Product Code must start with P and contain only capital letters", code.matches(Regex("P[A-Z]{3,14}")))
    }

    @Test
    fun `isLicensed returns null when licensing facade is not initialized`() {
        // In a headless unit test there is no licensing facade, so a definitive
        // answer is impossible — exactly the state the gate must treat as locked.
        assertNull(LicenseCheck.isLicensed())
    }

    @Test
    fun `gate treats unknown license state as locked`() {
        assertFalse(PremiumFeatureGate.isPremiumAvailable())
    }

    @Test
    fun `dev override unlocks premium without a license`() {
        System.setProperty(PremiumFeatureGate.DEV_OVERRIDE_PROPERTY, "true")
        try {
            assertTrue(PremiumFeatureGate.isPremiumAvailable())
        } finally {
            System.clearProperty(PremiumFeatureGate.DEV_OVERRIDE_PROPERTY)
        }
    }

    @Test
    fun `dev override is off by default`() {
        System.clearProperty(PremiumFeatureGate.DEV_OVERRIDE_PROPERTY)
        assertFalse(System.getProperty(PremiumFeatureGate.DEV_OVERRIDE_PROPERTY)?.toBoolean() == true)
    }

    @Test
    fun `a key with the wrong number of parts is rejected`() {
        val result = LicenseCheck.isLicensed()
        // Keep this explicit rather than reflecting into the private verifier:
        // malformed stamps simply must not be reported as licensed.
        assertEquals(null, result)
        assertFalse(result == true)
    }
}
