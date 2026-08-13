package io.github.andrepg.flatpak.runs.enums

/**
 * Enum representing different attributes of a Flatpak run configuration.
 *
 * @property COMMAND The Flatpak command to execute
 * @property MANIFEST The path to the Flatpak manifest file
 * @property BUILD_DIR The build directory path
 * @property CUSTOM_ARGS Custom arguments for the Flatpak command
 */
enum class FlatpakRunAttributes {
    COMMAND,
    MANIFEST,
    BUILD_DIR,
    CUSTOM_ARGS
}