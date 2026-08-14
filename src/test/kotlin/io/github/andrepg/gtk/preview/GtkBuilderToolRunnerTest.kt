package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.schema.SdkHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GtkBuilderToolRunnerTest {
    private val sampleOutput = """
        org.gnome.Sdk	50	user
        org.gnome.Platform	50	system
        org.gnome.Sdk	49	system
    """

    @Test
    fun `resolveBranch returns pinned branch when installed`() {
        val hint = SdkHint("org.gnome.Sdk", "50")
        val resolution = GtkBuilderToolRunner.resolveBranch(hint, "/usr/bin/flatpak")
        assertEquals(GtkBuilderToolRunner.BranchResolution.Installed("50"), resolution)
    }

    @Test
    fun `resolveBranch falls back to best installed branch when pinned not installed`() {
        val hint = SdkHint("org.gnome.Sdk", "99")
        val resolution = GtkBuilderToolRunner.resolveBranch(hint, "/usr/bin/flatpak")
        assertEquals(GtkBuilderToolRunner.BranchResolution.BranchNotInstalled("99"), resolution)
    }

    @Test
    fun `resolveBranch returns NotFound when SDK not installed`() {
        val hint = SdkHint("org.example.Sdk", null)
        val resolution = GtkBuilderToolRunner.resolveBranch(hint, "/usr/bin/flatpak")
        assertEquals(GtkBuilderToolRunner.BranchResolution.NotFound, resolution)
    }

    @Test
    fun `resolveBranch returns NotFound for null hint`() {
        val resolution = GtkBuilderToolRunner.resolveBranch(null, "/usr/bin/flatpak")
        assertEquals(GtkBuilderToolRunner.BranchResolution.NotFound, resolution)
    }

    @Test
    fun `validate passes gate for pure Gtk file`() {
        withTempDir { dir ->
            val uiFile = File(dir, "window.ui").apply { writeText("<interface/>") }
            val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak")
            assertTrue(result.passesGate)
            assertTrue(result.stderr.isBlank())
        }
    }

    @Test
    fun `validate fails gate for Adw file without shim`() {
        withTempDir { dir ->
            val uiFile = copyTestAsset(dir, "adw.ui")
            val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak")
            assertFalse(result.passesGate)
            assertTrue(result.stderr.contains("Invalid object type"))
        }
    }

    @Test
    fun `validate passes gate for Adw file with shim`() {
        withTempDir { dir ->
            val uiFile = copyTestAsset(dir, "adw.ui")
            val shim = AdwShimManager(dir, "/usr/bin/flatpak").ensureShim("org.gnome.Sdk", "50")!!
            val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak", shim.absolutePath)
            assertTrue("Validation must pass once libadwaita is registered", result.passesGate)
            assertTrue(result.stderr.isBlank())
        }
    }

    @Test
    fun `render produces PNG file`() {
        withTempDir { dir ->
            val uiFile = File(dir, "window.ui").apply {
                writeText(
                    """
                    <interface>
                        <object class="GtkWindow" id="win">
                            <child>
                                <object class="GtkLabel"><property name="label">hello</property></object>
                            </child>
                        </object>
                    </interface>
                    """.trimIndent()
                )
            }
            val outPng = File(dir, "out.png")
            val result = GtkBuilderToolRunner.render(uiFile, outPng, "org.gnome.Sdk", "50", "/usr/bin/flatpak")
            assertTrue(result.ok)
            assertTrue(outPng.isFile)
        }
    }

    /** Copies the shared default Adw/Gtk UI test asset into [dir]. */
    private fun copyTestAsset(dir: File, name: String): File {
        val source = File("src/test/testData/ui/default-adw-application.ui")
        return File(dir, name).apply { writeText(source.readText()) }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        // The flatpak sandbox masks host /tmp, so use a path under $HOME
        // (exposed through --filesystem=host) for the test files.
        val dir = File(System.getProperty("user.home"), "flatpak-preview-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
