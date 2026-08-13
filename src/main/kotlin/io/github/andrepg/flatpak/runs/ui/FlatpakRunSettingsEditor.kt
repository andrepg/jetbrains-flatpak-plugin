package io.github.andrepg.flatpak.runs.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.flatpak.runs.FlatpakCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunConfiguration
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.UiRows.browseTextFieldRow
import io.github.andrepg.shared.UiRows.comboBoxRow
import io.github.andrepg.shared.UiRows.textFieldRow
import javax.swing.JComponent

/**
 * Settings editor for the Flatpak run configuration.
 *
 * Renders a form with the Flatpak command selector, the manifest path and custom
 * command-line arguments, bound to a [FlatpakRunConfiguration].
 */
class FlatpakRunSettingsEditor : SettingsEditor<FlatpakRunConfiguration>() {
    private var command: FlatpakCommand = FlatpakCommand.BUILD
    private var manifestPath: String = ""
    private var customArguments: String = ""

    /**
     * @return the Swing component rendering the editor form
     */
    override fun createEditor(): JComponent = panel {
        comboBoxRow(
            label = Localization.message("runs.settings.command.label"),
            comment = Localization.message("runs.settings.command.description"),
            items = FlatpakCommand.entries,
        )
            .bindItem({ command }, { command = it ?: FlatpakCommand.BUILD })

        browseTextFieldRow(
            label = Localization.message("runs.settings.manifest.label"),
            project = ProjectManager.getInstance().defaultProject,
            comment = Localization.message("runs.settings.manifest.description"),
            fileChosen = { chosenFile -> chosenFile.path },
        )
            .bindText(::manifestPath)

        textFieldRow(
            label = Localization.message("runs.settings.custom-arguments.label"),
            comment = Localization.message("runs.settings.custom-arguments.description"),
        )
            .bindText(::customArguments)
    }

    /**
     * Populates the form fields from the given configuration.
     *
     * @param config the configuration whose current values are displayed
     */
    override fun resetEditorFrom(config: FlatpakRunConfiguration) {
        command = config.command
        manifestPath = config.manifestPath
        customArguments = config.customArguments.joinToString(" ")
    }

    /**
     * Applies the current form values to the given configuration.
     *
     * @param config the configuration updated from the form
     */
    override fun applyEditorTo(config: FlatpakRunConfiguration) {
        config.command = command
        config.manifestPath = manifestPath
        config.customArguments = customArguments.split(" ").filter { it.isNotBlank() }
    }
}
