package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.preview.ui.GtkPreviewContentPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Dimension

class RenderDimensionsTest {
    @Test
    fun `uses the panel's current size minus the content padding`() {
        val panel = GtkPreviewPanel()
        panel.panel().size = Dimension(333, 222)

        val inner = 2 * GtkPreviewContentPanel.CONTENT_PADDING
        val (width, height) = RenderDimensions.renderSize(panel)
        assertEquals(333 - inner, width)
        assertEquals(222 - inner, height)
    }

    @Test
    fun `falls back to the default size when the panel is not laid out`() {
        val panel = GtkPreviewPanel()

        val (width, height) = RenderDimensions.renderSize(panel)
        assertEquals(RenderDimensions.DEFAULT_WIDTH, width)
        assertEquals(RenderDimensions.DEFAULT_HEIGHT, height)
    }

    @Test
    fun `clamps to at least one pixel when the panel is smaller than the padding`() {
        val panel = GtkPreviewPanel()
        panel.panel().size = Dimension(10, 10)

        val (width, height) = RenderDimensions.renderSize(panel)
        assertEquals(1, width)
        assertEquals(1, height)
    }
}
