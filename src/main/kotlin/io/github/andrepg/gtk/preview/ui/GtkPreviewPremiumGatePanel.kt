package io.github.andrepg.gtk.preview.ui

import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import io.github.andrepg.shared.ui.EmptyStatePanel
import javax.swing.JPanel

/**
 * Empty-state panel shown when the GTK Preview feature is locked behind a
 * premium subscription. Delegates to the shared [EmptyStatePanel].
 */
class GtkPreviewPremiumGatePanel {
    fun panel(): JPanel {
        val gateTitle = Localization.message("gtk.preview.locked.title")

        return EmptyStatePanel(
            title = gateTitle,
            description = Localization.message("gtk.preview.locked.message"),
            actionText = Localization.message("gtk.preview.locked.action"),
            onAction = { PremiumFeatureGate.requestAccess(gateTitle) },
        ).panel()
    }
}
