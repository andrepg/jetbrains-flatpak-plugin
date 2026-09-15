package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Dimension

class RenderDimensionsTest {
    @Test
    fun `uses the panel's current size`() {
        val panel = GtkPreviewPanel()
        panel.panel().size = Dimension(333, 222)

        val (width, height) = RenderDimensions.renderSize(panel)
        assertEquals(333, width)
        assertEquals(222, height)
    }

    @Test
    fun `falls back to the default size when the panel is not laid out`() {
        val panel = GtkPreviewPanel()

        val (width, height) = RenderDimensions.renderSize(panel)
        assertEquals(RenderDimensions.DEFAULT_WIDTH, width)
        assertEquals(RenderDimensions.DEFAULT_HEIGHT, height)
    }
}
