package io.github.andrepg.shared.ui

import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A centered empty-state panel with three rows: **title**, **description**, and an
 * inline action row `[ action link | shortcut ]`.
 *
 * Follows the IntelliJ Platform empty-state guidelines:
 * - Bold title, auto-wrapping description, a single call-to-action link.
 * - Optional trailing shortcut label on the action row.
 *
 * @param title       bold heading
 * @param description wrapped body text
 * @param actionText  clickable action label (no trailing period)
 * @param onAction    callback fired when the action link is clicked
 * @param shortcut    optional shortcut text displayed to the right of the action link
 */
class EmptyStatePanel(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    shortcut: String? = null,
) {
    /**
     * Label based on `EmptyStatePanel(title)` parameter
     */
    private val labelTitle =
        JBLabel(title).apply {
            font = JBFont.regular().asBold()
            isAllowAutoWrapping = true
            alignmentX = Component.CENTER_ALIGNMENT
        }

    /**
     * Label based on `EmptyStatePanel(description)` parameter
     */
    private val labelDescription =
        JBLabel(description).apply {
            isAllowAutoWrapping = true
            alignmentX = Component.CENTER_ALIGNMENT
        }

    /**
     * Label based on `EmptyStatePanel(shortcut)` parameter
     */
    private val labelShortcut =
        JLabel(shortcut).apply {
            font = font.deriveFont((font.size - 1).toFloat())
        }

    /**
     * Panel containing both the action and related shortcut
     * provided when building the Empty State entity
     */
    private val actionPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.CENTER_ALIGNMENT

            add(ActionLink(actionText, ActionListener { onAction() }))

            if (!shortcut.isNullOrEmpty()) {
                add(Box.createHorizontalStrut(8))
                add(labelShortcut)
            }
        }

    /**
     * Root panel containing label, description and actions
     * given by constructor, allowing to embed in a main panel
     */
    private val root: JPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(labelTitle)
            add(Box.createVerticalStrut(8))
            add(labelDescription)
            add(Box.createVerticalStrut(12))
            add(actionPanel)
        }

    /** Returns the assembled [JPanel]. */
    fun panel(): JPanel =
        JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(root, GridBagConstraints().apply { anchor = GridBagConstraints.CENTER })
        }
}
