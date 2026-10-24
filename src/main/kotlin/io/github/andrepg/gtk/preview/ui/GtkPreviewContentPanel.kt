package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBScrollPane
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
 * stay reachable instead of cropping the preview.
 */
class GtkPreviewContentPanel(
    private val imagePath: Path?,
) {
    companion object {
        /**
         * Empty-border insets applied around the rendered image, in pixels.
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

            imagePath?.let { path ->
                val image = loadPreview(path)
                if (image != null) {
                    val imageLabel = JBLabel(ImageIcon(image), SwingConstants.LEFT)
                    val scrollPane =
                        JBScrollPane(imageLabel).apply {
                            border = BorderFactory.createEmptyBorder()
                            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                        }
                    add(scrollPane, BorderLayout.CENTER)
                } else {
                    val empty = JBLabel()
                    add(empty, BorderLayout.CENTER)
                }
            }
        }

    private fun loadPreview(path: Path): BufferedImage? =
        try {
            ImageIO.read(path.toFile())
        } catch (_: Exception) {
            null
        }
}
