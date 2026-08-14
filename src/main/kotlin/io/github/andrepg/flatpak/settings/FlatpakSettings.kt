package io.github.andrepg.flatpak.settings

import com.intellij.openapi.components.service

/**
 * Live accessor for the configured Flatpak binaries.
 *
 * Reads the persisted [FlatpakGlobalSettingsState] application service, falling
 * back to the [DefaultFlatpakPaths] when the user has not customized anything.
 * Getters evaluate on every access so Settings changes apply to the next run.
 */
object FlatpakSettings {
    /** The configured `flatpak` CLI binary. */
    val flatpakBinary: String
        get() = service<FlatpakGlobalSettingsState>().flatpakBinaryPath ?: DefaultFlatpakPaths.MAIN_BINARY

    /** The configured flatpak-builder invocation (binary or flatpak run id). */
    val builderBinary: String
        get() = service<FlatpakGlobalSettingsState>().flatpakBuilderBinaryPath ?: DefaultFlatpakPaths.BUILDER_BINARY
}
