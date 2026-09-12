package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.settings.FlatpakSettings
import java.io.File

/**
 * The configured `flatpak` CLI must be locatable: an absolute configured path
 * has to exist and be executable; a bare name is searched along `PATH`.
 *
 * Filesystem checks only — the validator never spawns processes (it can run on
 * the EDT), so whether the Builder app is installed inside the flatpak
 * installation stays out of scope.
 */
class FlatpakFoundRule(
    private val binaryPath: () -> String = { FlatpakSettings.flatpakBinary },
    private val locate: (String) -> File? = ::locateExecutable,
) : ValidationRule {
    override val id: String = "flatpak-found"

    override fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String> {
        val raw = binaryPath().trim()
        if (raw.isEmpty()) {
            return listOf("Flatpak binary path cannot be empty")
        }
        return if (locate(raw) != null) {
            emptyList()
        } else {
            listOf("Flatpak CLI not found: '$raw'. Install flatpak or set its path in the plugin settings")
        }
    }

    private companion object {
        /**
         * Resolves [raw] to an executable file: absolute paths are taken as-is,
         * bare names are searched along `PATH`. Returns null when nothing
         * executable matches.
         */
        fun locateExecutable(raw: String): File? {
            val candidate = File(raw)
            if (candidate.isAbsolute) {
                return candidate.takeIf { it.isFile && it.canExecute() }
            }
            val pathDirs =
                System
                    .getenv("PATH")
                    ?.split(File.pathSeparator)
                    .orEmpty()
                    .filter { it.isNotBlank() }
            return pathDirs.asSequence().map { File(it, raw) }.firstOrNull { it.isFile && it.canExecute() }
        }
    }
}
