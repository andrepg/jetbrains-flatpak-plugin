package io.github.andrepg.gtk.preview.ui

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class GtkPreviewPanel {
    private var hasFailed: Boolean = false
    private var isLoading: Boolean = false

    private val root: JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
        }

    init {
        refresh()
    }

    fun setFailed(failed: Boolean) {
        hasFailed = failed
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
    }

    fun refresh() {
        root.removeAll()
        root.add(
            when {
                isLoading -> GtkPreviewLoadingPanel().panel()
                hasFailed -> GtkPreviewErrorPanel().panel()
                else -> GtkPreviewContentPanel().panel()
            },
            BorderLayout.CENTER,
        )
        root.revalidate()
        root.repaint()
    }

    fun panel(): JPanel = root
}
