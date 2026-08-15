package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class CommandExecutionEngineTest {

    private val engine = CommandExecutionEngine(mock(Project::class.java))

    private fun config(configure: FlatpakRunSettings.() -> Unit = {}): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes())
        configure(configuration)
        return configuration
    }

    private fun manifest(content: String): String {
        val file = File.createTempFile("manifest", ".json")
        file.writeText(content)
        file.deleteOnExit()
        return file.path
    }

    private val sampleManifestPath = manifest(
        """
        {"id":"org.example.MyApp","sdk":"org.gnome.Sdk//50","runtime":"org.gnome.Platform//50","command":"my-app.sh"}
        """.trimIndent()
    )

    @Test
    fun `build run command uses manifest command and no force-clean`() {
        val line = engine.buildCommand(
            InternalCommand.RUN,
            config {
                command = UserVisibleCommand.RUN
                manifestPath = sampleManifestPath
                buildDir = "/tmp/build"
            }
        )
        assertEquals(
            listOf("/usr/bin/flatpak", "run", "org.flatpak.Builder", "--run", "/tmp/build", sampleManifestPath, "my-app.sh"),
            line
        )
    }

    @Test
    fun `build run command injects portal sandbox options before positional args`() {
        val line = engine.buildCommand(
            InternalCommand.RUN,
            config {
                command = UserVisibleCommand.RUN
                manifestPath = sampleManifestPath
                buildDir = "/tmp/build"
                enablePortals = true
                enableThemes = true
                enableAudio = true
                enableWayland = true
            }
        )
        assertTrue(line.contains("--run"))
        assertTrue(line.contains("--talk-name=org.freedesktop.portal.*"))
        assertTrue(line.contains("--device=dri"))
        assertTrue(line.contains("--env=GTK_USE_PORTAL=1"))
        assertTrue(line.contains("--filesystem=xdg-config/gtk-3.0:ro"))
        assertTrue(line.contains("--filesystem=xdg-data/icons:ro"))
        assertTrue(line.contains("--filesystem=xdg-data/themes:ro"))
        assertTrue(line.contains("--filesystem=xdg-config/glib-2.0"))
        assertTrue(line.contains("--socket=pulseaudio"))
        assertTrue(line.contains("--socket=wayland"))

        // Sandbox options must appear between --run and the positional DIRECTORY MANIFEST COMMAND args
        val runIndex = line.indexOf("--run")
        val positional = line.indexOf("/tmp/build")
        line.subList(runIndex + 1, positional).forEach { option ->
            assertTrue("sandbox option should precede positional args: $option", option.startsWith("-"))
        }
        assertFalse("--force-clean is invalid for --run mode", line.contains("--force-clean"))
        assertEquals("my-app.sh", line.last())
    }

    @Test
    fun `build run command falls back to app-id when manifest has no command`() {
        val noCommandManifest = manifest("""{"id":"org.example.MyApp","sdk":"org.gnome.Sdk//50"}""")
        val line = engine.buildCommand(
            InternalCommand.RUN,
            config {
                command = UserVisibleCommand.RUN
                manifestPath = noCommandManifest
                buildDir = "/tmp/build"
            }
        )
        assertEquals("org.example.MyApp", line.last())
    }

    @Test
    fun `build command applies force-clean only for the BUILD command`() {
        val buildConfig = config {
            manifestPath = sampleManifestPath
            buildDir = "/tmp/build"
            enableForceClean = true
        }
        assertTrue(
            "expected --force-clean for BUILD",
            engine.buildCommand(InternalCommand.BUILD, buildConfig).contains("--force-clean")
        )

        val exportConfig = config {
            command = UserVisibleCommand.EXPORT
            manifestPath = sampleManifestPath
            buildDir = "/tmp/build"
            enableForceClean = true
        }
        assertFalse(
            "expected no --force-clean for EXPORT",
            engine.buildCommand(InternalCommand.EXPORT, exportConfig).contains("--force-clean")
        )
    }

    @Test
    fun `build command maps each command to its own line`() {
        val config = config {
            manifestPath = sampleManifestPath
            buildDir = "/tmp/build"
            customArguments = listOf("--arg1")
        }

        assertEquals(
            listOf("/usr/bin/flatpak", "run", "org.flatpak.Builder", "/tmp/build", sampleManifestPath),
            engine.buildCommand(InternalCommand.BUILD, config)
        )
        assertEquals(
            listOf("/usr/bin/flatpak", "run", "org.flatpak.Builder", "--repo=repo-build", "/tmp/build", sampleManifestPath),
            engine.buildCommand(InternalCommand.EXPORT, config)
        )
        assertEquals(
            listOf("/usr/bin/flatpak", "run", "org.flatpak.Builder", "--show-manifest", sampleManifestPath),
            engine.buildCommand(InternalCommand.VALIDATE, config)
        )
        assertEquals(
            listOf("/usr/bin/flatpak", "run", "org.flatpak.Builder", "/tmp/build", sampleManifestPath, "--arg1"),
            engine.buildCommand(InternalCommand.CUSTOM, config)
        )
    }
}
