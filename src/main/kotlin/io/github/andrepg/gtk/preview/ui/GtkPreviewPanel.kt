package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class GtkPreviewPanel {
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
            add(JBLabel("Preview"), BorderLayout.CENTER)
        }
}
