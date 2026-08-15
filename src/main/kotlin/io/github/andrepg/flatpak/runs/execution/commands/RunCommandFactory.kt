package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.CommandExecutionArguments
import io.github.andrepg.flatpak.utils.FlatpakManifestVfsReader

class RunCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> {
        val appCommand = FlatpakManifestVfsReader.readCommand(settings.project, settings.manifestPath)
            ?: FlatpakManifestVfsReader.readAppId(settings.project, settings.manifestPath)
        return buildList {
            addAll(getFlatpakCommand())
            add("--run")
            addAll(CommandExecutionArguments.DEFAULT_BUS)
            addAll(buildSandboxOptions(settings))
            add(settings.effectiveBuildDir())
            add(settings.effectiveManifestPath())
            if (appCommand != null) add(appCommand)
        }
    }
}
