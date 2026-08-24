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

class BuildDirValidRuleTest {
    private val rule = BuildDirValidRule()

    private fun config(buildDir: String): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configuration.command = UserVisibleCommand.BUILD
        configuration.buildDir = buildDir
        return configuration
    }

    @Test
    fun `blank build dir falls back to the default and missing default is valid`() {
        // FlatpakRunSettings normalizes blank to the _build default; a missing
        // directory stays valid wherever it resolves.
        val emptyBase = File.createTempFile("builddir-rule", ".dir").apply { delete(); mkdirs() }
        try {
            assertTrue(rule.check(config(""), emptyBase.path).isEmpty())
        } finally {
            emptyBase.deleteRecursively()
        }
    }

    @Test
    fun `missing build dir is valid - flatpak-builder creates it`() {
        assertTrue(rule.check(config("does-not-exist/_build"), null).isEmpty())
    }

    @Test
    fun `existing writable directory is valid`() {
        val dir = File.createTempFile("builddir-rule", ".dir").apply { delete(); mkdirs() }
        try {
            assertTrue(rule.check(config(dir.path), null).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file as build dir is reported`() {
        val file = File.createTempFile("builddir-rule", ".file")
        try {
            val errors = rule.check(config(file.path), null)
            assertTrue(errors.single().contains("is a file, not a directory"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `unwritable existing build dir is reported`() {
        val dir = File.createTempFile("builddir-rule", ".dir").apply { delete(); mkdirs() }
        try {
            // Root ignores file permissions, so this case only applies to regular users.
            if (!dir.setWritable(false) || dir.canWrite()) return
            val errors = rule.check(config(dir.path), null)
            assertTrue(errors.single().contains("not writable"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
