package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.preview.actions.GtkPreviewRefreshAction
import io.github.andrepg.gtk.preview.actions.GtkPreviewRenderAction
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.license.PremiumFeatureGate

/**
 * Factory for the GTK Preview tool window.
 *
 * When premium features are available the tool window shows a live
 * preview of the active `.ui` file powered by [GtkPreviewRenderAction].
 * A [BulkFileListener] re-triggers the render whenever a `.ui` or
 * `.glade` file is saved.
 *
 * When premium is locked the window displays a
 * [GtkPreviewPremiumGatePanel] with a subscribe link.
 */
class GtkPreviewPanelFactory : ToolWindowFactory {
    private val enablePreview: Boolean = PremiumFeatureGate.isPremiumAvailable()

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        if (!enablePreview) {
            showPremiumGate(toolWindow)
            return
        }

        val panel = GtkPreviewPanel()
        val renderAction = GtkPreviewRenderAction(panel)
        val refreshAction =
            GtkPreviewRefreshAction { e ->
                e.project?.let { renderAction.triggerRender(it) }
            }

        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel.panel(), "", false),
        )
        toolWindow.setTitleActions(listOf(refreshAction))

        registerFileListener(project, toolWindow, renderAction)
        renderAction.triggerRender(project)
    }

    private fun showPremiumGate(toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(
                GtkPreviewPremiumGatePanel().panel(),
                "",
                false,
            ),
        )
    }

    private fun registerFileListener(
        project: Project,
        toolWindow: ToolWindow,
        renderAction: GtkPreviewRenderAction,
    ) {
        project.messageBus.connect(toolWindow.disposable).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val hasUiChange =
                        events.any { event ->
                            event.file?.name?.let(::isPreviewableFile) == true
                        }
                    if (hasUiChange) {
                        renderAction.triggerRender(project)
                    }
                }
            },
        )
    }

    private fun isPreviewableFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "ui" || ext == "glade"
    }
}
