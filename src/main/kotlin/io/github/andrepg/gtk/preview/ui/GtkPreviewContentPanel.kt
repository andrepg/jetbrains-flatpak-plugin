package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel

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
    fun panel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
            val component: JComponent =
                imagePath
                    ?.let { loadPreview(it) }
                    ?.let { JBLabel(ImageIcon(it)) }
                    ?: JBLabel()
            add(
                ScrollPaneFactory.createScrollPane(component, true),
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
