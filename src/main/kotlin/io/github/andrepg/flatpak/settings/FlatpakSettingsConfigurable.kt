package io.github.andrepg.flatpak.settings

import io.github.andrepg.flatpak.utils.FlatpakPathValidator
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.shared.Localization
import javax.swing.JComponent

class FlatpakSettingsConfigurable : SearchableConfigurable {
    var flatpakBinaryPath: String = FlatpakPaths.MAIN_BINARY
    var flatpakBuilderBinaryPath: String = FlatpakPaths.BUILDER_BINARY
    
    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "Flatpak"
    
    override fun getId(): String = "io.github.andrepg.flatpak.settings.FlatpakSettingsConfigurable"
    
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
    
    override fun isModified(): Boolean {
        return flatpakBinaryPath != FlatpakPaths.MAIN_BINARY ||
               flatpakBuilderBinaryPath != FlatpakPaths.BUILDER_BINARY
    }
    
    override fun apply() {
        // Apply settings
    }
    
    override fun reset() {
        flatpakBinaryPath = FlatpakPaths.MAIN_BINARY
        flatpakBuilderBinaryPath = FlatpakPaths.BUILDER_BINARY
    }
    
    override fun disposeUIResources() {
        // Cleanup if needed
    }
}