package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

class ValidateManifestCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> =
        this.getFlatpakCommand().plus(listOf("--show-manifest", settings.effectiveManifestPath()))
}
