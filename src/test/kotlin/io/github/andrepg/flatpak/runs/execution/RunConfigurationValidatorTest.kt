package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class RunConfigurationValidatorTest {
    private fun config(configure: FlatpakRunSettings.() -> Unit = {}): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configure(configuration)
        return configuration
    }

    @Test
    fun `valid configuration has no errors`() {
        withTempManifest { manifest ->
            val errors =
                RunConfigurationValidator.validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.path
                        buildDir = File(manifest.parentFile, "build").path
                    },
                )
            assertTrue(errors.isEmpty())
        }
    }

    @Test
    fun `default manifest path is reported`() {
        val errors =
            RunConfigurationValidator.validate(
                config {
                    command = UserVisibleCommand.BUILD
                    buildDir = "build"
                },
            )
        assertTrue(errors.any { it.contains("Manifest file not found") })
    }

    @Test
    fun `missing manifest file is reported`() {
        val errors =
            RunConfigurationValidator.validate(
                config {
                    command = UserVisibleCommand.BUILD
                    manifestPath = "does-not-exist.json"
                    buildDir = "build"
                },
            )
        assertTrue(errors.any { it.contains("Manifest file not found") })
    }

    @Test
    fun `all errors are collected in one call`() {
        val buildDirUnderFile = File(File.createTempFile("validator-parent", ".file"), "sub").path
        val errors =
            RunConfigurationValidator.validate(
                config {
                    command = UserVisibleCommand.BUILD
                    manifestPath = "does-not-exist.json"
                    buildDir = buildDirUnderFile
                },
            )
        assertEquals(2, errors.size)
        assertTrue(errors.any { it.contains("Manifest file not found") })
        assertTrue(errors.any { it.contains("Cannot create build directory") })
    }

    private fun withTempManifest(block: (File) -> Unit) {
        val dir =
            File.createTempFile("validator", ".dir").apply {
                delete()
                mkdirs()
            }
        try {
            val manifest =
                File(
                    dir,
                    "org.example.app.json",
                ).apply { writeText("{\"app-id\": \"org.example.app\", \"sdk\": \"org.gnome.Sdk\"}") }
            block(manifest)
        } finally {
            dir.deleteRecursively()
        }
    }
}
