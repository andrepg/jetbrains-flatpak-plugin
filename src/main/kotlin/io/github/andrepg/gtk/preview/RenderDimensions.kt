package io.github.andrepg.gtk.preview

/**
 * Maps the GTK Preview tool window's docked side to a default render
 * size for the headless renderer.
 *
 * A tool window docked on the left/right edge of the IDE is tall (portrait)
 * while one docked on the top/bottom edge is wide (landscape). The renderer
 * snapshot matches that orientation so the first dimension follows the
 * panel's dominant side; the content panel scrolls any overflow.
 */
internal object RenderDimensions {
    internal const val PORTRAIT_WIDTH = 600
    internal const val PORTRAIT_HEIGHT = 800

    internal const val LANDSCAPE_WIDTH = 800
    internal const val LANDSCAPE_HEIGHT = 600

    /**
     * @param portrait `true` when the panel occupies a vertical strip of the
     *   IDE (docked left/right), `false` when it occupies a horizontal strip
     *   (docked top/bottom).
     * @return render `(width, height)` in pixels.
     */
    internal fun renderSize(portrait: Boolean): Pair<Int, Int> =
        if (portrait) {
            PORTRAIT_WIDTH to PORTRAIT_HEIGHT
        } else {
            LANDSCAPE_WIDTH to LANDSCAPE_HEIGHT
        }
}