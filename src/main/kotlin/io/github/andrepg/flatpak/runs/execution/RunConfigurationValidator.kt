package io.github.andrepg.flatpak.runs.execution

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import java.io.File

/**
 * Collects run-configuration errors without throwing and without mutating the
 * filesystem: no directories are created or removed here — flatpak-builder owns
 * build-directory creation at run time, so a missing build directory is valid;
 * only pre-existing paths are checked.
 *
 * Pure JDK logic (no platform imports) so it is unit-testable and reusable from
 * the editor (Apply time) and from [FlatpakRunSettings.checkConfiguration].
 */
object RunConfigurationValidator {
    /**
     * Validates [config] and returns every problem found; empty list means valid.
     *
     * Relative paths are resolved against [basePath] when provided; when it is
     * null or blank they fall back to the process working directory.
     */
    fun validate(
        config: FlatpakRunSettings,
        basePath: String? = null,
    ): List<String> {
        val errors = mutableListOf<String>()

        if (config.manifestPath.isBlank()) {
            errors += "Manifest path cannot be empty"
        } else {
            val manifestFile = resolve(basePath, config.manifestPath)
            if (!manifestFile.exists()) {
                errors += "Manifest file not found: ${config.manifestPath}"
            } else if (manifestFile.isDirectory) {
                errors += "Manifest path is a directory, not a file: ${config.manifestPath}"
            }
        }

        if (config.buildDir.isBlank()) {
            errors += "Build directory cannot be empty"
        } else {
            errors += buildDirErrors(basePath, config.buildDir)
        }

        return errors
    }

    /**
     * Only pre-existing paths are validated: flatpak-builder creates the build
     * directory itself, and reports any creation failure with its own error.
     */
    private fun buildDirErrors(
        basePath: String?,
        buildDir: String,
    ): List<String> {
        val buildDirFile = resolve(basePath, buildDir)
        if (!buildDirFile.exists()) return emptyList()
        if (!buildDirFile.isDirectory) {
            return listOf("Build directory is a file, not a directory: $buildDir")
        }
        return if (buildDirFile.canWrite()) {
            emptyList()
        } else {
            listOf("Build directory is not writable: $buildDir")
        }
    }

    private fun resolve(
        basePath: String?,
        path: String,
    ): File {
        val file = File(path)
        if (file.isAbsolute || basePath.isNullOrBlank()) return file
        return File(basePath, path)
    }
}
