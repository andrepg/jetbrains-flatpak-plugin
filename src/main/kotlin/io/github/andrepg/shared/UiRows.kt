package io.github.andrepg.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout

/**
 * Shared JetBrains UI DSL building blocks used by the plugin's forms.
 *
 * Both the Settings pages ([FlatpakSettingsConfigurable]) and the run configuration editor
 * ([RunConfigurationSettingsPanel]) build their forms with these rows, keeping the
 * label + optional comment + input layout consistent across the plugin.
 */
object UiRows {

    /**
     * A label row with a plain text field.
     *
     * @param label the field label
     * @param comment an optional description rendered under the label
     * @return the text field cell, ready to be bound with `bindText`
     */
    fun Panel.textFieldRow(
        label: String,
        comment: String? = null,
    ): Cell<JBTextField> {
        val row = row(label) {}
        row.layout(RowLayout.LABEL_ALIGNED)
        if (comment != null) row.rowComment(comment)
        return row.textField()
    }

    /**
     * A label row with a text field and a browse button.
     *
     * @param label the field label
     * @param project the project used to open the file chooser
     * @param comment an optional description rendered under the label
     * @param fileChosen optional callback mapping the chosen file to the text written into the field
     * @return the text field cell, ready to be bound with `bindText`
     */
    fun Panel.browseTextFieldRow(
        label: String,
        project: Project,
        comment: String? = null,
        fileChosen: ((chosenFile: VirtualFile) -> String)? = null,
    ): Cell<TextFieldWithBrowseButton> {
        val row = row(label) {}
        row.layout(RowLayout.LABEL_ALIGNED)
        if (comment != null) row.rowComment(comment)
        return row.textFieldWithBrowseButton(project, fileChosen)
    }

    /**
     * A label row with a multiline-capable text field where each line is a separate value.
     *
     * @param label the field label
     * @param comment an optional description rendered under the label
     * @return the expandable text field cell
     */
    fun Panel.expandableTextFieldRow(
        label: String,
        comment: String? = null,
    ): Cell<ExpandableTextField> {
        val row = row(label) {}
        row.layout(RowLayout.LABEL_ALIGNED)
        if (comment != null) row.rowComment(comment)
        return row.expandableTextField(
            { text: String -> text.lines().map(String::trim).filter(String::isNotBlank).toMutableList() },
            { values: List<String> -> values.joinToString("\n") },
        )
    }

    /**
     * A label row with a combo box.
     *
     * @param label the combo box label
     * @param comment an optional description rendered under the label
     * @param items the selectable items
     * @return the combo box cell, ready to be bound with `bindItem`
     */
    fun <T> Panel.comboBoxRow(
        label: String,
        comment: String? = null,
        items: Collection<T>,
    ): Cell<ComboBox<T>> {
        val row = row(label) {}
        row.layout(RowLayout.LABEL_ALIGNED)
        if (comment != null) row.rowComment(comment)
        return row.comboBox(items)
    }
}
