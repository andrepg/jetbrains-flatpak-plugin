package io.github.andrepg.flatpak.settings.ui

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.flatpak.settings.DefaultFlatpakPaths
import io.github.andrepg.flatpak.utils.FlatpakPathValidator
import io.github.andrepg.shared.Localization

/**
 * The [FlatpakSettingsPanel] builds the entire Settings window under
 * Settings | Language & Frameworks.
 *
 * Owns the system-wide configuration fields (Flatpak binaries) and their validation;
 * [FlatpakSettingsConfigurable] delegates display and the Apply/Reset lifecycle to this panel.
 */
class FlatpakSettingsPanel {

    /** Path to the `flatpak` binary. */
    var flatpakBinaryPath: String = DefaultFlatpakPaths.MAIN_BINARY

    /** Path to the `flatpak-builder` binary. */
    var flatpakBuilderBinaryPath: String = DefaultFlatpakPaths.BUILDER_BINARY

    /** The settings component to display. */
    val component: DialogPanel = panel {
        group(Localization.message("settings.flatpak.binaries.title")) {
            row(Localization.message("settings.flatpak.binaries.flatpak.label")) {
                rowComment(Localization.message("settings.flatpak.binaries.flatpak.description"))
                textField().bindText(::flatpakBinaryPath)
                    .validationOnInput {
                        when {
                            !FlatpakPathValidator.validateBinaryPath(flatpakBinaryPath) ->
                                error(Localization.message("settings.flatpak.binaries.flatpak.error"))
                            !flatpakBinaryPath.startsWith("/") ->
                                error(Localization.message("settings.flatpak.binaries.flatpak.error"))
                            else -> null
                        }
                    }
            }

            row(Localization.message("settings.flatpak.binaries.flatpak-builder.label")) {
                rowComment(Localization.message("settings.flatpak.binaries.flatpak-builder.description"))
                textField().bindText(::flatpakBuilderBinaryPath)
                    .validationOnInput {
                        when {
                            !FlatpakPathValidator.validateBinaryPath(flatpakBuilderBinaryPath) ->
                                error(Localization.message("settings.flatpak.binaries.flatpak-builder.error"))
                            !flatpakBuilderBinaryPath.startsWith("/") ->
                                error(Localization.message("settings.flatpak.binaries.flatpak-builder.error"))
                            else -> null
                        }
                    }
            }
        }
    }

    /**
     * @return true if any setting differs from its default value
     */
    fun isModified(): Boolean =
        flatpakBinaryPath != DefaultFlatpakPaths.MAIN_BINARY ||
            flatpakBuilderBinaryPath != DefaultFlatpakPaths.BUILDER_BINARY

    /**
     * Restores the settings to their default values.
     */
    fun reset() {
        flatpakBinaryPath = DefaultFlatpakPaths.MAIN_BINARY
        flatpakBuilderBinaryPath = DefaultFlatpakPaths.BUILDER_BINARY
    }
}
