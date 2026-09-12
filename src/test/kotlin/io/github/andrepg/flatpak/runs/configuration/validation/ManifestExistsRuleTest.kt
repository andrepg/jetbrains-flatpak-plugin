package io.github.andrepg.flatpak.runs.configuration.validation

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class ManifestExistsRuleTest {
    private val rule = ManifestExistsRule()

    private fun config(manifestPath: String): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configuration.command = UserVisibleCommand.BUILD
        configuration.manifestPath = manifestPath
        return configuration
    }

    @Test
    fun `blank path falls back to the configured default and is checked like any other`() {
        // FlatpakRunSettings normalizes blank to the flatpak.json default.
        val errors = rule.check(config(""), "/nonexistent-base")
        assertEquals(listOf("Manifest file not found: flatpak.json"), errors)
    }

    @Test
    fun `missing file is reported with the configured path`() {
        val errors = rule.check(config("does-not-exist.json"), null)
        assertEquals(listOf("Manifest file not found: does-not-exist.json"), errors)
    }

    @Test
    fun `directory as manifest is reported`() {
        val dir =
            File.createTempFile("manifest-rule", ".dir").apply {
                delete()
                mkdirs()
            }
        try {
            val errors = rule.check(config(dir.path), null)
            assertTrue(errors.single().contains("is a directory, not a file"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existing file passes and relative paths resolve against base`() {
        val manifest = File.createTempFile("manifest-rule", ".json")
        try {
            assertTrue(rule.check(config(manifest.path), null).isEmpty())
            assertTrue(rule.check(config(manifest.name), manifest.parent).isEmpty())
        } finally {
            manifest.delete()
        }
    }
}
