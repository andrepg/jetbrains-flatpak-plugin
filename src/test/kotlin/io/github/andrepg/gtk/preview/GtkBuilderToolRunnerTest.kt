package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.ProcessResult
import org.junit.Assert.assertEquals
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
    fun `compile runs make inside the GNOME SDK and writes the hash`() {
        val configDir = tmp.newFolder("cfg").toPath()
        // Pre-create an executable binary so compile() succeeds after the
        // fake make returns (no-op). The runner checks canExecute() once the
        // (in this case already-compiled) binary is in place.
        val binary = configDir.resolve("gtk_embedded_preview").also {
            Files.createFile(it)
            it.toFile().setExecutable(true)
        }
        val commands = mutableListOf<List<String>>()
        val fakeRunner = CommandRunner { command, _ ->
            commands += command
            when {
                command.size >= 2 && command[1] == "list" -> ProcessResult(0, "org.gnome.Sdk\t50\tuser\n", "")
                else -> ProcessResult(0, "", "")
            }
        }

        val runner = GtkBuilderToolRunner(runner = fakeRunner, flatpakBinary = "/usr/bin/flatpak", configDir = configDir.toFile())
        val result = runner.compile()

        // Branch detection happened
        assertTrue(commands.any { it.size >= 2 && it[1] == "list" })

        // Make was invoked with the correct shape
        val expectedMake = listOf(
            "/usr/bin/flatpak", "run",
            "--command=make", "--filesystem=host",
            "org.gnome.Sdk//50",
            "-C", configDir.resolve("compiler").toFile().absolutePath,
            "build-flatpak",
            "TARGET=${binary.toFile().absolutePath}",
        )
        assertTrue("Make command not recorded: $commands", commands.contains(expectedMake))

        // The source hash file was written
        assertTrue("Hash file missing", configDir.resolve("gtk_embedded_preview.hash").toFile().exists())

        // compile() returned the expected binary
        assertEquals(binary.toFile(), result)
    }

    @Test
    fun `compile skips recompilation when the hash is current`() {
        val configDir = tmp.newFolder("cfg").toPath()
        configDir.resolve("gtk_embedded_preview").also {
            Files.createFile(it)
            it.toFile().setExecutable(true)
        }
        // First compile writes the current hash and (re)uses the existing
        // executable binary, via a fake flatpak.
        val firstRunner = CommandRunner { command, _ ->
            when {
                command.size >= 2 && command[1] == "list" -> ProcessResult(0, "org.gnome.Sdk\t50\tuser\n", "")
                else -> ProcessResult(0, "", "")
            }
        }
        GtkBuilderToolRunner(runner = firstRunner, flatpakBinary = "/usr/bin/flatpak", configDir = configDir.toFile()).compile()

        val fakeRunner = CommandRunner { _, _ -> error("should not be called — recompilation skipped") }
        val binary = configDir.resolve("gtk_embedded_preview").toFile()
        val secondRunner = GtkBuilderToolRunner(runner = fakeRunner, flatpakBinary = "/usr/bin/flatpak", configDir = configDir.toFile())
        // isCurrentBinary now matches, so compile() returns the cached binary
        // without touching the flatpak runner at all.
        val result = secondRunner.compile()
        assertEquals(binary, result)
    }

    @Test
    fun `renderCommand targets the headless compositor display inside the SDK`() {
        val configDir = tmp.newFolder("cfg")
        val binary = tmp.newFile("gtk_embedded_preview")
        val uiFile = tmp.newFile("window.ui").toPath()
        val output = tmp.newFolder("out").toPath().resolve("preview-x.png")
        val runner = GtkBuilderToolRunner(flatpakBinary = "/usr/bin/flatpak", configDir = configDir)

        val cmd = runner.renderCommand(
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