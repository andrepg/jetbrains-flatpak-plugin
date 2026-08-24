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

class FlatpakFoundRuleTest {
    private fun config(): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configuration.command = UserVisibleCommand.BUILD
        return configuration
    }

    @Test
    fun `absolute existing executable passes`() {
        val binary = File.createTempFile("flatpak-rule", ".bin").apply { setExecutable(true) }
        try {
            val rule = FlatpakFoundRule(binaryPath = { binary.path }, locate = ::realLocate)
            assertTrue(rule.check(config(), null).isEmpty())
        } finally {
            binary.delete()
        }
    }

    @Test
    fun `absolute non-executable file is rejected`() {
        val binary = File.createTempFile("flatpak-rule", ".bin")
        try {
            val rule = FlatpakFoundRule(binaryPath = { binary.path }, locate = ::realLocate)
            assertTrue(rule.check(config(), null).single().contains("Flatpak CLI not found"))
        } finally {
            binary.delete()
        }
    }

    @Test
    fun `bare name resolved by the locator passes`() {
        val found = File("/usr/bin/flatpak-fake")
        val rule =
            FlatpakFoundRule(
                binaryPath = { "flatpak" },
                locate = { name -> if (name == "flatpak") found else null },
            )
        assertTrue(rule.check(config(), null).isEmpty())
    }

    @Test
    fun `missing binary reports the configured name and remediation`() {
        val rule = FlatpakFoundRule(binaryPath = { "flatpak-xyz" }, locate = { null })
        val errors = rule.check(config(), null)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("'flatpak-xyz'"))
        assertTrue(errors.single().contains("plugin settings"))
    }

    @Test
    fun `blank configured path is rejected`() {
        val rule = FlatpakFoundRule(binaryPath = { "  " }, locate = { error("must not be called") })
        assertEquals(listOf("Flatpak binary path cannot be empty"), rule.check(config(), null))
    }
}

/** The production locator, used where tests need real filesystem semantics. */
private fun realLocate(raw: String): File? {
    val candidate = File(raw)
    return candidate.takeIf { it.isFile && it.canExecute() }
}
