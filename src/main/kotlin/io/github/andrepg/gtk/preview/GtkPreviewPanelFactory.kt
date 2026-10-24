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
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import javax.swing.JPanel

/**
 * Factory for the GTK Preview tool window.
 *
 * When premium features are available the tool window shows a live preview of
 * the active `.ui` file, rendered through [GtkPreviewRenderScheduler]. A
 * [BulkFileListener] re-triggers the render whenever the open `.ui`/`.glade`
 * file is saved (other `.ui` files are ignored so they cannot clobber the
 * preview).
 *
 * When premium is locked the window displays a [GtkPreviewPremiumGatePanel]
 * with a subscribe link. The gate is evaluated fresh on every
 * [createToolWindowContent] and re-checked before every render, so a mid-session
 * subscription change is honoured without an IDE restart.
 */
class GtkPreviewPanelFactory : ToolWindowFactory {
    /**
     * Holds the main preview to display at panel
     */
    private val gtkPreviewPanel = GtkPreviewPanel()

    /**
     * Serializes all render triggers (open, refresh, VFS saves).
     */
    private val renderScheduler = GtkPreviewRenderScheduler(gtkPreviewPanel)

    /**
     * The action used to refresh the panel
     */
    private val gtkPreviewRefreshAction =
        GtkPreviewRefreshAction(renderScheduler) { project ->
            renderScheduler.scheduleRender(project)
        }

    /**
     * Create main window content to display at panel
     */
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        // The tool window id stays "GtkPreviewPanel" (layout persistence,
        // stable identifier) while the stripe shows the user-facing title.
        toolWindow.stripeTitle = Localization.message("gtk.preview.panel.title")

        // H4: the premium gate is re-evaluated here, not stored as a field, so
        // a subscription purchase/expiry mid-session flips the panel.
        if (!PremiumFeatureGate.isPremiumAvailable()) {
            createMainContentPanel(toolWindow, GtkPreviewPremiumGatePanel().panel())
            return
        }

        createMainContentPanel(toolWindow, gtkPreviewPanel.panel())

        toolWindow.setTitleActions(
            listOf(gtkPreviewRefreshAction),
        )

        registerFileListener(project, toolWindow)

        renderScheduler.scheduleRender(project)
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
                val uiEvent = events.firstOrNull { isGtkUiFile(it.file?.name.toString()) } ?: return
                renderScheduler.scheduleRenderIfCurrent(project, uiEvent.file)
            }
        },
    )
}
