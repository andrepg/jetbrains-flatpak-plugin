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
import java.util.concurrent.atomic.AtomicReference

/**
 * Serializes GTK preview renders.
 *
 * Every trigger point — tool-window open, the Render/Refresh title actions and
 * the VFS change listener — goes through [scheduleRender], so at most one
 * render is in flight at a time. While a render is running, further calls are
 * coalesced into a single pending render of the latest [Project], which starts
 * as soon as the current one finishes: two `mutter`/`make` processes can never
 * overlap (no `wayland-N` TOCTOU, no concurrent `make` on the same binary) and
 * a stale PNG can never be applied over a newer render.
 *
 * All Swing mutation (the panel state flip and the size read) happens on the
 * EDT via [startRender]; the render itself runs through
 * [GtkPreviewBackgroundRenderTask], whose callbacks return to the EDT. This is
 * what makes the H1 action path (invoked on a background thread) safe.
 *
 * Thread-safe: [scheduleRender] and [scheduleRenderIfCurrent] may be called
 * from any thread and return immediately.
 */
class GtkPreviewRenderScheduler(
    private val panel: GtkPreviewPanel,
) {
    private val log = Log.getInstance(GtkPreviewRenderScheduler::class.java)

    private val inFlight = AtomicBoolean(false)
    private val pendingProject = AtomicReference<Project>(null)
    private val lock = Any()

    /**
     * Schedules a render of the currently open `.ui` file, coalescing with any
     * in-flight render. Returns immediately.
     */
    fun scheduleRender(project: Project) {
        if (project.isDisposed) return

        // H4: re-check the premium gate on every render, so a mid-session
        // subscription change is honoured without an IDE restart.
        if (!PremiumFeatureGate.isPremiumAvailable()) {
            log.debug("Skipping preview render; premium feature is locked")
            return
        }

        val startNow =
            synchronized(lock) {
                if (inFlight.get()) {
                    pendingProject.set(project)
                    false
                } else {
                    inFlight.set(true)
                    true
                }
            }
        if (startNow) startRender(project)
    }

    /**
     * Schedules a render only when [changedFile] is the `.ui` file currently
     * open in the active editor, so saving a different `.ui` file (e.g. a
     * sibling defining a custom class) does not clobber the preview.
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
     * hands the render to the platform progress manager — which, when started
     * from the EDT, runs the task on a background thread and dispatches its
     * callbacks back to the EDT.
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
     * in-flight guard and starts the coalesced pending render, if any.
     */
    private fun renderFinished() {
        val next =
            synchronized(lock) {
                inFlight.set(false)
                val pending = pendingProject.getAndSet(null)
                if (pending != null && !pending.isDisposed) {
                    inFlight.set(true)
                    pending
                } else {
                    null
                }
            }
        if (next != null) startRender(next)
    }
}
