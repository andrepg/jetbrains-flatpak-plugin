package io.github.andrepg.gtk.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderDimensionsTest {
    @Test
    fun `portrait side renders taller than wide`() {
        val (width, height) = RenderDimensions.renderSize(portrait = true)
        assertEquals(RenderDimensions.PORTRAIT_WIDTH, width)
        assertEquals(RenderDimensions.PORTRAIT_HEIGHT, height)
        assertEquals(600, width)
        assertEquals(800, height)
    }

    @Test
    fun `landscape side renders wider than tall`() {
        val (width, height) = RenderDimensions.renderSize(portrait = false)
        assertEquals(RenderDimensions.LANDSCAPE_WIDTH, width)
        assertEquals(RenderDimensions.LANDSCAPE_HEIGHT, height)
        assertEquals(800, width)
        assertEquals(600, height)
    }
}