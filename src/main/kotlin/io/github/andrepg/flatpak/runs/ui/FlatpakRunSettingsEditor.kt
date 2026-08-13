package io.github.andrepg.flatpak.runs.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import io.github.andrepg.flatpak.runs.FlatpakCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunConfiguration
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings editor for the Flatpak run configuration.
 *
 * Renders a form with the Flatpak command selector, the manifest path and custom
 * command-line arguments, bound to a [FlatpakRunConfiguration].
 */
class FlatpakRunSettingsEditor : SettingsEditor<FlatpakRunConfiguration>() {
    private val panel: JPanel = JPanel(GridBagLayout())
    private val commandComboBox = JComboBox(FlatpakCommand.values())
    private val manifestField = JBTextField()
    private val customArgumentsField = JBTextField()

    /**
     * @return the Swing component rendering the editor form
     */
    override fun createEditor(): JComponent = panel

    /**
     * Populates the form fields from the given configuration.
     *
     * @param config the configuration whose current values are displayed
     */
    override fun resetEditorFrom(config: FlatpakRunConfiguration) {
        commandComboBox.selectedItem = config.command
        manifestField.text = config.manifestPath
        customArgumentsField.text = config.customArguments.joinToString(" ")
    }

    /**
     * Applies the current form values to the given configuration.
     *
     * @param config the configuration updated from the form
     */
    override fun applyEditorTo(config: FlatpakRunConfiguration) {
        config.command = commandComboBox.selectedItem as FlatpakCommand
        config.manifestPath = manifestField.text
        config.customArguments = customArgumentsField.text.split(" ").filter { it.isNotBlank() }
    }

    init {
        val gbc = createGridBagConstraints()

        // Command selection
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Command:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(commandComboBox, gbc)

        // Manifest field
        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Manifest:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(manifestField, gbc)

        // Custom arguments field
        gbc.gridy = 3
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Custom arguments:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(customArgumentsField, gbc)
    }

    private fun createGridBagConstraints(): GridBagConstraints {
        val constraints = GridBagConstraints()
        constraints.anchor = GridBagConstraints.WEST
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(2, 2, 2, 2)
        constraints.weightx = 0.0
        return constraints
    }
}
