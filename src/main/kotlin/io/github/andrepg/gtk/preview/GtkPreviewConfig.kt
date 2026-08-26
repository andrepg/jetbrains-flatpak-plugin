package io.github.andrepg.gtk.preview

import java.io.File

/**
 * Shared constants for the GTK Preview feature.
 */
internal object GtkPreviewConfig {
    /** Plugin config subdirectory used to cache the compiled renderer and preview PNGs. */
    val configDir: File =
        com.intellij.openapi.application.PathManager
            .getConfigDir()
            .resolve("flatpak-preview")
            .toFile()
            .also { it.mkdirs() }
}
