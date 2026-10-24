package io.github.andrepg.gtk.preview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.tasks.GtkPreviewBackgroundRenderTask
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.license.PremiumFeatureGate
import io.github.andrepg.shared.log.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serializes GTK preview renders in "lock and block" mode.
 *
 * At most one render runs at a time. While a render is in flight, further
 * triggers are ignored (not queued/coalesced).
 */
class GtkPreviewRenderScheduler(
    private val panel: GtkPreviewPanel,
) {
    private val log = Log.getInstance(GtkPreviewRenderScheduler::class.java)

    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    /**
     * Schedules a render of the currently open `.ui` file. If a render is
     * already running, the request is ignored.
     */
    fun scheduleRender(project: Project) {
        if (project.isDisposed) return

        // By checking premium status here we avoid to restart the IDE after a purchase to re-render
        if (!PremiumFeatureGate.isPremiumAvailable()) {
            log.debug("Skipping preview render; premium feature is locked")
            return
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping preview render; already running")
            return
        }

        startRender(project)
    }

    /**
     * Schedules a render only when [changedFile] is the `.ui` file currently
     * open in the active editor. If a render is already running, the request
     * is ignored.
     */
    fun scheduleRenderIfCurrent(
        project: Project,
        changedFile: VirtualFile?,
    ) {
        if (changedFile == null || !isGtkUiFile(changedFile.name)) return
        val active = FileEditorManager.getInstance(project).selectedEditor?.file ?: return
        if (active.path != changedFile.path) return
        scheduleRender(project)
    }

    private fun startRender(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                renderFinished()
            } else {
                runOnEdt(project)
            }
        }
    }

    /**
     * EDT: flips the panel to the loading state, captures its current size and
     * hands the render to the platform progress manager.
     */
    private fun runOnEdt(project: Project) {
        panel.setLoading(true)
        panel.setFailed(false)
        panel.refresh()

        val (width, height) = RenderDimensions.renderSize(panel)

        val task =
            GtkPreviewBackgroundRenderTask(project, panel, width, height) {
                renderFinished()
            }

        ProgressManager.getInstance().run(task)
    }

    /**
     * Called from the render task's `onFinished` (on the EDT): releases the
     * running guard.
     */
    private fun renderFinished() {
        running.set(false)
    }
}
