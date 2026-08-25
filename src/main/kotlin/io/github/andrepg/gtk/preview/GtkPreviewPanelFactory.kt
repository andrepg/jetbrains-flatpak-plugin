package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.license.PremiumFeatureGate

class GtkPreviewPanelFactory : ToolWindowFactory {
    val enablePreview: Boolean = PremiumFeatureGate.isPremiumAvailable()

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val previewPanel = if (enablePreview) GtkPreviewPanel().getPanel() else GtkPreviewPremiumGatePanel().getPanel()

        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(previewPanel, "", false),
        )
    }
}
