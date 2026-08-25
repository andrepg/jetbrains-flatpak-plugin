package io.github.andrepg.gtk.preview.ui

import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.ui.EmptyStatePanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class GtkPreviewErrorPanel {
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
            add(
                EmptyStatePanel(
                    title = Localization.message("gtk.preview.fail.title"),
                    description = Localization.message("gtk.preview.fail.description"),
                    actionText = "",
                    onAction = { },
                ).panel(),
                BorderLayout.CENTER,
            )
        }
}
