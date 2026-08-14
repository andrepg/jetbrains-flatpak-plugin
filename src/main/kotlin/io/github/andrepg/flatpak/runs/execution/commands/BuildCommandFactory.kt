package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.CommandExecutionArguments

class BuildCommandFactory : CommandFactory() {
    private val statesWithForceClean = listOf(
        UserVisibleCommand.EXPORT,
        UserVisibleCommand.BUILD
    )

    override fun create(settings: FlatpakRunSettings): List<String> = this.getFlatpakCommand().let {
        val allowForceClean = statesWithForceClean.contains(settings.command)

        if (settings.enableForceClean && allowForceClean) {
            it.plus(CommandExecutionArguments.FORCE_CLEAN)
        }

        it.plusElement(settings.buildDir)
        it.plusElement(settings.manifestPath)
    }
}