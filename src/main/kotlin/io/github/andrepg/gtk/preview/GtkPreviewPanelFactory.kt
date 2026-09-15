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
    /**
     * Defines if we should enable preview based on licensing gate
     */
    private val shouldEnablePreview: Boolean = PremiumFeatureGate.isPremiumAvailable()

    /**
     * Holds the main preview to display at panel
     */
    private val gtkPreviewPanel = GtkPreviewPanel()

    /**
     * The action used to refresh the panel
     */
    private val gtkPreviewRenderAction = GtkPreviewRenderAction(gtkPreviewPanel)

    private val gtkPreviewRefreshAction =
        GtkPreviewRefreshAction { project ->
            gtkPreviewRenderAction.callBackgroundTask(project)
        }

    /**
     * Create main window content to display at panel
     */
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        if (!shouldEnablePreview) {
            createMainContentPanel(toolWindow, GtkPreviewPremiumGatePanel().panel())
            return
        }

        createMainContentPanel(toolWindow, gtkPreviewPanel.panel())

        toolWindow.setTitleActions(
            listOf(gtkPreviewRefreshAction),
        )

        registerFileListener(project, toolWindow)

        gtkPreviewRenderAction.callBackgroundTask(project)
    }

    /**
     * Create main content panel and adds it to main window
     * via main Window ContentManager current instance
     */
    private fun createMainContentPanel(
        window: ToolWindow,
        panel: JPanel,
    ) = window.contentManager.addContent(
        ContentFactory.getInstance().createContent(panel, null, false),
    )

    private fun registerFileListener(
        project: Project,
        toolWindow: ToolWindow,
    ) = project.messageBus.connect(toolWindow.disposable).subscribe(
        VirtualFileManager.VFS_CHANGES,
        object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val supportedUiOpened = events.any({ isGtkUiFile(it.file?.name.toString()) })

                if (supportedUiOpened) gtkPreviewRenderAction.callBackgroundTask(project)
            }
        },
    )
}
