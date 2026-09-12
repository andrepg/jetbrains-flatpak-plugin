package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

/**
 * The build directory must be set and, when it already exists, be a writable
 * directory. A missing directory is valid: flatpak-builder owns its creation at
 * run time, so validation must not create or require it (only pre-existing
 * paths are checked).
 */
class BuildDirValidRule : ValidationRule {
    override val id: String = "build-dir-valid"

    override fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String> {
        if (config.buildDir.isBlank()) {
            return listOf("Build directory cannot be empty")
        }
        val buildDirFile = resolveAgainstBase(basePath, config.buildDir)
        if (!buildDirFile.exists()) return emptyList()
        if (!buildDirFile.isDirectory) {
            return listOf("Build directory is a file, not a directory: ${config.buildDir}")
        }
        return if (buildDirFile.canWrite()) {
            emptyList()
        } else {
            listOf("Build directory is not writable: ${config.buildDir}")
        }
    }
}
