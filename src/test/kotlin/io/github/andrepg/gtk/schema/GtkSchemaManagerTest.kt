package io.github.andrepg.gtk.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GtkSchemaManagerTest {

    private val hint = SdkHint(sdkAppId = "org.gnome.Sdk", branch = "50")

    @Test
    fun `cachedSchema returns null before any generation`() {
        withTempDirs { configDir, _ ->
            val manager = GtkSchemaManager(configDir, emptyList())
            assertNull(manager.cachedSchema(hint))
            assertNull(manager.cachedSchema(null))
        }
    }

    @Test
    fun `generateSchema generates and caches the XSD for an installed SDK`() {
        withTempDirs { configDir, baseDir ->
            val girDir = baseDir.resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0").apply { mkdirs() }
            copyFixture(girDir)

            val manager = GtkSchemaManager(configDir, listOf(baseDir))
            val generated = manager.generateSchema(hint, "/nonexistent/flatpak")

            val expected = configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd")
            assertNotNull(generated)
            assertEquals(expected, generated)
            assertTrue(expected.isFile)
            assertTrue(expected.readText().contains("GtkButton"))
            assertEquals(expected, manager.cachedSchema(hint))
        }
    }

    @Test
    fun `generateSchema serves a pre-existing cache file without regeneration`() {
        withTempDirs { configDir, baseDir ->
            val cached = configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd").apply { writeText("<cached/>") }

            val manager = GtkSchemaManager(configDir, listOf(baseDir))
            assertEquals(cached, manager.generateSchema(hint, "/nonexistent/flatpak"))
            assertEquals(cached, manager.cachedSchema(hint))
        }
    }

    @Test
    fun `generateSchema returns null when no SDK is installed`() {
        withTempDirs { configDir, baseDir ->
            val manager = GtkSchemaManager(configDir, listOf(baseDir))
            assertNull(manager.generateSchema(hint, "/nonexistent/flatpak"))
            assertFalse(configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd").exists())
        }
    }

    @Test
    fun `generateSchema returns null for a null hint`() {
        withTempDirs { configDir, baseDir ->
            val manager = GtkSchemaManager(configDir, listOf(baseDir))
            assertNull(manager.generateSchema(null, "/nonexistent/flatpak"))
        }
    }

    @Test
    fun `markRequested returns true once per key`() {
        withTempDirs { configDir, _ ->
            val manager = GtkSchemaManager(configDir, emptyList())
            assertTrue(manager.markRequested(hint))
            assertFalse(manager.markRequested(hint))
            assertTrue(manager.markRequested(SdkHint("org.gnome.Sdk", null)))
            assertFalse(manager.markRequested(SdkHint("org.gnome.Sdk", null)))
            assertFalse(manager.markRequested(null))
        }
    }

    private fun copyFixture(girDir: File) {
        File("test-data/gir").listFiles()?.forEach { girDir.resolve(it.name).writeText(it.readText()) }
    }

    private fun withTempDirs(block: (File, File) -> Unit) {
        val configDir = createTempDir()
        val baseDir = createTempDir()
        try {
            block(configDir, baseDir)
        } finally {
            configDir.deleteRecursively()
            baseDir.deleteRecursively()
        }
    }
}
