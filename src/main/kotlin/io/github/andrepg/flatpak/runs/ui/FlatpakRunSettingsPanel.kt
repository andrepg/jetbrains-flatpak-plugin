package io.github.andrepg.flatpak.runs.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.FeatureFlags
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.UiRows.browseTextFieldRow
import io.github.andrepg.shared.UiRows.comboBoxRow
import io.github.andrepg.shared.UiRows.expandableTextFieldRow
import io.github.andrepg.shared.UiRows.textFieldRow
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FlatpakRunSettingsPanel : SettingsEditor<FlatpakRunSettings>() {

    private lateinit var commandComboBox: ComboBox<UserVisibleCommand>
    private lateinit var manifestField: TextFieldWithBrowseButton
    private var cleanupGroupRow: Row? = null

    private var customArgumentsField: ExpandableTextField? = null
    private lateinit var buildDir: JBTextField

    private lateinit var forceCleanCheck: JBCheckBox
    private lateinit var deepCleanCheck: JBCheckBox
    private lateinit var portalsCheck: JBCheckBox
    private lateinit var themesCheck: JBCheckBox
    private lateinit var audioCheck: JBCheckBox
    private lateinit var waylandCheck: JBCheckBox

    private val textFields by lazy {
        listOfNotNull(
            customArgumentsField,
            buildDir,
        )
    }

    private val textWithButtons by lazy {
        listOf(
            manifestField
        )
    }

    private val checkboxes by lazy {
        listOf<JBCheckBox>(
            forceCleanCheck,
            deepCleanCheck,
            portalsCheck,
            themesCheck,
            audioCheck,
            waylandCheck,
        )
    }

    private val panel: DialogPanel = panel {
        val project = ProjectManager.getInstance().defaultProject

        group(Localization.message("runs.settings.group.command")) {
            commandComboBox = comboBoxRow(
                label = Localization.message("runs.settings.command.label"),
                comment = Localization.message("runs.settings.command.description"),
                items = UserVisibleCommand.entries,
            ).component
        }

        group(Localization.message("runs.settings.group.manifest")) {
            manifestField = browseTextFieldRow(
                label = Localization.message("runs.settings.manifest.label"),
                project = project,
                comment = Localization.message("runs.settings.manifest.description"),
                fileChosen = { chosenFile -> chosenFile.path },
            ).component
        }

        group(Localization.message("runs.settings.group.cleanup")) {
            row {
                forceCleanCheck = checkBox(Localization.message("runs.settings.force-clean.label"))
                    .comment(Localization.message("runs.settings.force-clean.description"))
                    .component
            }
            row {
                deepCleanCheck = checkBox(Localization.message("runs.settings.deep-clean.label"))
                    .comment(Localization.message("runs.settings.deep-clean.description"))
                    .component
            }
        }.visible(commandComboBox.selectedItem == UserVisibleCommand.BUILD)

        group(Localization.message("runs.settings.group.portals")) {
            row {
                portalsCheck = checkBox(Localization.message("runs.settings.portals.label"))
                    .comment(Localization.message("runs.settings.portals.description"))
                    .component
            }
            row {
                themesCheck = checkBox(Localization.message("runs.settings.themes.label"))
                    .comment(Localization.message("runs.settings.themes.description"))
                    .component
            }
            row {
                audioCheck = checkBox(Localization.message("runs.settings.audio.label"))
                    .comment(Localization.message("runs.settings.audio.description"))
                    .component
            }
            row {
                waylandCheck = checkBox(Localization.message("runs.settings.wayland.label"))
                    .comment(Localization.message("runs.settings.wayland.description"))
                    .component
            }
        }

        group(Localization.message("runs.settings.group.advanced")) {
            buildDir = textFieldRow(
                label = Localization.message("runs.settings.build-output.label"),
                comment = Localization.message("runs.settings.build-output.description"),
            ).component

            if (FeatureFlags.getBoolean(FeatureFlags.FEATURE_FLAG_SHOW_CUSTOM_ARGUMENTS)) {
                customArgumentsField = expandableTextFieldRow(
                    label = Localization.message("runs.settings.custom-arguments.label"),
                    comment = Localization.message("runs.settings.custom-arguments.description"),
                ).component
            }
        }
    }

    init {
        wireChangeListeners()
    }

    /**
     * Notifies the editor about every edit so the Apply button gets enabled as
     * soon as any field changes.
     */
    private fun wireChangeListeners() {
        commandComboBox.addActionListener {
            fireEditorStateChanged()
        }

        checkboxes.forEach { it.addChangeListener { fireEditorStateChanged() } }

        textFields
            .plus(textWithButtons.map { it.textField })
            .forEach { it.document.addDocumentListener(notifyingDocumentListener()) }
    }

    private fun notifyingDocumentListener() = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = fireEditorStateChanged()
        override fun removeUpdate(e: DocumentEvent) = fireEditorStateChanged()
        override fun changedUpdate(e: DocumentEvent) = fireEditorStateChanged()
    }

    override fun resetEditorFrom(configuration: FlatpakRunSettings) {
        // Build Run type used
        commandComboBox.item = configuration.command

        // Manifest and build dir output
        manifestField.text = configuration.manifestPath
        buildDir.text = configuration.buildDir

        // Custom arguments passed to Flatpak (only when the row is visible)
        customArgumentsField?.text = configuration.customArguments.joinToString("\n")

        // Build clean arguments
        forceCleanCheck.isSelected = configuration.enableForceClean
        deepCleanCheck.isSelected = configuration.enableDeepClean

        // Portals to use when run (opt-in default)
        portalsCheck.isSelected = configuration.enablePortals
        audioCheck.isSelected = configuration.enableAudio
        themesCheck.isSelected = configuration.enableThemes
        waylandCheck.isSelected = configuration.enableWayland
    }

    override fun applyEditorTo(configuration: FlatpakRunSettings) {
        configuration.command = commandComboBox.item ?: UserVisibleCommand.BUILD

        manifestField.text.also { configuration.manifestPath = it }
        buildDir.text.also { configuration.buildDir = it }

        // Custom Flatpak build arguments (kept as-is when the row is hidden)
        customArgumentsField?.let { field ->
            configuration.customArguments = field.text
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        // Build clean arguments
        forceCleanCheck.isSelected.also { configuration.enableForceClean = it }
        deepCleanCheck.isSelected.also { configuration.enableDeepClean = it }

        // Portals to use when run (opt-in default)
        portalsCheck.isSelected.also { configuration.enablePortals = it }
        audioCheck.isSelected.also { configuration.enableAudio = it }
        themesCheck.isSelected.also { configuration.enableThemes = it }
        waylandCheck.isSelected.also { configuration.enableWayland = it }
    }

    override fun createEditor(): JComponent = panel
}
