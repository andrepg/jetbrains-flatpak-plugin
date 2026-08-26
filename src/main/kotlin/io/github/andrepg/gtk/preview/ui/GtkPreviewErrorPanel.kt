package io.github.andrepg.gtk.preview.ui

import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.ui.EmptyStatePanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Displays an error state in the GTK Preview tool window.
 *
 * Uses [EmptyStatePanel] to show a bold title and a description
 * explaining what went wrong.  When [message] is non-null it
 * overrides the default localized description so the caller can
 * provide a specific reason (e.g. "No .ui file open").
 *
 * @property message optional human-readable error description;
 *   defaults to the localized `gtk.preview.fail.description` key
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
                    onAction = { },
                ).panel(),
                BorderLayout.CENTER,
            )
        }
}
