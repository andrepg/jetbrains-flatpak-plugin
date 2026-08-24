package io.github.andrepg.gtk.schema.locator

import com.intellij.util.io.delete
import io.github.andrepg.gtk.schema.FakeFlatpakCli
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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
    fun `locate ignores SDKs outside the supported list`() {
        assertNull(GirSdkLocator.locate("org.example.Sdk", "50", "/nonexistent/flatpak"))
    }

    @Test
    fun `locate returns null for blank or missing app ids`() {
        assertNull(GirSdkLocator.locate(null, null, "/nonexistent/flatpak"))
        assertNull(GirSdkLocator.locate("", null, "/nonexistent/flatpak"))
    }

    @Test
    fun `locate returns null when the flatpak CLI is unavailable`() {
        assertNull(GirSdkLocator.locate("org.gnome.Sdk", "50", "/nonexistent/flatpak"))
    }

    @Test
    fun `locate resolves the gir dir through the flatpak CLI`() {
        withTempDir { dir ->
            val installRoot =
                dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active").apply { mkdirs() }
            val girDir = installRoot.resolve("files/share/gir-1.0").apply { mkdirs() }
            girDir.resolve("Gtk-4.0.gir").writeText("<x/>")

            val flatpak = FakeFlatpakCli.install(dir, runtimes = "org.gnome.Sdk\t50\tuser", location = installRoot)

            assertEquals(girDir, GirSdkLocator.locate("org.gnome.Sdk", "50", flatpak.absolutePath))
        }
    }

    @Test
    fun `locate picks the highest installed branch without a hint`() {
        withTempDir { dir ->
            val girDirs =
                listOf("49", "50").map { branch ->
                    dir
                        .resolve("runtime/org.gnome.Sdk/x86_64/$branch/active/files/share/gir-1.0")
                        .apply { mkdirs() }
                        .also { it.resolve("Gtk-4.0.gir").writeText("<x/>") }
                }
            val runtimes = "org.gnome.Sdk\t49\tsystem\norg.gnome.Sdk\t50\tuser"
            val active50 = dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active")
            val flatpak = FakeFlatpakCli.install(dir, runtimes = runtimes, location = active50)

            assertEquals(girDirs[1], GirSdkLocator.locate("org.gnome.Sdk", null, flatpak.absolutePath))
        }
    }

    @Test
    fun `locate returns null when Gtk gir is missing at the resolved location`() {
        withTempDir { dir ->
            val installRoot = dir.resolve("runtime/org.gnome.Sdk/x86_64/50/active").apply { mkdirs() }
            val flatpak = FakeFlatpakCli.install(dir, runtimes = "org.gnome.Sdk\t50\tuser", location = installRoot)

            assertNull(GirSdkLocator.locate("org.gnome.Sdk", "50", flatpak.absolutePath))
        }
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
