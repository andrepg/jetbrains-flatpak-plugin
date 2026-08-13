package io.github.andrepg.gtk.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class AdwShimManagerTest {
    @Test
    fun `ensureShim compiles and caches the shim`() {
        withTempDir { dir ->
            val manager = AdwShimManager(dir, "/usr/bin/flatpak")
            val shim = manager.ensureShim("org.gnome.Sdk", "50")
            assertNotNull(shim)
            assertTrue(shim!!.isFile)
            assertEquals(File(dir, "adw-shim-50.so"), shim)

            // Second call returns cached file
            assertEquals(shim, manager.ensureShim("org.gnome.Sdk", "50"))
        }
    }

    @Test
    fun `ensureShim returns null when compilation fails`() {
        withTempDir { dir ->
            val manager = AdwShimManager(dir, "/nonexistent/flatpak")
            val shim = manager.ensureShim("org.gnome.Sdk", "50")
            assertNull(shim)
        }
    }

    @Test
    fun `shimFile returns the expected path`() {
        withTempDir { dir ->
            val manager = AdwShimManager(dir, "/usr/bin/flatpak")
            assertEquals(File(dir, "adw-shim-50.so"), manager.shimFile("50"))
        }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDir()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
