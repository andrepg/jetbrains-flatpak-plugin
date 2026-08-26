package io.github.andrepg.gtk.preview.ui

import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Root panel for the GTK Preview tool window.
 *
 * Acts as a simple state machine that swaps between three child
 * panels depending on the current state:
 *
 * - **Loading** — shown while a render is in progress
 * - **Error** — shown when the render fails or no `.ui` file is active
 * - **Content** — displays the rendered preview image
 *
 * The render lifecycle is driven externally by
 * [io.github.andrepg.gtk.preview.actions.GtkPreviewRenderAction]
 * which calls [setLoading], [setFailed], sets [renderedImage], and
 * finally calls [refresh] to swap the visible panel.
 *
 * @property renderedImage path to the most recently rendered PNG, or
 *   `null` when no render has succeeded yet
 * @property errorMessage human-readable description shown in the error
 *   panel when [hasFailed] is `true`
 */
class GtkPreviewPanel {
    private var hasFailed: Boolean = false
    private var isLoading: Boolean = false

    var renderedImage: Path? = null
    var errorMessage: String? = null

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
                hasFailed -> GtkPreviewErrorPanel(errorMessage).panel()
                else -> GtkPreviewContentPanel(renderedImage).panel()
            },
            BorderLayout.CENTER,
        )
        root.revalidate()
        root.repaint()
    }

    fun panel(): JPanel = root
}
