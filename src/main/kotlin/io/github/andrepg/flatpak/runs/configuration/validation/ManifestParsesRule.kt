package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.exception.FlatpakManifestException
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.utils.FlatpakManifestReader
import java.io.File

/**
 * The manifest must be parseable and carry an application identity
 * (`app-id`, falling back to `id`): flatpak-builder fails later with a much
 * less obvious error, and every plugin feature (run names, RUN command) keys
 * off this field.
 *
 * Silently skips when the file is missing — [ManifestExistsRule] already owns
 * that report, avoiding duplicate messages.
 */
class ManifestParsesRule(
    private val readAppId: (File) -> String? = ::parseAppIdStrict,
) : ValidationRule {
    override val id: String = "manifest-parses"

    override fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String> {
        if (config.manifestPath.isBlank()) return emptyList()
        val manifestFile = resolveAgainstBase(basePath, config.manifestPath)
        if (!manifestFile.exists() || manifestFile.isDirectory) return emptyList()
        return try {
            if (readAppId(manifestFile).isNullOrBlank()) {
                listOf("Manifest has neither 'app-id' nor 'id': ${config.manifestPath}")
            } else {
                emptyList()
            }
        } catch (_: FlatpakManifestException) {
            listOf("Manifest could not be parsed: ${config.manifestPath}")
        } catch (_: Exception) {
            listOf("Manifest could not be read: ${config.manifestPath}")
        }
    }

    private companion object {
        /** Strict parse: throws [FlatpakManifestException] on unparseable content. */
        fun parseAppIdStrict(file: File): String? =
            FlatpakManifestReader.pickAppId(
                FlatpakManifestReader.parseFields(file.readText(), file.name, "app-id", "id"),
            )
    }
}
