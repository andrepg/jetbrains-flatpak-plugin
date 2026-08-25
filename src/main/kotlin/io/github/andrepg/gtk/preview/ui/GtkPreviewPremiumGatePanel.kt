package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A simple Empty State component, with title, message and a single action.
 * Originally built to gate out the GTK Preview, it can be made generic in
 * the future and renamed to a simple EmptyStatePanel later.
 */
class GtkPreviewPremiumGatePanel {
    val messageTitle = Localization.message("gtk.preview.locked.title")
    val messageDescription = Localization.message("gtk.preview.locked.message")
    val messageCta = Localization.message("gtk.preview.locked.action")

    val labelTitle = JBLabel(messageTitle)
    val labelMessage = JBLabel(messageDescription).apply { isAllowAutoWrapping = true }

    val fontBold = JBFont.regular().asBold()

    fun getPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            val emptyStatePanel =
                panel {
                    group {
                        row {
                            cell(labelTitle.withFont(fontBold)).align(AlignX.CENTER)
                        }
                        row {
                            cell(labelMessage).align(AlignX.CENTER)
                        }
                        row {
                            link(messageCta) {
                                PremiumFeatureGate.requestAccess(messageTitle)
                            }.align(AlignX.CENTER)
                        }
                    }
                }

            add(
                Box(BoxLayout.Y_AXIS).apply {
                    add(Box.createVerticalGlue())
                    add(emptyStatePanel)
                    add(Box.createVerticalGlue())
                },
                BorderLayout.CENTER,
            )
        }
}
