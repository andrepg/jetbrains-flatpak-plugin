package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Displays the rendered GTK preview image.
 *
 * The PNG is shown at its natural (1:1) size inside a two-axis scroll pane,
 * so after the tool window is resized smaller the scrollbars let the margins
 * stay reachable instead of cropping the preview.  When [imagePath] is
 * `null` an empty label is displayed (the parent state machine in
 * [GtkPreviewPanel] should not typically show this panel without an image).
 *
 * @property imagePath path to the rendered PNG file, or `null` if no
 *   render has been performed yet
 */
class GtkPreviewContentPanel(
    private val imagePath: Path?,
) {
    companion object {
        /**
         * Empty-border insets applied around the rendered image, in pixels.
         * The size handed to the renderer subtracts 2x this padding so the
         * PNG fills exactly the visible area instead of being cropped
         * (see [io.github.andrepg.gtk.preview.RenderDimensions]).
         */
        const val CONTENT_PADDING = 8
    }

    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border =
                BorderFactory.createEmptyBorder(
                    CONTENT_PADDING,
                    CONTENT_PADDING,
                    CONTENT_PADDING,
                    CONTENT_PADDING,
                )

            imagePath?.let {
                val path = loadPreview(it)
                val imagePanel = JBLabel(ImageIcon(path), SwingConstants.LEFT)
                add(imagePanel, BorderLayout.CENTER)
            }
        }

    private fun loadPreview(path: Path): BufferedImage? =
        try {
            ImageIO.read(path.toFile())
        } catch (e: Exception) {
            null
        }
}
