package io.github.andrepg.gtk.preview.ui

import com.intellij.ui.components.JBLabel
import io.github.andrepg.shared.license.PremiumFeatureGate
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Shown in the GTK Preview tool window when the user has no active
 * Marketplace license. Points to the free features and offers to obtain one.
 */
class UpgradePanel : JPanel(BorderLayout()) {

    init {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)

        val title = JBLabel("<html><b>GTK Preview requires a subscription</b></html>", SwingConstants.CENTER)
        val subtitle = JBLabel(
            "<html>Flatpak run configurations, manifest completion and .ui schema validation are free.<br>" +
                "Upgrade to unlock the live GTK Preview tool window.</html>",
            SwingConstants.CENTER
        )
        val upgradeButton = JButton("Get a license")
        upgradeButton.addActionListener {
            PremiumFeatureGate.requestAccess("GTK Preview requires a Flatpak DevTools subscription.")
        }

        listOf(title, subtitle, upgradeButton).forEach { it.alignmentX = Component.CENTER_ALIGNMENT }

        container.add(Box.createVerticalGlue())
        container.add(title)
        container.add(Box.createVerticalStrut(8))
        container.add(subtitle)
        container.add(Box.createVerticalStrut(12))
        container.add(upgradeButton)
        container.add(Box.createVerticalGlue())

        add(container, BorderLayout.CENTER)
    }
}
