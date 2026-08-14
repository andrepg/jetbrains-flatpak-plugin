package io.github.andrepg.flatpak.settings.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.flatpak.settings.FlatpakGlobalSettingsState
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.UiRows.browseTextFieldRow
import io.github.andrepg.shared.UiRows.textFieldRow
import javax.swing.JComponent

/**
 * Settings page for the Flatpak binary paths.
 *
 * Backed by the persisted [FlatpakGlobalSettingsState] application service:
 * [reset] loads the current values, [apply] writes them back.
 */
class FlatpakSettingsConfigurable : Configurable {

    private val settings = service<FlatpakGlobalSettingsState>()

    private lateinit var flatpakField: TextFieldWithBrowseButton
    private lateinit var builderField: JBTextField

    override fun getDisplayName(): String = "Flatpak"

    override fun createComponent(): JComponent = panel {
        group(Localization.message("settings.flatpak.binaries.title")) {
            flatpakField = browseTextFieldRow(
                label = Localization.message("settings.flatpak.binaries.flatpak.label"),
                project = ProjectManager.getInstance().defaultProject,
                comment = Localization.message("settings.flatpak.binaries.flatpak.description"),
                fileChosen = { chosenFile -> chosenFile.path },
            ).component

            builderField = textFieldRow(
                label = Localization.message("settings.flatpak.binaries.flatpak-builder.label"),
                comment = Localization.message("settings.flatpak.binaries.flatpak-builder.description"),
            ).component
        }
    }

    override fun isModified(): Boolean =
        flatpakField.text.orEmpty() != settings.flatpakBinaryPath.orEmpty() ||
            builderField.text.orEmpty() != settings.flatpakBuilderBinaryPath.orEmpty()

    override fun apply() {
        settings.flatpakBinaryPath = flatpakField.text.orEmpty()
        settings.flatpakBuilderBinaryPath = builderField.text.orEmpty()
    }

    override fun reset() {
        flatpakField.text = settings.flatpakBinaryPath.orEmpty()
        builderField.text = settings.flatpakBuilderBinaryPath.orEmpty()
    }

    override fun disposeUIResources() {
        // No-op
    }
}
