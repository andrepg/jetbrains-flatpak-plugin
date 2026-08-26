package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JPanel

/**
 * Displays the rendered GTK preview image.
 *
 * When [imagePath] is non-null the PNG is loaded and shown centered in
 * the panel.  When `null` an empty label is displayed (the parent state
 * machine in [GtkPreviewPanel] should not typically show this panel
 * without an image).
 *
 * @property imagePath path to the rendered PNG file, or `null` if no
 *   render has been performed yet
 */
class GtkPreviewContentPanel(
    private val imagePath: Path?,
) {
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
            val image = imagePath?.let { loadPreview(it) }
            add(
                if (image != null) {
                    JBLabel(ImageIcon(image))
                } else {
                    JBLabel()
                },
                BorderLayout.CENTER,
            )
        }

    private fun loadPreview(path: Path): BufferedImage? =
        try {
            ImageIO.read(path.toFile())
        } catch (e: Exception) {
            null
        }
}
