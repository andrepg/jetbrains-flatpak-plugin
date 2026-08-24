package io.github.andrepg.flatpak.runs.configuration.validation

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class CustomHasArgumentsRuleTest {
    private val rule = CustomHasArgumentsRule()

    private fun config(
        command: UserVisibleCommand,
        arguments: List<String>,
    ): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configuration.command = command
        configuration.customArguments = arguments
        return configuration
    }

    @Test
    fun `custom command without arguments is rejected`() {
        assertEquals(
            listOf("Custom command requires at least one argument"),
            rule.check(config(UserVisibleCommand.CUSTOM, emptyList()), null),
        )
    }

    @Test
    fun `custom command with arguments passes`() {
        assertEquals(emptyList<String>(), rule.check(config(UserVisibleCommand.CUSTOM, listOf("--from-assets")), null))
    }

    @Test
    fun `other commands never require arguments`() {
        UserVisibleCommand.entries
            .filter { it != UserVisibleCommand.CUSTOM }
            .forEach { command ->
                assertEquals(emptyList<String>(), rule.check(config(command, emptyList()), null))
            }
    }
}
