package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.preview.actions.GtkPreviewRefreshAction
import io.github.andrepg.gtk.preview.actions.GtkPreviewRenderAction
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.license.PremiumFeatureGate

class GtkPreviewPanelFactory : ToolWindowFactory {
    val enablePreview: Boolean = PremiumFeatureGate.isPremiumAvailable()

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val contentFactory = ContentFactory.getInstance()

        if (enablePreview) {
            val panel = GtkPreviewPanel()
            val renderAction = GtkPreviewRenderAction(panel)
            val refreshAction = GtkPreviewRefreshAction(panel)

            toolWindow.contentManager.addContent(
                contentFactory.createContent(panel.panel(), "", false),
            )
            toolWindow.setTitleActions(listOf(renderAction, refreshAction))
        } else {
            toolWindow.contentManager.addContent(
                contentFactory.createContent(GtkPreviewPremiumGatePanel().panel(), "", false),
            )
        }
    }
}
