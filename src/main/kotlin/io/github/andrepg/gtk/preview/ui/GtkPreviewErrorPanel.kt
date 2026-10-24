package io.github.andrepg.gtk.preview.ui

import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.ui.EmptyStatePanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Displays an error state in the GTK Preview tool window.
 */
class GtkPreviewErrorPanel(
    private val message: String? = null,
) {
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
            add(
                EmptyStatePanel(
                    title = Localization.message("gtk.preview.fail.title"),
                    description = message ?: Localization.message("gtk.preview.fail.description"),
                    actionText = "",
                    onAction = {},
                ).panel(),
                BorderLayout.CENTER,
            )
        }
}
