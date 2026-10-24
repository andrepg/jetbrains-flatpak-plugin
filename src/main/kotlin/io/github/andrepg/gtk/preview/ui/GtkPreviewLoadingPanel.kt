package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.andrepg.shared.Localization
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

class GtkPreviewLoadingPanel {
    private val loadingLabel =
        JBLabel(
            Localization.message("gtk.preview.loading.title"),
            com.intellij.ui.AnimatedIcon.Default(),
            SwingConstants.CENTER,
        ).apply {
            isAllowAutoWrapping = true
        }

    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(loadingLabel, BorderLayout.CENTER)
        }
}
