package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import java.io.File

/**
 * One named, independently reviewable run-configuration check, chained inside
 * [io.github.andrepg.flatpak.runs.configuration.RunConfigurationValidator] in
 * the style of the schema patches: every rule documents its purpose, collects
 * problems instead of throwing, and can be extended or dropped without touching
 * the others.
 */
interface ValidationRule {
    /** Stable identifier used in logs and fail-open error wrapping. */
    val id: String

    /**
     * Returns every problem found with the configuration; empty means valid.
     * Implementations must never throw and never mutate the filesystem.
     */
    fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String>
}

/** Resolves [path] against [basePath]; absolute paths pass through untouched. */
internal fun resolveAgainstBase(
    basePath: String?,
    path: String,
): File {
    val file = File(path)
    return if (file.isAbsolute || basePath.isNullOrBlank()) file else File(basePath, path)
}
