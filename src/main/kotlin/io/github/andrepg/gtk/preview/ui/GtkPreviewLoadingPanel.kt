package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.andrepg.shared.Localization
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JProgressBar

class GtkPreviewLoadingPanel {
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(
                JBLabel(Localization.message("gtk.preview.loading.title")),
                BorderLayout.NORTH,
            )
            add(
                JProgressBar().apply {
                    isIndeterminate = true
                },
                BorderLayout.CENTER,
            )
        }
}
