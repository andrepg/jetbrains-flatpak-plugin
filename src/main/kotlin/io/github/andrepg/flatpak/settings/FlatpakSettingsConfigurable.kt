package io.github.andrepg.flatpak.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class FlatpakSettingsConfigurable : SearchableConfigurable {
    
    private var flatpakBinaryPath: String = "/usr/bin/flatpak"
    private var flatpakBuilderBinaryPath: String = "/usr/bin/flatpak run org.flatpak.Builder"
    
    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "Flatpak"
    
    override fun getId(): String = "io.github.andrepg.flatpak.settings.FlatpakSettingsConfigurable"
    
    override fun createComponent(): JComponent {
        return panel {
            group("Flatpak Binaries") {
                row("Flatpak binary:") {
                    textField().bindText(::flatpakBinaryPath)
                }
                row("Flatpak-builder command:") {
                    textField().bindText(::flatpakBuilderBinaryPath)
                }
            }
        }
    }
    
    override fun isModified(): Boolean {
        return flatpakBinaryPath != "/usr/bin/flatpak" ||
               flatpakBuilderBinaryPath != "/usr/bin/flatpak run org.flatpak.Builder"
    }
    
    override fun apply() {
        // Apply settings
    }
    
    override fun reset() {
        flatpakBinaryPath = "/usr/bin/flatpak"
        flatpakBuilderBinaryPath = "/usr/bin/flatpak run org.flatpak.Builder"
    }
    
    override fun disposeUIResources() {
        // Cleanup if needed
    }
}