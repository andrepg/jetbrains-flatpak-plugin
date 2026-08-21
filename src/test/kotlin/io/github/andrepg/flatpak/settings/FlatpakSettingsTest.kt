package io.github.andrepg.flatpak.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Hermetic contract test: with no IDE application on the thread, every
 * [FlatpakSettings] getter must fall back to the documented defaults instead
 * of throwing (regression guard for the `service<>()` NPE).
 */
class FlatpakSettingsTest {
    @Test
    fun `falls back to default binaries without an IDE application`() {
        assertEquals(DefaultFlatpakPaths.MAIN_BINARY, FlatpakSettings.flatpakBinary)
        assertEquals(DefaultFlatpakPaths.BUILDER_BINARY, FlatpakSettings.builderBinary)
    }

    @Test
    fun `falls back to disabled flags without an IDE application`() {
        assertFalse(FlatpakSettings.sentryEnabled)
        assertFalse(FlatpakSettings.debugLoggingEnabled)
    }
}
