package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.CommandExecutionArguments
import io.github.andrepg.flatpak.utils.FlatpakManifestVfsReader
import io.github.andrepg.shared.log.Log

class RunCommandFactory(
    private val hostBusAvailable: () -> Boolean = CommandExecutionArguments::hostHasFlatpakBus,
) : CommandFactory() {
    private val log = Log.getInstance(RunCommandFactory::class.java)

    override fun create(settings: FlatpakRunSettings): List<String> {
        val appCommand = FlatpakManifestVfsReader.readCommand(settings.project, settings.manifestPath)
            ?: FlatpakManifestVfsReader.readAppId(settings.project, settings.manifestPath)
        return buildList {
            addAll(getFlatpakCommand())
            add("--run")
            if (hostBusAvailable()) {
                addAll(CommandExecutionArguments.DEFAULT_BUS)
            } else {
                log.warn("Host exposes no /run/flatpak/bus; skipping D-Bus sockets (app gets the filtered default bus)")
            }
            addAll(buildSandboxOptions(settings))
            add(settings.effectiveBuildDir())
            add(settings.effectiveManifestPath())
            if (appCommand != null) add(appCommand)
        }
    }
}
