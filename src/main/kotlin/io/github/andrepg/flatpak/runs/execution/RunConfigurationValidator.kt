package io.github.andrepg.flatpak.runs.execution

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import java.io.File

/**
 * Collects run-configuration errors without throwing.
 *
 * Pure JDK logic (no platform imports) so it is unit-testable and reusable from
 * the editor (Apply time) and from [FlatpakRunSettings.checkConfiguration].
 */
object RunConfigurationValidator {

    /**
     * Validates [config] and returns every problem found; empty list means valid.
     */
    fun validate(config: FlatpakRunSettings): List<String> {
        val errors = mutableListOf<String>()

        if (config.manifestPath.isBlank()) {
            errors += "Manifest path cannot be empty"
        } else {
            val manifestFile = File(config.manifestPath)
            if (!manifestFile.exists()) {
                errors += "Manifest file not found: ${config.manifestPath}"
            } else if (manifestFile.isDirectory) {
                errors += "Manifest path is a directory, not a file: ${config.manifestPath}"
            }
        }

        if (config.buildDir.isBlank()) {
            errors += "Build directory cannot be empty"
        } else {
            val buildDirFile = File(config.buildDir)
            if (!buildDirFile.exists() && !buildDirFile.mkdirs()) {
                errors += "Cannot create build directory: ${config.buildDir}"
            } else if (!buildDirFile.canWrite()) {
                errors += "Build directory is not writable: ${config.buildDir}"
            }
        }

        return errors
    }
}
