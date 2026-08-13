package io.github.andrepg.flatpak.runs.enums

/**
 * Enum representing different Flatpak commands that can be executed.
 *
 * @property BUILD Builds the Flatpak application
 * @property CLEAN Cleans the build directory
 * @property EXPORT Exports the Flatpak application to a bundle
 * @property RUN Runs the Flatpak application
 * @property VALIDATE Validates the Flatpak manifest
 * @property CUSTOM Executes a custom Flatpak command with user-provided arguments
 */
enum class FlatpakCommand {
    BUILD, CLEAN, EXPORT, RUN, VALIDATE, CUSTOM
}