package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.preview.actions.GtkPreviewRefreshAction
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.license.PremiumFeatureGate

class GtkPreviewPanelFactory : ToolWindowFactory {
    val enablePreview: Boolean = PremiumFeatureGate.isPremiumAvailable()

    val contentFactory: ContentFactory = ContentFactory.getInstance()

    val previewActions =
        listOf(
            GtkPreviewRefreshAction(),
        )

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val previewPanel = if (enablePreview) GtkPreviewPanel().panel() else GtkPreviewPremiumGatePanel().panel()
        val contentPanel = contentFactory.createContent(previewPanel, "", false)

        toolWindow.contentManager.addContent(contentPanel)

        if (enablePreview) toolWindow.setTitleActions(previewActions)
    }
}
