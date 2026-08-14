package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.shared.FeatureFlags
import io.github.andrepg.shared.license.PremiumFeatureGate
import javax.swing.JComponent

/**
 * Creates the GTK preview tool window.
 *
 * IDE glue: creates the panel and adds it to the tool window. The live preview
 * is a premium feature — unlicensed users get an [UpgradePanel] instead.
 */
class GtkPreviewToolWindowFactory : ToolWindowFactory {
    private val contentFactory = ContentFactory.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!isGtkPreviewEnabled()) return

        val content: Content = when {
            PremiumFeatureGate.isPremiumAvailable() -> buildPreviewPanel(project, toolWindow)
            else -> buildUpgradePanel()
        }

        toolWindow.contentManager.addContent(content)
    }

    private fun createContent(panel: JComponent): Content = contentFactory.createContent(
        panel,
        "",
        false
    )

    private fun buildUpgradePanel(): Content = createContent(UpgradePanel())

    private fun buildPreviewPanel(project: Project, toolWindow: ToolWindow): Content {
        val gtkPreviewPanel = GtkPreviewPanel(project, toolWindow)
        val contentPanel = createContent(gtkPreviewPanel)

        contentPanel.setDisposer {
            gtkPreviewPanel.dispose()
        }

        return contentPanel
    }

    private fun isGtkPreviewEnabled(): Boolean = FeatureFlags.getBoolean(
        FeatureFlags.FEATURE_FLAG_ENABLE_GTK_PREVIEW
    )
}
