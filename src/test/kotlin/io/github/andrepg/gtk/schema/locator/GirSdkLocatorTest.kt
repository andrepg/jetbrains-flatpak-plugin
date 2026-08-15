package io.github.andrepg.gtk.schema.locator

import com.intellij.util.io.delete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists

class GirSdkLocatorTest {
    private val sampleOutput =
        """
        org.gnome.Sdk	50	user
        org.gnome.Platform	50	system
        org.freedesktop.Sdk	24.08	system
        org.gnome.Sdk	49	system
        """.trimIndent()

    @Test
    fun `parseRuntimeRows skips blanks and partial lines`() {
        val rows = GirSdkLocator.parseRuntimeRows("\n${sampleOutput}\n\nbogus\t\n")
        assertEquals(4, rows.size)
        assertEquals(GirSdkLocator.RuntimeRow("org.gnome.Sdk", "50", "user"), rows[0])
    }

    @Test
    fun `pickBranch prefers the hint`() {
        val rows = GirSdkLocator.parseRuntimeRows(sampleOutput)
        assertEquals("50", GirSdkLocator.pickBranch(rows, "org.gnome.Sdk", "50"))
        assertEquals("49", GirSdkLocator.pickBranch(rows, "org.gnome.Sdk", "49"))
    }

    @Test
    fun `pickBranch falls back to the highest numeric branch`() {
        val rows = GirSdkLocator.parseRuntimeRows(sampleOutput)
        assertEquals("50", GirSdkLocator.pickBranch(rows, "org.gnome.Sdk", null))
    }

    @Test
    fun `pickBranch breaks ties in favor of user installations`() {
        val rows = GirSdkLocator.parseRuntimeRows(sampleOutput)
        assertEquals("50", GirSdkLocator.pickBranch(rows, "org.gnome.Sdk", "50"))
    }

    @Test
    fun `pickBranch returns null when the SDK is not installed`() {
        val rows = GirSdkLocator.parseRuntimeRows(sampleOutput)
        assertNull(GirSdkLocator.pickBranch(rows, "org.example.Sdk", null))
    }

    @Test
    fun `resolveFromBaseDir finds gir-1_0 and verifies Gtk gir`() {
        withTempDir { dir ->
            val girDir = dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0").apply { mkdirs() }
            girDir.resolve("Gtk-4.0.gir").writeText("<x/>")
            assertEquals(girDir, GirSdkLocator.resolveFromBaseDir(dir, "org.gnome.Sdk", "50"))
            assertNull(GirSdkLocator.resolveFromBaseDir(dir, "org.gnome.Sdk", "49"))
        }
    }

    @Test
    fun `locate uses the glob fallback when the flatpak CLI is unavailable`() {
        withTempDir { dir ->
            dir
                .resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0")
                .apply { mkdirs() }
                .resolve("Gtk-4.0.gir")
                .writeText("<x/>")

            val found =
                GirSdkLocator.locate(
                    sdkAppId = "org.gnome.Sdk",
                    branchHint = "50",
                    flatpakBinary = "/nonexistent/flatpak",
                    baseDirs = listOf(dir),
                )
            assertEquals(
                dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0"),
                found,
            )
        }
    }

    @Test
    fun `locate scans for the highest branch when no hint is given`() {
        withTempDir { dir ->
            dir
                .resolve("runtime/org.gnome.Sdk/x86_64/49/active/files/share/gir-1.0")
                .apply { mkdirs() }
                .resolve("Gtk-4.0.gir")
                .writeText("<x/>")
            dir
                .resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0")
                .apply { mkdirs() }
                .resolve("Gtk-4.0.gir")
                .writeText("<x/>")

            val found =
                GirSdkLocator.locate(
                    sdkAppId = "org.gnome.Sdk",
                    branchHint = null,
                    flatpakBinary = "/nonexistent/flatpak",
                    baseDirs = listOf(dir),
                )
            assertEquals(
                dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active/files/share/gir-1.0"),
                found,
            )
        }
    }

    @Test
    fun `locate returns null when no SDK is installed`() {
        withTempDir { dir ->
            assertNull(
                GirSdkLocator.locate("org.gnome.Sdk", null, "/nonexistent/flatpak", listOf(dir)),
            )
        }
    }

    @Test
    fun `locate returns null for blank or missing app ids`() {
        assertNull(GirSdkLocator.locate(null, null, "/nonexistent/flatpak"))
        assertNull(GirSdkLocator.locate("", null, "/nonexistent/flatpak"))
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDirectory()
        try {
            block(dir.toFile())
        } finally {
            dir.delete(true)
        }
    }
}
