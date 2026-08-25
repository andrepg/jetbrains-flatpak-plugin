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
    private val root: JPanel =
        JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)

                    add(
                        JBLabel(title).apply {
                            font = JBFont.regular().asBold()
                            alignmentX = Component.CENTER_ALIGNMENT
                        },
                    )
                    add(Box.createVerticalStrut(8))

                    add(
                        JBLabel(description).apply {
                            isAllowAutoWrapping = true
                            alignmentX = Component.CENTER_ALIGNMENT
                        },
                    )
                    add(Box.createVerticalStrut(12))

                    add(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            alignmentX = Component.CENTER_ALIGNMENT
                            add(ActionLink(actionText, ActionListener { onAction() }))
                            if (shortcut != null) {
                                add(Box.createHorizontalStrut(8))
                                add(
                                    JBLabel(shortcut).apply {
                                        font = font.deriveFont((font.size - 1).toFloat())
                                    },
                                )
                            }
                        },
                    )
                },
                GridBagConstraints().apply {
                    anchor = GridBagConstraints.CENTER
                },
            )
        }

    /** Returns the assembled [JPanel]. */
    fun panel(): JPanel = root
}
