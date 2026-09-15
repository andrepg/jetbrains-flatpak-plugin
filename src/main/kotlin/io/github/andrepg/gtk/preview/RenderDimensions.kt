package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.preview.ui.GtkPreviewContentPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel

/**
 * Seeds the headless renderer with the GTK Preview panel's current size.
 *
 * The renderer snapshot matches the tool window's content area at the moment
 * the render is scheduled so the preview fills the panel. Because the content
 * panel scrolls any overflow, a portrait or landscape dock side does not
 * matter — only the panel's current size.
 *
 * The content panel insets the image with [GtkPreviewContentPanel.CONTENT_PADDING]
 * on all sides, so the size handed to the renderer is reduced by twice that
 * padding: the exported PNG then exactly fills the visible area instead of
 * being cropped under the border.
 *
 * The panel size is read on the EDT at the scheduling site of the background
 * render task, and the resulting pixels are handed to the task, which never
 * touches Swing.
 */
internal object RenderDimensions {
    /** Fallback render size when the panel has not been laid out yet. */
    internal const val DEFAULT_WIDTH = 800
    internal const val DEFAULT_HEIGHT = 600

    /**
     * @param panel the preview panel whose current size seeds the render
     * @return render `(width, height)` in pixels; falls back to
     *   [DEFAULT_WIDTH] x [DEFAULT_HEIGHT] until the panel has a real size
     */
    internal fun renderSize(panel: GtkPreviewPanel): Pair<Int, Int> {
        val size = panel.panel().size
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return DEFAULT_WIDTH to DEFAULT_HEIGHT

        val inner = 2 * GtkPreviewContentPanel.CONTENT_PADDING
        return (width - inner).coerceAtLeast(1) to (height - inner).coerceAtLeast(1)
    }
}
