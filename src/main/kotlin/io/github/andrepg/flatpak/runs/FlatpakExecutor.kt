package io.github.andrepg.flatpak.runs

import io.github.andrepg.flatpak.enums.FlatpakCommand

class FlatpakExecutor {
    fun commandLine(command: FlatpakCommand, manifest: String, buildDir: String): List<String> {
        val flatpakBinary = "/usr/bin/flatpak"
        val flatpakBuilderBinary = "/usr/bin/flatpak run org.flatpak.Builder"
        
        return when (command) {
            FlatpakCommand.BUILD ->
                listOf(flatpakBuilderBinary, buildDir, manifest)

            FlatpakCommand.CLEAN ->
                listOf("rm", "-rf", buildDir)

            FlatpakCommand.COMPILE ->
                listOf(flatpakBuilderBinary, "--build-only", buildDir, manifest)

            FlatpakCommand.EXPORT ->
                listOf(flatpakBuilderBinary, "--repo=repo", buildDir, manifest)

            FlatpakCommand.RUN ->
                listOf(flatpakBinary, "run", manifest)
        }
    }
}