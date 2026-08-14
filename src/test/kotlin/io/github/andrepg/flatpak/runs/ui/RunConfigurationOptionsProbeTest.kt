package io.github.andrepg.flatpak.runs.ui

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunConfigurationOptionsProbeTest {
    @Test
    fun probe() {
        val o = FlatpakRunSettingsAttributes()
        println("PROBE initial flatpakManifest=${o.flatpakManifest}")
        o.flatpakManifest = ""
        println("PROBE after set empty=${o.flatpakManifest}")
        assertEquals("", o.flatpakManifest)
        o.flatpakManifest = null
        println("PROBE after set null=${o.flatpakManifest}")
        assertNull(o.flatpakManifest)
    }
}
