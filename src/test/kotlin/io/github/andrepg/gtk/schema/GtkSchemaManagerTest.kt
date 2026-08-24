package io.github.andrepg.gtk.schema

import com.intellij.util.io.delete
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GtkSchemaManagerTest {
    private val hint = SdkHint(sdkAppId = "org.gnome.Sdk", branch = "50")

    @Test
    fun `cachedSchema returns null before any generation`() {
        withTempDirs { configDir, _ ->
            val manager = GtkSchemaManager(configDir)
            assertNull(manager.cachedSchema(hint))
            assertNull(manager.cachedSchema(null))
        }
    }

    @Test
    fun `generateSchema generates and caches the XSD for an installed SDK`() {
        withTempDirs { configDir, work ->
            val (manager, flatpak) = managerWithInstalledSdk(configDir, work)

            val generated = manager.generateSchema(hint, flatpak.absolutePath)

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
        withTempDirs { configDir, _ ->
            val cached = configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd").apply { writeText("<cached/>") }

            val manager = GtkSchemaManager(configDir)
            assertEquals(cached, manager.generateSchema(hint, "/nonexistent/flatpak"))
            assertEquals(cached, manager.cachedSchema(hint))
        }
    }

    @Test
    fun `generateSchema returns null when no SDK is installed`() {
        withTempDirs { configDir, work ->
            val flatpak = FakeFlatpakCli.install(work)

            val manager = GtkSchemaManager(configDir)
            assertNull(manager.generateSchema(hint, flatpak.absolutePath))
            assertFalse(configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd").exists())
        }
    }

    @Test
    fun `generateSchema returns null when the SDK is unsupported`() {
        withTempDirs { configDir, work ->
            val girRoot = FakeFlatpakCli.girRoot(work.resolve("sdk"))
            FakeFlatpakCli.copyFixtures(girRoot)
            val flatpak =
                FakeFlatpakCli.install(
                    work,
                    runtimes = "org.example.Sdk\t50\tuser",
                    location = work.resolve("sdk"),
                )

            val manager = GtkSchemaManager(configDir)
            assertNull(manager.generateSchema(SdkHint(sdkAppId = "org.example.Sdk", branch = "50"), flatpak.absolutePath))
            assertFalse(configDir.resolve("gtk-ui-org.example.Sdk-50.xsd").exists())
        }
    }

    @Test
    fun `generateSchema returns null for a null hint`() {
        withTempDirs { configDir, _ ->
            val manager = GtkSchemaManager(configDir)
            assertNull(manager.generateSchema(null, "/nonexistent/flatpak"))
        }
    }

    @Test
    fun `generateSchema reports locating and caching progress steps`() {
        withTempDirs { configDir, work ->
            val (manager, flatpak) = managerWithInstalledSdk(configDir, work)

            val steps = mutableListOf<GtkSchemaStep>()
            assertNotNull(
                manager.generateSchema(hint, flatpak.absolutePath) {
                    steps += it
                    true
                },
            )

            assertEquals(GtkSchemaStep.Locating, steps.first())
            assertTrue(GtkSchemaStep.Caching in steps)
        }
    }

    @Test
    fun `generateSchema aborts on cancellation and caches nothing`() {
        withTempDirs { configDir, work ->
            val (manager, flatpak) = managerWithInstalledSdk(configDir, work)

            assertNull(manager.generateSchema(hint, flatpak.absolutePath) { it is GtkSchemaStep.Parsing })
            assertFalse(configDir.resolve("gtk-ui-org.gnome.Sdk-50.xsd").exists())
        }
    }

    @Test
    fun `markRequested returns true once per key`() {
        withTempDirs { configDir, _ ->
            val manager = GtkSchemaManager(configDir)
            assertTrue(manager.markRequested(hint))
            assertFalse(manager.markRequested(hint))
            assertTrue(manager.markRequested(SdkHint("org.gnome.Sdk", null)))
            assertFalse(manager.markRequested(SdkHint("org.gnome.Sdk", null)))
            assertFalse(manager.markRequested(null))
        }
    }

    /** Installs a fake flatpak CLI whose supported SDK ships the hermetic GIR fixtures. */
    private fun managerWithInstalledSdk(
        configDir: File,
        work: File,
    ): Pair<GtkSchemaManager, File> {
        val girRoot = FakeFlatpakCli.girRoot(work.resolve("sdk"))
        FakeFlatpakCli.copyFixtures(girRoot)
        val flatpak =
            FakeFlatpakCli.install(work, runtimes = "org.gnome.Sdk\t50\tuser", location = work.resolve("sdk"))
        return GtkSchemaManager(configDir) to flatpak
    }

    private fun withTempDirs(block: (File, File) -> Unit) {
        val configDir = createTempDirectory()
        val workDir = createTempDirectory()

        try {
            block(configDir.toFile(), workDir.toFile())
        } finally {
            configDir.delete(true)
            workDir.delete(true)
        }
    }
}
