package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.CommandExecutionArguments

class BuildCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> = buildList {
        addAll(getFlatpakCommand())
        if (settings.enableForceClean && settings.command == UserVisibleCommand.BUILD) {
            addAll(CommandExecutionArguments.FORCE_CLEAN)
        }
        add(settings.buildDir)
        add(settings.manifestPath)
    }
}
