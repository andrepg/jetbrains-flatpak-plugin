package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.utils.FlatpakManifestReader

class RunCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> {
        val appCommand = FlatpakManifestReader.readCommand(settings.manifestPath)
            ?: FlatpakManifestReader.readAppId(settings.manifestPath)
        return buildList {
            addAll(getFlatpakCommand())
            add("--run")
            addAll(buildSandboxOptions(settings))
            add(settings.buildDir)
            add(settings.manifestPath)
            if (appCommand != null) add(appCommand)
        }
    }
}
