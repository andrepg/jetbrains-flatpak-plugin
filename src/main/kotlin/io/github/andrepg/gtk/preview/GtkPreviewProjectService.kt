package io.github.andrepg.gtk.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.actions.GtkPreviewRefreshAction
import io.github.andrepg.gtk.preview.ui.GtkPreviewErrorPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.gtk.preview.ui.GtkPreviewPremiumGatePanel
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import io.github.andrepg.shared.log.Log
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class GtkPreviewProjectService(
    private val project: Project,
) : Disposable {
    private val log = Log.getInstance(GtkPreviewProjectService::class.java)

    val panel = GtkPreviewPanel()
    val renderScheduler = GtkPreviewRenderScheduler(panel)

    val refreshAction =
        GtkPreviewRefreshAction(renderScheduler) { p ->
            renderScheduler.scheduleRender(p)
        }

    private var toolWindow: ToolWindow? = null
    private var attached = false

    fun setToolWindow(window: ToolWindow) {
        toolWindow = window
    }

    fun attach() {
        if (attached) return
        if (project.isDisposed) return
        attached = true

        val connection = project.messageBus.connect(this)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (!PremiumFeatureGate.isPremiumAvailable()) return
                    val uiEvent =
                        events.firstOrNull { event ->
                            if (event !is VFileContentChangeEvent) return@firstOrNull false
                            val file = event.file ?: return@firstOrNull false
                            isGtkUiFile(file.name)
                        } ?: return
                    renderScheduler.scheduleRenderIfCurrent(project, uiEvent.file)
                }
            },
        )
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(
                    source: FileEditorManager,
                    file: VirtualFile,
                ) {
                    if (!PremiumFeatureGate.isPremiumAvailable()) return
                    if (!isGtkUiFile(file.name)) return
                    renderScheduler.scheduleRender(project)
                }
            },
        )
    }

    fun scheduleInitialRender() {
        if (!PremiumFeatureGate.isPremiumAvailable()) return
        renderScheduler.scheduleRender(project)
    }

    fun showPremiumGate() {
        val window = toolWindow ?: return
        val manager = window.contentManager
        if (manager.contentCount == 0) return
        val content = manager.getContent(0) ?: return
        content.component = GtkPreviewPremiumGatePanel().panel()
        content.displayName = null
        window.setTitleActions(emptyList())
    }

    fun showUiMissingState() {
        val window = toolWindow ?: return
        val manager = window.contentManager
        if (manager.contentCount == 0) return
        val content = manager.getContent(0) ?: return
        content.component =
            io.github.andrepg.shared.ui.EmptyStatePanel(
                title = Localization.message("gtk.preview.ui.missing.title"),
                description = Localization.message("gtk.preview.ui.missing.description"),
                actionText = "",
                onAction = {},
            ).panel()
        content.displayName = null
        window.setTitleActions(listOf(refreshAction))
    }

    fun showSdkMissingState() {
        val window = toolWindow ?: return
        val manager = window.contentManager
        if (manager.contentCount == 0) return
        val content = manager.getContent(0) ?: return
        content.component =
            io.github.andrepg.shared.ui.EmptyStatePanel(
                title = Localization.message("gtk.preview.sdk.missing.title"),
                description = Localization.message("gtk.preview.sdk.missing.description"),
                actionText = "",
                onAction = {},
            ).panel()
        content.displayName = null
        window.setTitleActions(listOf(refreshAction))
    }

    override fun dispose() = Unit
}
