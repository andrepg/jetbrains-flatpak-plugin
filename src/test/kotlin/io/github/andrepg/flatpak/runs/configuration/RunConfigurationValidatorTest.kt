package io.github.andrepg.flatpak.runs.configuration

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val buildDirAsFile = File.createTempFile("validator-build", ".file")
        try {
            val errors =
                RunConfigurationValidator.validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = "does-not-exist.json"
                        buildDir = buildDirAsFile.path
                    },
                )
            assertEquals(2, errors.size)
            assertTrue(errors.any { it.contains("Manifest file not found") })
            assertTrue(errors.any { it.contains("is a file, not a directory") })
        } finally {
            buildDirAsFile.delete()
        }
    }

    @Test
    fun `missing build directory is accepted even when its parent is invalid`() {
        val parentAsFile = File.createTempFile("validator-parent", ".file")
        try {
            val errors =
                RunConfigurationValidator.validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = "does-not-exist.json"
                        buildDir = File(parentAsFile, "sub").path
                    },
                )
            assertTrue(errors.none { it.contains("Build directory") })
        } finally {
            parentAsFile.delete()
        }
    }

    @Test
    fun `relative paths resolve against base path without creating anything`() {
        withTempManifest { manifest ->
            val base = manifest.parentFile
            val buildDirInCwd = File("_build")
            val buildDirInCwdExisted = buildDirInCwd.exists()
            val errors =
                RunConfigurationValidator.validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.name
                        buildDir = "_build"
                    },
                    basePath = base.path,
                )
            assertTrue(errors.isEmpty())
            assertFalse("validation must not create the build dir", File(base, "_build").exists())
            assertEquals(buildDirInCwdExisted, buildDirInCwd.exists())
        }
    }

    @Test
    fun `relative manifest path resolves against base path`() {
        withTempManifest { manifest ->
            val errors =
                RunConfigurationValidator.validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.name
                        buildDir = "_build"
                    },
                    basePath = manifest.parent,
                )
            assertTrue(errors.none { it.contains("Manifest file not found") })
        }
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
