package io.github.andrepg.flatpak.runs.configuration.validation

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
class ManifestParsesRuleTest {
    private val rule = ManifestParsesRule()

    private fun config(manifestPath: String): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configuration.command = UserVisibleCommand.BUILD
        configuration.manifestPath = manifestPath
        return configuration
    }

    @Test
    fun `json manifest with app-id passes`() {
        withManifest("""{"app-id": "org.example.App"}""") { manifest ->
            assertTrue(rule.check(config(manifest.path), null).isEmpty())
        }
    }

    @Test
    fun `id is accepted as application identity`() {
        withManifest("""{"id": "org.example.App"}""") { manifest ->
            assertTrue(rule.check(config(manifest.path), null).isEmpty())
        }
    }

    @Test
    fun `manifest without identity fields is reported`() {
        withManifest("""{"sdk": "org.gnome.Sdk", "runtime": "org.gnome.Platform"}""") { manifest ->
            val errors = rule.check(config(manifest.path), null)
            assertTrue(errors.single().contains("neither 'app-id' nor 'id'"))
        }
    }

    @Test
    fun `unparseable content is reported`() {
        withManifest("{ not json at all") { manifest ->
            val errors = rule.check(config(manifest.path), null)
            assertTrue(errors.single().contains("could not be parsed"))
        }
    }

    @Test
    fun `yaml manifests parse too`() {
        val yaml =
            """
            id: org.example.App
            sdk: org.gnome.Sdk
            """.trimIndent()
        withManifest(yaml, name = "org.example.App.yml") { manifest ->
            assertTrue(rule.check(config(manifest.path), null).isEmpty())
        }
    }

    @Test
    fun `missing or blank manifest is skipped - exists rule owns that report`() {
        assertTrue(rule.check(config("does-not-exist.json"), null).isEmpty())
        assertTrue(rule.check(config(""), null).isEmpty())
    }

    private fun withManifest(
        content: String,
        name: String = "org.example.App.json",
        block: (File) -> Unit,
    ) {
        val dir = File.createTempFile("parses-rule", ".dir").apply { delete(); mkdirs() }
        try {
            block(File(dir, name).apply { writeText(content) })
        } finally {
            dir.deleteRecursively()
        }
    }
}
