package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

class BuildCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> =
        buildList {
            addAll(getFlatpakCommand())
            if (settings.enableForceClean && settings.command == UserVisibleCommand.BUILD) {
                addAll(CommandExecutionArguments.FORCE_CLEAN)
            }
            add(settings.effectiveBuildDir())
            add(settings.effectiveManifestPath())
        }
}
