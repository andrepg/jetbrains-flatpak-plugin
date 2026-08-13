package io.github.andrepg.flatpak.runs.configuration

/**
 * Default settings values shared by Flatpak run configurations.
 */
data object FlatpakRunSettings {
    /** Default manifest file name used when none is configured. */
    const val DEFAULT_MANIFEST: String = "flatpak.json"

    /** Default output directory used by the Flatpak builder. */
    const val DEFAULT_OUTPUT: String = "_build"
}