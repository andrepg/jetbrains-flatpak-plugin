package io.github.andrepg.flatpak.settings.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.flatpak.settings.FlatpakGlobalSettingsState
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.UiRows.browseTextFieldRow
import io.github.andrepg.shared.UiRows.textFieldRow
import io.github.andrepg.shared.diagnostics.DiagnosticsInitializer
import io.github.andrepg.shared.diagnostics.DiagnosticsSettingsState
import javax.swing.JComponent

/**
 * IDE-level settings page for the plugin: Flatpak binary paths and diagnostics.
 *
 * Backed by the persisted [FlatpakGlobalSettingsState] and
 * [DiagnosticsSettingsState] application services: [reset] loads the current
 * values, [apply] writes them back. Applying the Diagnostics group reconfigures
 * the runtime (debug logging level + Sentry client) immediately, no IDE
 * restart needed.
 */
class DevToolsSettingsConfigurable : Configurable {
    private val settings = service<FlatpakGlobalSettingsState>()
    private val diagnostics = service<DiagnosticsSettingsState>()

    private lateinit var flatpakField: TextFieldWithBrowseButton
    private lateinit var builderField: JBTextField
    private lateinit var sentryCheck: JBCheckBox
    private lateinit var debugCheck: JBCheckBox

    override fun getDisplayName(): String = Localization.message("settings.devtools.title")

    override fun createComponent(): JComponent =
        panel {
            group(Localization.message("settings.devtools.binaries.title")) {
                flatpakField =
                    browseTextFieldRow(
                        label = Localization.message("settings.devtools.binaries.flatpak.label"),
                        project = ProjectManager.getInstance().defaultProject,
                        comment = Localization.message("settings.devtools.binaries.flatpak.description"),
                        fileChosen = { chosenFile -> chosenFile.path },
                    ).component

                builderField =
                    textFieldRow(
                        label = Localization.message("settings.devtools.binaries.flatpak-builder.label"),
                        comment = Localization.message("settings.devtools.binaries.flatpak-builder.description"),
                    ).component
            }

            group(Localization.message("settings.devtools.diagnostics.title")) {
                row {
                    sentryCheck =
                        checkBox(Localization.message("settings.devtools.diagnostics.sentry.label"))
                            .comment(Localization.message("settings.devtools.diagnostics.sentry.description"))
                            .component
                }
                row {
                    debugCheck =
                        checkBox(Localization.message("settings.devtools.diagnostics.debug.label"))
                            .comment(Localization.message("settings.devtools.diagnostics.debug.description"))
                            .component
                }
            }
        }

    override fun isModified(): Boolean =
        flatpakField.text.orEmpty() != settings.flatpakBinaryPath.orEmpty() ||
            builderField.text.orEmpty() != settings.flatpakBuilderBinaryPath.orEmpty() ||
            sentryCheck.isSelected != diagnostics.sentryEnabled ||
            debugCheck.isSelected != diagnostics.debugLoggingEnabled

    override fun apply() {
        settings.flatpakBinaryPath = flatpakField.text.orEmpty()
        settings.flatpakBuilderBinaryPath = builderField.text.orEmpty()

        val sentryChanged = sentryCheck.isSelected != diagnostics.sentryEnabled
        val debugChanged = debugCheck.isSelected != diagnostics.debugLoggingEnabled
        diagnostics.sentryEnabled = sentryCheck.isSelected
        diagnostics.debugLoggingEnabled = debugCheck.isSelected

        if (sentryChanged || debugChanged) {
            // Reconfigure without blocking the Settings dialog on Sentry's async startup.
            ApplicationManager.getApplication().executeOnPooledThread {
                DiagnosticsInitializer().applyRuntimeConfiguration()
            }
        }
    }

    override fun reset() {
        flatpakField.text = settings.flatpakBinaryPath.orEmpty()
        builderField.text = settings.flatpakBuilderBinaryPath.orEmpty()
        sentryCheck.isSelected = diagnostics.sentryEnabled
        debugCheck.isSelected = diagnostics.debugLoggingEnabled
    }

    override fun disposeUIResources() {
        // No-op
    }
}
