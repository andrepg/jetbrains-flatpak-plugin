package io.github.andrepg.flatpak.runs.configuration

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import io.github.andrepg.flatpak.runs.configuration.validation.BuildDirValidRule
import io.github.andrepg.flatpak.runs.configuration.validation.CustomHasArgumentsRule
import io.github.andrepg.flatpak.runs.configuration.validation.FlatpakFoundRule
import io.github.andrepg.flatpak.runs.configuration.validation.ManifestExistsRule
import io.github.andrepg.flatpak.runs.configuration.validation.ManifestParsesRule
import io.github.andrepg.flatpak.runs.configuration.validation.ValidationRule
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

    /**
     * The filesystem/manifest/custom subset of the production chain: keeps these
     * tests independent of whether the host has a flatpak binary installed
     * ([FlatpakFoundRule] is covered by its own hermetic tests).
     */
    private fun rules(vararg extra: ValidationRule): List<ValidationRule> =
        listOf(
            ManifestExistsRule(),
            ManifestParsesRule(),
            BuildDirValidRule(),
            CustomHasArgumentsRule(),
        ) + extra

    private fun validate(
        configuration: FlatpakRunSettings,
        basePath: String? = null,
        chain: List<ValidationRule> = rules(),
    ): List<String> = RunConfigurationValidator.validate(configuration, basePath, chain)

    @Test
    fun `valid configuration has no errors`() {
        withTempManifest { manifest ->
            val errors =
                validate(
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
            validate(
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
            validate(
                config {
                    command = UserVisibleCommand.BUILD
                    manifestPath = "does-not-exist.json"
                    buildDir = "build"
                },
            )
        assertTrue(errors.any { it.contains("Manifest file not found") })
        assertFalse("missing files are the exists rule's report only", errors.any { it.contains("could not be parsed") })
    }

    @Test
    fun `all errors are collected in one call`() {
        val buildDirAsFile = File.createTempFile("validator-build", ".file")
        try {
            val errors =
                validate(
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
    fun `unparseable manifest is reported`() {
        withTempManifest { manifest ->
            manifest.writeText("{ this is not json")
            val errors =
                validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.path
                        buildDir = "_build"
                    },
                )
            assertTrue(errors.single().contains("could not be parsed"))
        }
    }

    @Test
    fun `manifest without application identity is reported`() {
        withTempManifest { manifest ->
            manifest.writeText("""{"sdk": "org.gnome.Sdk"}""")
            val errors =
                validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.path
                        buildDir = "_build"
                    },
                )
            assertTrue(errors.single().contains("'app-id' nor 'id'"))
        }
    }

    @Test
    fun `custom command without arguments is reported`() {
        withTempManifest { manifest ->
            val errors =
                validate(
                    config {
                        command = UserVisibleCommand.CUSTOM
                        manifestPath = manifest.path
                        buildDir = "_build"
                        customArguments = emptyList()
                    },
                )
            assertTrue(errors.single().contains("requires at least one argument"))
        }
    }

    @Test
    fun `missing flatpak binary is reported through the chain`() {
        withTempManifest { manifest ->
            val errors =
                validate(
                    config {
                        command = UserVisibleCommand.BUILD
                        manifestPath = manifest.path
                        buildDir = "_build"
                    },
                    chain =
                        rules(
                            FlatpakFoundRule(
                                binaryPath = { "flatpak" },
                                locate = { null },
                            ),
                        ),
                )
            assertTrue(errors.single().contains("Flatpak CLI not found"))
        }
    }

    @Test
    fun `a rule that throws is wrapped instead of breaking the check`() {
        val throwing =
            object : ValidationRule {
                override val id = "throwing"

                override fun check(
                    config: FlatpakRunSettings,
                    basePath: String?,
                ): List<String> = error("boom")
            }
        val errors = validate(config { command = UserVisibleCommand.BUILD }, chain = listOf(throwing))
        assertEquals(listOf("throwing: validation failed unexpectedly (boom)"), errors)
    }

    @Test
    fun `missing build directory is accepted even when its parent is invalid`() {
        val parentAsFile = File.createTempFile("validator-parent", ".file")
        try {
            val errors =
                validate(
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
                validate(
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
                validate(
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
