package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

class DeepCleanCommandFactory : CommandFactory() {
    private fun getFlatpakHomePath(): String = System.getProperty("user.home").let {
        (if (it.endsWith("/")) it else "$it/").plus(".cache/flatpak-builder/")
    }

    override fun create(settings: FlatpakRunSettings): List<String> {
        val pathsToDelete = listOfNotNull(
            settings.buildDir,
            getFlatpakHomePath(),
            // TODO Implement Project's flatpak-builder clean up
        )

        return listOf("rm", "-rf").plus(pathsToDelete)
    }
}