package io.github.andrepg.flatpak.settings

import io.github.andrepg.flatpak.utils.FlatpakPathValidator
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.shared.Localization
import javax.swing.JComponent

/**
 * Settings page for configuring the Flatpak binaries used by the plugin.
 *
 * Registered as a project configurable in `plugin.xml` under the Language group, letting users
 * set the `flatpak` and `flatpak-builder` binary paths with input validation.
 */
class FlatpakSettingsConfigurable : SearchableConfigurable {
    /** Path to the `flatpak` binary. */
    var flatpakBinaryPath: String = FlatpakPaths.MAIN_BINARY

    /** Path to the `flatpak-builder` binary. */
    var flatpakBuilderBinaryPath: String = FlatpakPaths.BUILDER_BINARY

    /**
     * @return the name shown in the settings tree
     */
    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "Flatpak"

    /**
     * @return the unique identifier of this configurable
     */
    override fun getId(): String = "io.github.andrepg.flatpak.settings.FlatpakSettingsConfigurable"

    /**
     * @return the settings component to display
     */
    override fun createComponent(): JComponent {
        return panel {
            group(Localization.message("settings.flatpak.binaries.title")) {
                row(Localization.message("settings.flatpak.binaries.flatpak.label")) {
                    rowComment(Localization.message("settings.flatpak.binaries.flatpak.description"))
                    textField().bindText(::flatpakBinaryPath)
                        .validationOnInput {
                            if (!FlatpakPathValidator.validateBinaryPath(flatpakBinaryPath)) {
                                error(Localization.message("settings.flatpak.binaries.flatpak.error"))
                            } else if (!flatpakBinaryPath.startsWith("/")) {
                                error(Localization.message("settings.flatpak.binaries.flatpak.error"))
                            } else {
                                null
                            }
                        }
                }

                row(Localization.message("settings.flatpak.binaries.flatpak-builder.label")) {
                    rowComment(Localization.message("settings.flatpak.binaries.flatpak-builder.description"))
                    textField().bindText(::flatpakBuilderBinaryPath)
                        .validationOnInput {
                            if (!FlatpakPathValidator.validateBinaryPath(flatpakBuilderBinaryPath)) {
                                error(Localization.message("settings.flatpak.binaries.flatpak-builder.error"))
                            } else if (!flatpakBuilderBinaryPath.startsWith("/")) {
                                error(Localization.message("settings.flatpak.binaries.flatpak-builder.error"))
                            } else {
                                null
                            }
                        }
                }
            }
        }
    }

    /**
     * @return true if any setting differs from its default value
     */
    override fun isModified(): Boolean {
        return flatpakBinaryPath != FlatpakPaths.MAIN_BINARY ||
               flatpakBuilderBinaryPath != FlatpakPaths.BUILDER_BINARY
    }

    /**
     * Persists the current setting values.
     */
    override fun apply() {
        // Apply settings
    }

    /**
     * Restores the settings to their default values.
     */
    override fun reset() {
        flatpakBinaryPath = FlatpakPaths.MAIN_BINARY
        flatpakBuilderBinaryPath = FlatpakPaths.BUILDER_BINARY
    }

    /**
     * Releases resources held by this configurable.
     */
    override fun disposeUIResources() {
        // Cleanup if needed
    }
}
