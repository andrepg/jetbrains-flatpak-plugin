package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.utils.FlatpakManifestReader

class RunCommandFactory: CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> = this.getFlatpakCommand().let {
        val appCommand = FlatpakManifestReader.readCommand(settings.manifestPath)
        val appId = FlatpakManifestReader.readAppId(settings.manifestPath)

        val sandboxArgs = this.buildSandboxOptions(settings).joinToString(" ")

        it.plus(listOfNotNull(
            "--run",
            sandboxArgs,
            settings.buildDir,
            settings.manifestPath,
            appCommand ?: appId
        ))
    }
}