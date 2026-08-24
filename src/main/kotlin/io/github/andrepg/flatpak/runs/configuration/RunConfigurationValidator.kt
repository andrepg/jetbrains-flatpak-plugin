package io.github.andrepg.flatpak.runs.configuration

import io.github.andrepg.flatpak.runs.configuration.validation.BuildDirValidRule
import io.github.andrepg.flatpak.runs.configuration.validation.CustomHasArgumentsRule
import io.github.andrepg.flatpak.runs.configuration.validation.FlatpakFoundRule
import io.github.andrepg.flatpak.runs.configuration.validation.ManifestExistsRule
import io.github.andrepg.flatpak.runs.configuration.validation.ManifestParsesRule
import io.github.andrepg.flatpak.runs.configuration.validation.ValidationRule

/**
 * Collects run-configuration problems without throwing and without mutating the
 * filesystem: no directories are created or removed here — flatpak-builder owns
 * build-directory creation at run time, so a missing build directory is valid;
 * only pre-existing paths are checked.
 *
 * Validation is a chain of named [ValidationRule]s (one file per rule, in the
 * `validation` subpackage), applied in order and collected into a single problem
 * list — the same reviewable-pipeline pattern as the GTK schema patches. A rule
 * that throws anyway is wrapped by the fail-open guard below and reported as a
 * single error instead of breaking the whole check.
 *
 * Pure JDK logic (no platform imports) so it is unit-testable and reusable from
 * the editor (Apply time) and from [FlatpakRunSettings.checkConfiguration].
 */
object RunConfigurationValidator {
    /** The production chain, in reporting order. */
    val defaultRules: List<ValidationRule> =
        listOf(
            ManifestExistsRule(),
            ManifestParsesRule(),
            BuildDirValidRule(),
            FlatpakFoundRule(),
            CustomHasArgumentsRule(),
        )

    /**
     * Validates [config] with the production chain and returns every problem
     * found; empty list means valid.
     *
     * Relative paths are resolved against [basePath] when provided; when it is
     * null or blank they fall back to the process working directory.
     */
    fun validate(
        config: FlatpakRunSettings,
        basePath: String? = null,
    ): List<String> = validate(config, basePath, defaultRules)

    /** Chain seam for tests: runs exactly [rules] against [config]. */
    internal fun validate(
        config: FlatpakRunSettings,
        basePath: String?,
        rules: List<ValidationRule>,
    ): List<String> =
        rules.flatMap { rule ->
            try {
                rule.check(config, basePath)
            } catch (e: Exception) {
                listOf("${rule.id}: validation failed unexpectedly (${e.message})")
            }
        }
}
