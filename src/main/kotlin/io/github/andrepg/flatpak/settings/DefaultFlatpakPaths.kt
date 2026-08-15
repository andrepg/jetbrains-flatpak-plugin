package io.github.andrepg.flatpak.settings

/**
 * Default paths used to locate the Flatpak executables.
 */
object DefaultFlatpakPaths {
    /** Path to the main `flatpak` CLI binary. */
    const val MAIN_BINARY = "/usr/bin/flatpak"

    /** Flatpak run ID of the Flatpak Builder application. */
    const val BUILDER_BINARY = "org.flatpak.Builder"
}
