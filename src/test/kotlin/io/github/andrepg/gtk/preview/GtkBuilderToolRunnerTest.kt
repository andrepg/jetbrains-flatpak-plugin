package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.preview.binary.GtkHeadlessRendererDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GtkBuilderToolRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        GtkBuilderToolRunner.clearCachedBranchForTests()
    }

    @Test
    fun `needsCompilation is true when the renderer is not cached`() {
        val configDir = tmp.newFolder("cfg")

        val runner = GtkBuilderToolRunner(flatpakBinary = "/usr/bin/flatpak", configDir = configDir)

        assertTrue(runner.needsCompilation())
    }

    @Test
    fun `needsCompilation is false when the cached binary matches the marker`() {
        val configDir = tmp.newFolder("cfg")
        val binDir = configDir.toPath().resolve("bin")
        Files.createDirectories(binDir)
        binDir.resolve(GtkPreviewConfig.RENDERER_BINARY_NAME).also {
            Files.createFile(it)
            it.toFile().setExecutable(true)
        }
        binDir.resolve("${GtkPreviewConfig.RENDERER_BINARY_NAME}.version")
            .toFile()
            .writeText(GtkHeadlessRendererDownloader.RENDERER_VERSION)

        val runner = GtkBuilderToolRunner(flatpakBinary = "/usr/bin/flatpak", configDir = configDir)

        assertFalse(runner.needsCompilation())
    }

    @Test
    fun `renderCommand targets the headless compositor display inside the SDK`() {
        val configDir = tmp.newFolder("cfg")
        val binary = tmp.newFile("gtk_embedded_preview")
        val uiFile = tmp.newFile("window.ui").toPath()
        val output = tmp.newFolder("out").toPath().resolve("preview-x.png")
        val runner = GtkBuilderToolRunner(flatpakBinary = "/usr/bin/flatpak", configDir = configDir)

        val cmd =
            runner.renderCommand(
                flatpakBinary = "/usr/bin/flatpak",
                branch = "50",
                binary = binary,
                uiFile = uiFile,
                outputPng = output,
                width = 640,
                height = 360,
                searchDir = uiFile.parent,
                display = "wayland-2",
                runtimeDir = "/run/user/1000",
            )

        // Head: flatpak run targeting the GNOME SDK
        assertEquals("/usr/bin/flatpak", cmd[0])
        assertEquals("run", cmd[1])
        assertTrue("app ref missing", cmd.contains("org.gnome.Sdk//50"))

        // Sandbox options
        assertTrue("--socket=wayland missing", cmd.contains("--socket=wayland"))
        assertTrue("--filesystem=host missing", cmd.contains("--filesystem=host"))
        assertTrue("--filesystem=…/runtime missing", cmd.contains("--filesystem=/run/user/1000"))
        assertTrue("--env=GDK_BACKEND missing", cmd.contains("--env=GDK_BACKEND=wayland"))
        assertTrue("--env=WAYLAND_DISPLAY missing", cmd.contains("--env=WAYLAND_DISPLAY=wayland-2"))
        assertTrue("--env=XDG_RUNTIME_DIR missing", cmd.contains("--env=XDG_RUNTIME_DIR=/run/user/1000"))
        assertTrue("--command missing", cmd.contains("--command=${binary.absolutePath}"))

        // Positional arguments at the tail: uiFile, output, width, height, searchDir
        val tail = cmd.takeLast(5)
        assertEquals(
            listOf(
                uiFile.toAbsolutePath().toString(),
                output.toAbsolutePath().toString(),
                "640",
                "360",
                uiFile.parent.toAbsolutePath().toString(),
            ),
            tail,
        )
    }
}
