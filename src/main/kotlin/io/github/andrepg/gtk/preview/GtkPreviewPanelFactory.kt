package io.github.andrepg.gtk.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.actions.GtkPreviewRefreshAction
import io.github.andrepg.gtk.preview.actions.GtkPreviewRenderAction
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.license.PremiumFeatureGate
import javax.swing.JPanel

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
            createContentWindow(toolWindow, GtkPreviewPremiumGatePanel().panel())
            return
        }

        val gtkPreviewPanel = GtkPreviewPanel()
        val gtkPreviewRenderAction = GtkPreviewRenderAction(gtkPreviewPanel)

        createContentWindow(toolWindow, gtkPreviewPanel.panel())

        val refreshAction =
            GtkPreviewRefreshAction { e ->
                e.project?.let { gtkPreviewRenderAction.triggerRender(it) }
            }

        toolWindow.setTitleActions(listOf(refreshAction))

        registerFileListener(project, toolWindow, gtkPreviewRenderAction)
        gtkPreviewRenderAction.triggerRender(project)
    }

    private fun createContentWindow(
        window: ToolWindow,
        panel: JPanel,
    ) {
        window.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel, null, false),
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

    private fun isPreviewableFile(name: String): Boolean = isGtkUiFile(name)
}
