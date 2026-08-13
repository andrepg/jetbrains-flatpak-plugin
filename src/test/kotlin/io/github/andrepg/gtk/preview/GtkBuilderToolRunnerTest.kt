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
            val uiFile = File(dir, "adw.ui").apply { writeText("<interface><object class='AdwApplicationWindow'/></interface>") }
            val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak")
            assertFalse(result.passesGate)
            assertTrue(result.stderr.contains("Invalid object type 'AdwApplicationWindow'"))
        }
    }

    @Test
    fun `render produces PNG file`() {
        withTempDir { dir ->
            val uiFile = File(dir, "window.ui").apply { writeText("<interface/>") }
            val outPng = File(dir, "out.png")
            val result = GtkBuilderToolRunner.render(uiFile, outPng, "org.gnome.Sdk", "50", "/usr/bin/flatpak")
            assertTrue(result.ok)
            assertTrue(outPng.isFile)
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
