package io.github.andrepg.flatpak.runs

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import io.github.andrepg.flatpak.enums.FlatpakCommand
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class FlatpakRunSettingsEditor : SettingsEditor<FlatpakRunConfiguration>() {
    private val panel: JPanel = JPanel(GridBagLayout())
    private val commandComboBox = JComboBox(FlatpakCommand.values())
    private val manifestField = JBTextField()
    private val buildDirField = JBTextField()

    override fun createEditor(): JComponent = panel

    override fun resetEditorFrom(config: FlatpakRunConfiguration) {
        commandComboBox.selectedItem = config.command
        manifestField.text = config.manifestPath
        buildDirField.text = config.buildDir
    }

    override fun applyEditorTo(config: FlatpakRunConfiguration) {
        config.command = commandComboBox.selectedItem as FlatpakCommand
        config.manifestPath = manifestField.text
        config.buildDir = buildDirField.text
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

        // Build directory field
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("Build directory:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(buildDirField, gbc)
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
