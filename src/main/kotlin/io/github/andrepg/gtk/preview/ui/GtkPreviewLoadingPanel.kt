package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.andrepg.shared.Localization
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JProgressBar

class GtkPreviewLoadingPanel {
    private val loadingMessage = Localization.message("gtk.preview.loading.title")

    private val loadingLabel = JBLabel(loadingMessage).apply { isAllowAutoWrapping = true }

    private val progressBar: JProgressBar = JProgressBar().apply { isIndeterminate = true }

    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(loadingLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.CENTER)
        }
}
