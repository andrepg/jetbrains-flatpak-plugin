package io.github.andrepg.flatpak.settings.ui

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import javax.swing.JComponent

/**
 * Settings page for configuring the Flatpak binaries used by the plugin.
 *
 * Registered as a project configurable in `plugin.xml` under the Language group, letting users
 * set the `flatpak` and `flatpak-builder` binary paths with input validation. The UI and its
 * state live in [FlatpakSettingsPanel]; this configurable only delegates the lifecycle calls.
 */
class FlatpakSettingsConfigurable : SearchableConfigurable {

    private val settingsPanel = FlatpakSettingsPanel()

    /**
     * @return the name shown in the settings tree
     */
    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "Flatpak"

    /**
     * @return the unique identifier of this configurable
     */
    override fun getId(): String = "io.github.andrepg.flatpak.settings.ui.FlatpakSettingsConfigurable"

    /**
     * @return the settings component to display
     */
    override fun createComponent(): JComponent = settingsPanel.component

    /**
     * @return true if any setting differs from its default value
     */
    override fun isModified(): Boolean = settingsPanel.isModified()

    /**
     * Persists the current setting values.
     */
    override fun apply() {
        // Values are already bound to the panel fields; nothing further to persist.
    }

    /**
     * Restores the settings to their default values.
     */
    override fun reset() = settingsPanel.reset()

    /**
     * Releases resources held by this configurable.
     */
    override fun disposeUIResources() {
        // Cleanup if needed
    }
}
