package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

class ExportBundleCommandFactory : CommandFactory() {
    override fun create(settings: FlatpakRunSettings): List<String> =
        this.getFlatpakCommand().plus(
            listOf(
                "--repo=repo-build",
                settings.effectiveBuildDir(),
                settings.effectiveManifestPath(),
            ),
        )
}
