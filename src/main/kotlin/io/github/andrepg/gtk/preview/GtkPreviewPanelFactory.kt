package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import javax.swing.JPanel

/**
 * Factory for the GTK Preview tool window.
 *
 * The premium gate is re-evaluated on every call and the content is swapped in
 * place so a subscription change is honoured without an IDE restart.
 */
class GtkPreviewPanelFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        // The tool window id stays "GtkPreviewPanel" (layout persistence)
        toolWindow.stripeTitle = Localization.message("gtk.preview.panel.title")

        val service = project.getService(GtkPreviewProjectService::class.java)
        val contentManager = toolWindow.contentManager

        if (!PremiumFeatureGate.isPremiumAvailable()) {
            val content =
                ContentFactory.getInstance().createContent(
                    GtkPreviewPremiumGatePanel().panel(),
                    null,
                    false,
                )
            content.isCloseable = false
            contentManager.addContent(content)
            service.setToolWindow(toolWindow)
            service.showPremiumGate()
            return
        }

        val content =
            ContentFactory.getInstance().createContent(
                service.panel.panel(),
                null,
                false,
            )
        content.isCloseable = false
        contentManager.addContent(content)

        toolWindow.setTitleActions(
            listOf(service.refreshAction),
        )

        service.setToolWindow(toolWindow)
        service.attach()
        service.scheduleInitialRender()
    }
}
