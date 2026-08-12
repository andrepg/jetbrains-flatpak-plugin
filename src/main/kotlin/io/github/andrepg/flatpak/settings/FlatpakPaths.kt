package io.github.andrepg.flatpak.settings

data object FlatpakPaths {
    const val MAIN_BINARY: String = "/usr/bin/flatpak"
    const val BUILDER_BINARY: String = "/usr/bin/flatpak run org.flatpak.Builder"
}