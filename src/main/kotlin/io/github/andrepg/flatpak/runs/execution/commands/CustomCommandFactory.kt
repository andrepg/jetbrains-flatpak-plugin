package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

class CustomCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> = this.getFlatpakCommand().plus(
        listOf(
            settings.buildDir,
            settings.manifestPath,
            settings.customArguments.joinToString(" ")
        )
    )
}