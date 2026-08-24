package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

/**
 * The manifest path must be set and point to an existing file: the most common
 * misconfiguration (a moved or never-configured manifest) surfaces here instead
 * of as a flatpak command failure.
 */
class ManifestExistsRule : ValidationRule {
    override val id: String = "manifest-exists"

    override fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String> {
        if (config.manifestPath.isBlank()) {
            return listOf("Manifest path cannot be empty")
        }
        val manifestFile = resolveAgainstBase(basePath, config.manifestPath)
        if (!manifestFile.exists()) {
            return listOf("Manifest file not found: ${config.manifestPath}")
        }
        if (manifestFile.isDirectory) {
            return listOf("Manifest path is a directory, not a file: ${config.manifestPath}")
        }
        return emptyList()
    }
}
